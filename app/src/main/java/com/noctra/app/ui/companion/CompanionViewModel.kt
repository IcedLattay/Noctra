package com.noctra.app.ui.companion

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.repository.InventoryRepository
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.ShopRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.SleepSyncManager
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.CompanionEvolutionUseCase
import com.noctra.app.domain.usecase.DataSeedingUseCase
import com.noctra.app.domain.usecase.ReconciliationAuditUseCase
import com.noctra.app.data.model.RewardLedger
import com.noctra.app.utils.NetworkObserver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class CompanionViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CompanionVM"
    }

    private val networkObserver = NetworkObserver(application)
    private val userProfileRepository = UserProfileRepository()
    private val rewardRepository = RewardLedgerRepository()
    private val sleepRecordRepository = SleepRecordRepository()
    private val shopRepository = ShopRepository()
    private val inventoryRepository = InventoryRepository()
    private val evolutionUseCase = CompanionEvolutionUseCase()
    private val seedingUseCase = DataSeedingUseCase()
    private val auditUseCase = ReconciliationAuditUseCase()
    private val sleepSyncManager = SleepSyncManager()

    data class ShopItemUiModel(
        val item: ShopItem,
        val isOwned: Boolean,
        val isEquipped: Boolean,
        val canAfford: Boolean
    )

    data class CompanionUiState(
        val isLoading: Boolean = false,
        val isOffline: Boolean = false,
        val isAnimationLoaded: Boolean = false,
        val displayName: String = "User",
        val evolutionState: CompanionEvolutionUseCase.EvolutionState? = null,
        val tokenBalance: Int = 0,
        val lastSleepScore: Int? = null,
        val equippedOutfit: ShopItem? = null,
        val shopItems: List<ShopItemUiModel> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    // Set by loadData — lets the reconnect auto-reload know who to load for.
    private var lastUserId: String? = null

    init {
        // Live connectivity: the push flip, not a per-load poll. Disconnect
        // → placeholder immediately (no request needed to discover it);
        // reconnect while showing the placeholder → auto-retry.
        viewModelScope.launch {
            var lastOnline = com.noctra.app.utils.ConnectivityMonitor.isOnline.value
            com.noctra.app.utils.ConnectivityMonitor.isOnline.collect { online ->
                if (online == lastOnline) return@collect
                lastOnline = online
                if (!online) {
                    _uiState.update { it.copy(isLoading = false, isOffline = true) }
                } else if (_uiState.value.isOffline) {
                    lastUserId?.let { retry(it) }
                }
            }
        }
    }

    private val _showMorningPopup = MutableSharedFlow<Pair<Int, Int>>()
    val showMorningPopup: SharedFlow<Pair<Int, Int>> = _showMorningPopup.asSharedFlow()

    private val _showEvolutionPopup = MutableSharedFlow<EvolutionEvent>()
    val showEvolutionPopup: SharedFlow<EvolutionEvent> = _showEvolutionPopup.asSharedFlow()

    private val _showNoticePopup = MutableSharedFlow<CompanionNotice>()
    val showNoticePopup: SharedFlow<CompanionNotice> = _showNoticePopup.asSharedFlow()

    enum class CompanionNotice { RESTORED, LOST, WARNING }

    data class EvolutionEvent(
        val newStage: CompanionEvolutionUseCase.EvolutionState,
        val oldLevel: Int
    )

    private var previousStageLevel: Int? = null

    // session flag to prevent dialog loop
    private var noticeHandledThisSession = false

    // Once-per-process guard: onViewCreated + onResume both trigger
    // loadData on cold open, and the prefs flag is only written by the
    // fragment collector AFTER emission — so both calls pass the gate
    // and the morning popup fires twice. This kills the duplicate.
    private var morningPopupEmittedForDate: String? = null

    fun retry(userId: String) {
        _uiState.update { it.copy(isOffline = false, isLoading = true) }
        loadData(userId, null)
    }

    // Source of truth for the morning-recap once-per-day guard. Read fresh
    // at decision time so no caller (retry, reconnect auto-reload) can
    // bypass it by passing a stale or null parameter.
    private fun readShownSleepDate(): String? =
        getApplication<Application>()
            .getSharedPreferences("noctra_prefs", android.content.Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)

    // Clears per-user UI state so the next login never flashes the
    // previous account's data while fresh data loads
    fun onLogout() {
        _uiState.value = CompanionUiState()
        previousStageLevel = null
        noticeHandledThisSession = false
        morningPopupEmittedForDate = null
    }

    fun loadData(userId: String, lastShownSleepDate: String?) {
        lastUserId = userId
        viewModelScope.launch {
            if (!networkObserver.checkNow()) {
                _uiState.update { it.copy(isLoading = false, isOffline = true) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, isOffline = false) }
            try {
                // Re-read the recap flag HERE rather than trusting the caller's
                // parameter: retry()/reconnect paths pass null, which used to
                // bypass the once-per-session-date guard after a process kill
                // (popup re-showed). The pref is the source of truth.
                val shownDate = readShownSleepDate()

                // Journey-start gate: the account's first synced night is the
                // night ON or AFTER onboarding — never a pre-signup night.
                // Fresh accounts used to pull "last night" on day 0 (it
                // predates the account), freezing XP for nights the journey
                // never covered. Null stamp = legacy account predating the
                // column → no gate.
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val journeyStart = profile.onboardingCompletedAt
                    ?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }

                // 0. Inline provisional sync of last night (first-open-of-the-day
                //    trigger). Gated on the recap flag so later resumes don't
                //    re-hit Health Connect. Silent: result ignored — the 9 AM
                //    worker is the backup, and the auditor covers the audit range.
                val sessionDate = java.time.LocalDate.now().minusDays(1)
                Log.d(TAG, "loadData — shownDate=$shownDate, sessionDate=$sessionDate, journeyStart=$journeyStart (caller said $lastShownSleepDate)")
                val predatesJourney = journeyStart != null && sessionDate.isBefore(journeyStart)
                if (shownDate != sessionDate.toString() && !predatesJourney) {
                    Log.d(TAG, "loadData — triggering inline sync for $sessionDate")
                    val syncResult = sleepSyncManager.syncSessionDate(userId, sessionDate)
                    Log.d(TAG, "loadData — syncResult: $syncResult")
                } else {
                    Log.d(TAG, "loadData — sync skipped (${if (predatesJourney) "night predates journey" else "already synced today"})")
                }

                // 1. Snapshot Ledger BEFORE Audit
                val oldLedger = rewardRepository.getRewardLedger(userId)
                
                // 2. Run the Reconciliation Audit
                auditUseCase.execute(userId)
                
                // 3. Snapshot Ledger AFTER Audit
                val newLedger = rewardRepository.getRewardLedger(userId)

                // 4. Determine and Trigger Notice
                if (oldLedger != null && newLedger != null && !noticeHandledThisSession) {
                    val notice = determineNotice(oldLedger, newLedger)
                    if (notice != null) {
                        _showNoticePopup.emit(notice)
                        noticeHandledThisSession = true
                    }
                }

                refreshData(userId, shownDate, triggerMorningPopup = true)
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
                val isOffline = !networkObserver.checkNow()
                _uiState.update { it.copy(isLoading = false, isOffline = isOffline, error = if (!isOffline) com.noctra.app.utils.AuthErrorMapper.userMessage(e, "Couldn't load your data.") else null) }
            }
        }
    }

    private fun determineNotice(old: RewardLedger, new: RewardLedger): CompanionNotice? {
        return when {
            // Priority 1: RESTORED
            new.currentStreak > old.currentStreak && new.currentStreak > 0 -> CompanionNotice.RESTORED
            // Priority 2: LOST
            new.currentStreak == 0 && old.currentStreak > 0 -> CompanionNotice.LOST
            // Priority 3: WARNING
            !old.hasFirstMiss && new.hasFirstMiss -> CompanionNotice.WARNING
            else -> null
        }
    }

    private suspend fun refreshData(
        userId: String, 
        lastShownSleepDate: String?, 
        triggerMorningPopup: Boolean = true
    ) {
        try {
            val profile = userProfileRepository.getOrCreateProfile(userId)
            val latestSleep = sleepRecordRepository.getLatestSleepRecord(userId)
            val ledger = rewardRepository.getRewardLedger(userId)
            
            // 1. Get equipment & Shop items
            val inventory = inventoryRepository.getUserInventory(userId)
            val equippedItemId = profile.outfitEquipped
            val allShopItems = shopRepository.getAllShopItems()
            val balance = ledger?.tokenBalance ?: 0

            val inventoryItemIds = inventory.map { it.itemId }.toSet()
            
            val uiModels = allShopItems.map { item ->
                ShopItemUiModel(
                    item = item,
                    isOwned = inventoryItemIds.contains(item.itemId),
                    isEquipped = item.itemId == equippedItemId,
                    canAfford = balance >= item.tokenCost
                )
            }

            val equippedOutfit = allShopItems.find { it.itemId == equippedItemId }

            Log.d("CompanionVM", "Equipped outfit: ${equippedOutfit?.label}")

            if (ledger != null) {
                val evolution = evolutionUseCase.execute(ledger.totalXp)
                
                // 2. Milestone check
                if (previousStageLevel != null && evolution.stageLevel > previousStageLevel!!) {
                    _showEvolutionPopup.emit(EvolutionEvent(evolution, previousStageLevel!!))
                }
                previousStageLevel = evolution.stageLevel

                // 3. Update State
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        displayName = if (profile.displayName.isEmpty()) "User" else profile.displayName,
                        evolutionState = evolution,
                        tokenBalance = ledger.tokenBalance,
                        lastSleepScore = latestSleep?.compositeScore,
                        equippedOutfit = equippedOutfit,
                        shopItems = uiModels,
                        error = null
                    )
                }

                // 5. Morning Popup check — the recap is about LAST NIGHT's
                // session (dated yesterday). Show once per session date:
                // records get written twice (provisional + finalization), so
                // the flag — not record existence — is the gate.
                if (triggerMorningPopup) {
                    val recapSessionDate = java.time.LocalDate.now().minusDays(1).toString()
                    // Fresh pref read, not the parameter: callers like retry()
                    // pass null, which used to bypass this guard after a
                    // process kill and re-show the recap. Pref is truth.
                    val shownDate = readShownSleepDate()
                    if (latestSleep != null &&
                        latestSleep.sessionDate == recapSessionDate &&
                        shownDate != recapSessionDate &&
                        morningPopupEmittedForDate != recapSessionDate
                    ) {
                        morningPopupEmittedForDate = recapSessionDate
                        // TEMP (XP economy): the emitted bonus IS the stored
                        // frozen amount — display and award can't disagree.
                        _showMorningPopup.emit(Pair(latestSleep.compositeScore ?: 0, latestSleep.xpAccrued))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            val isOffline = !networkObserver.checkNow()
            _uiState.update { it.copy(isLoading = false, isOffline = isOffline, error = if (!isOffline) e.message else null) }
        }
    }

    fun purchaseAndEquip(userId: String, item: ShopItem) {
        viewModelScope.launch {
            try {
                val ledger = rewardRepository.getRewardLedger(userId)
                if (ledger != null && ledger.tokenBalance >= item.tokenCost) {
                    val newBalance = ledger.tokenBalance - item.tokenCost
                    
                    // Optimistic UI update
                    _uiState.update { currentState ->
                        val updatedItems = currentState.shopItems.map { model ->
                            if (model.item.itemId == item.itemId) {
                                model.copy(isOwned = true, isEquipped = true)
                            } else {
                                model.copy(isEquipped = false)
                            }
                        }
                        currentState.copy(
                            tokenBalance = newBalance,
                            shopItems = updatedItems,
                            equippedOutfit = item
                        )
                    }

                    // Background DB updates
                    val updatedLedger = ledger.copy(
                        tokenBalance = newBalance,
                        lastUpdated = OffsetDateTime.now().toString()
                    )
                    rewardRepository.updateRewardLedger(updatedLedger)
                    inventoryRepository.purchaseItem(userId, item.itemId)
                    inventoryRepository.setEquippedOutfit(userId, item.itemId)
                    
                    // Final background refresh
                    refreshData(userId, null, triggerMorningPopup = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = com.noctra.app.utils.AuthErrorMapper.userMessage(e, "Purchase failed — please try again.")) }
                refreshData(userId, null, triggerMorningPopup = false)
            }
        }
    }

    fun equipItem(userId: String, item: ShopItem) {
        val isCurrentlyEquipped = _uiState.value.shopItems.find { it.item.itemId == item.itemId }?.isEquipped ?: false
        
        viewModelScope.launch {
            try {
                // Optimistic UI update
                _uiState.update { currentState ->
                    val updatedItems = currentState.shopItems.map { model ->
                        if (model.item.itemId == item.itemId) {
                            model.copy(isEquipped = true)
                        } else {
                            model.copy(isEquipped = false)
                        }
                    }

                    currentState.copy(
                        shopItems = updatedItems,
                        equippedOutfit = item
                    )
                }
                
                inventoryRepository.setEquippedOutfit(userId, item.itemId)
                refreshData(userId, null, triggerMorningPopup = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = com.noctra.app.utils.AuthErrorMapper.userMessage(e, "Equip failed — please try again.")) }
                refreshData(userId, null, triggerMorningPopup = false)
            }
        }
    }

    fun seedDemoData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                seedingUseCase.seedMockData(userId)
                noticeHandledThisSession = false // allow for re-testing
                refreshData(userId, null, triggerMorningPopup = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = com.noctra.app.utils.AuthErrorMapper.userMessage(e, "Seeding failed — please try again.")) }
            }
        }
    }

    fun addXp(userId: String, amount: Int) {
        viewModelScope.launch {
            applyXp(userId, amount)
        }
    }

    /**
     * TEMP (XP economy): sums every unsettled row's frozen amount, applies
     * once, marks settled. Returns total applied and nights settled.
     */
    data class Settlement(val total: Int, val nights: Int)

    suspend fun settleMorningXp(userId: String): Settlement {
        val rows = sleepRecordRepository.getUnsettledRecords(userId)
        if (rows.isEmpty()) return Settlement(0, 0)
        val total = rows.sumOf { it.xpAccrued }
        if (total != 0) {
            applyXp(userId, total)
        }
        rows.forEach { row ->
            try {
                sleepRecordRepository.markXpSettled(userId, row.sessionDate)
            } catch (e: Exception) {
                Log.e("CompanionVM", "Mark settled failed for ${row.sessionDate}", e)
            }
        }
        return Settlement(total, rows.size)
    }

    private suspend fun applyXp(userId: String, amount: Int) {
        try {
            val ledger = rewardRepository.getRewardLedger(userId)
            if (ledger != null) {
                val newXp = (ledger.totalXp + amount).coerceAtLeast(0)

                val updatedLedger = ledger.copy(
                    totalXp = newXp,
                    hasFirstMiss = false, // xp tweak silences warning
                    lastUpdated = OffsetDateTime.now().toString()
                )
                rewardRepository.updateRewardLedger(updatedLedger)

                // Force refresh without triggering the morning/tired popups
                refreshData(userId, null, triggerMorningPopup = false)
            }
        } catch (e: Exception) {
            Log.e("CompanionVM", "XP tweak failed", e)
        }
    }
}
