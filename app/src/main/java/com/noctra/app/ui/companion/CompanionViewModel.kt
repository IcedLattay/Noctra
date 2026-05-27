package com.noctra.app.ui.companion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.repository.InventoryRepository
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.ShopRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.CompanionEvolutionUseCase
import com.noctra.app.domain.usecase.DataSeedingUseCase
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
    private val userProfileRepository: UserProfileRepository = UserProfileRepository(),
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository(),
    private val sleepRecordRepository: SleepRecordRepository = SleepRecordRepository(),
    private val shopRepository: ShopRepository = ShopRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val evolutionUseCase: CompanionEvolutionUseCase = CompanionEvolutionUseCase(),
    private val seedingUseCase: DataSeedingUseCase = DataSeedingUseCase()
) : ViewModel() {

    data class CompanionUiState(
        val isLoading: Boolean = false,
        val displayName: String = "User",
        val evolutionState: CompanionEvolutionUseCase.EvolutionState? = null,
        val tokenBalance: Int = 0,
        val lastSleepScore: Int? = null,
        val equippedItems: Map<String, ShopItem> = emptyMap(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val _showMorningPopup = MutableSharedFlow<Pair<Int, Int>>()
    val showMorningPopup: SharedFlow<Pair<Int, Int>> = _showMorningPopup.asSharedFlow()

    private val _showEvolutionPopup = MutableSharedFlow<CompanionEvolutionUseCase.EvolutionState>()
    val showEvolutionPopup: SharedFlow<CompanionEvolutionUseCase.EvolutionState> = _showEvolutionPopup.asSharedFlow()

    private val _showDevolutionPopup = MutableSharedFlow<Unit>()
    val showDevolutionPopup: SharedFlow<Unit> = _showDevolutionPopup.asSharedFlow()

    private var previousStageLevel: Int? = null
    
    // session flag to prevent dialog loop
    private var devolutionHandledThisSession = false

    fun loadData(userId: String, lastShownSleepDate: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                userProfileRepository.getOrCreateProfile(userId)
                refreshData(userId, lastShownSleepDate, triggerMorningPopup = true, checkDevolution = true)
            } catch (e: Exception) {
                Log.e("CompanionVM", "Load failed", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun refreshData(
        userId: String, 
        lastShownSleepDate: String?, 
        triggerMorningPopup: Boolean = true,
        checkDevolution: Boolean = false
    ) {
        try {
            val profile = userProfileRepository.getOrCreateProfile(userId)
            val latestSleep = sleepRecordRepository.getLatestSleepRecord(userId)
            val ledger = rewardRepository.getRewardLedger(userId)
            
            // 1. Get equipment
            val inventory = inventoryRepository.getUserInventory(userId)
            val equippedItemIds = inventory.filter { it.isEquipped }.map { it.itemId }.toSet()
            val allShopItems = shopRepository.getAllShopItems()
            val equippedMap = allShopItems.filter { equippedItemIds.contains(it.itemId) }
                .associateBy { it.category }
            
            Log.d("CompanionVM", "Equipped Items: ${equippedMap.keys}")

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
                        equippedItems = equippedMap,
                        error = null
                    )
                }

                // 4. One-time devolution check
                if (checkDevolution && !devolutionHandledThisSession && ledger.devolutionPending) {
                    devolutionHandledThisSession = true
                    _showDevolutionPopup.emit(Unit)
                    rewardRepository.updateRewardLedger(ledger.copy(devolutionPending = false))
                }

                // 5. Morning Popup check
                if (triggerMorningPopup) {
                    val today = java.time.LocalDate.now().toString()
                    if (latestSleep != null && latestSleep.sessionDate == today && lastShownSleepDate != today) {
                        _showMorningPopup.emit(Pair(latestSleep.compositeScore ?: 0, 7))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CompanionVM", "Refresh failed", e)
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun seedDemoData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                seedingUseCase.seedMockData(userId)
                devolutionHandledThisSession = false // allow for re-testing
                refreshData(userId, null, triggerMorningPopup = true, checkDevolution = true)
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
                        devolutionPending = false, // xp tweak silences devolution
                        lastUpdated = OffsetDateTime.now().toString()
                    )
                    rewardRepository.updateRewardLedger(updatedLedger)
                    
                    // Force refresh without triggering the morning/tired popups
                    refreshData(userId, null, triggerMorningPopup = false, checkDevolution = false)
                }
            } catch (e: Exception) {
                Log.e("CompanionVM", "XP tweak failed", e)
            }
        }
    }
}
