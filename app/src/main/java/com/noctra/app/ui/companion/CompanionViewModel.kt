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

    private val _showMorningPopup = MutableSharedFlow<Pair<Int, Int>>()
    val showMorningPopup: SharedFlow<Pair<Int, Int>> = _showMorningPopup.asSharedFlow()

    private val _showEvolutionPopup = MutableSharedFlow<CompanionEvolutionUseCase.EvolutionState>()
    val showEvolutionPopup: SharedFlow<CompanionEvolutionUseCase.EvolutionState> = _showEvolutionPopup.asSharedFlow()

    private val _showNoticePopup = MutableSharedFlow<CompanionNotice>()
    val showNoticePopup: SharedFlow<CompanionNotice> = _showNoticePopup.asSharedFlow()

    enum class CompanionNotice { RESTORED, LOST, WARNING }

    private var previousStageLevel: Int? = null
    
    // session flag to prevent dialog loop
    private var noticeHandledThisSession = false

    fun retry(userId: String) {
        _uiState.update { it.copy(isOffline = false, isLoading = true) }
        loadData(userId, null)
    }

    // Clears per-user UI state so the next login never flashes the
    // previous account's data while fresh data loads
    fun onLogout() {
        _uiState.value = CompanionUiState()
        previousStageLevel = null
        noticeHandledThisSession = false
    }

    fun loadData(userId: String, lastShownSleepDate: String?) {
        viewModelScope.launch {
            if (!networkObserver.checkNow()) {
                _uiState.update { it.copy(isLoading = false, isOffline = true) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, isOffline = false) }
            try {
                // 0. Inline provisional sync of last night (first-open-of-the-day
                //    trigger). Gated on the recap flag so later resumes don't
                //    re-hit Health Connect. Silent: result ignored — the 9 AM
                //    worker is the backup, and the auditor covers the audit range.
                val sessionDate = java.time.LocalDate.now().minusDays(1).toString()
                Log.d(TAG, "loadData — lastShownSleepDate: $lastShownSleepDate, sessionDate: $sessionDate")
                if (lastShownSleepDate != sessionDate) {
                    Log.d(TAG, "loadData — triggering inline sync for $sessionDate")
                    val syncResult = sleepSyncManager.syncSessionDate(userId, java.time.LocalDate.now().minusDays(1))
                    Log.d(TAG, "loadData — syncResult: $syncResult")
                } else {
                    Log.d(TAG, "loadData — sync skipped (already synced today)")
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

                userProfileRepository.getOrCreateProfile(userId)
                refreshData(userId, lastShownSleepDate, triggerMorningPopup = true)
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
                val isOffline = !networkObserver.checkNow()
                _uiState.update { it.copy(isLoading = false, isOffline = isOffline, error = if (!isOffline) e.message else null) }
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
                    _showEvolutionPopup.emit(evolution)
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
                    if (latestSleep != null &&
                        latestSleep.sessionDate == recapSessionDate &&
                        lastShownSleepDate != recapSessionDate
                    ) {
                        _showMorningPopup.emit(Pair(latestSleep.compositeScore ?: 0, 7))
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
                _uiState.update { it.copy(error = "Purchase failed: ${e.message}") }
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
                _uiState.update { it.copy(error = "Equip failed: ${e.message}") }
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
                _uiState.update { it.copy(isLoading = false, error = "Seeding failed: ${e.message}") }
            }
        }
    }

    fun addXp(userId: String, amount: Int) {
        viewModelScope.launch {
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
}
