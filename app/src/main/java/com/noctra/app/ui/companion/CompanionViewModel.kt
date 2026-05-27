package com.noctra.app.ui.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.RewardLedgerRepository
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
    private val evolutionUseCase: CompanionEvolutionUseCase = CompanionEvolutionUseCase(),
    private val seedingUseCase: DataSeedingUseCase = DataSeedingUseCase()
) : ViewModel() {

    data class CompanionUiState(
        val isLoading: Boolean = false,
        val displayName: String = "User",
        val evolutionState: CompanionEvolutionUseCase.EvolutionState? = null,
        val tokenBalance: Int = 0,
        val lastSleepScore: Int? = null,
        val devolutionPending: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val _showMorningPopup = MutableSharedFlow<Pair<Int, Int>>()
    val showMorningPopup: SharedFlow<Pair<Int, Int>> = _showMorningPopup.asSharedFlow()

    private val _showEvolutionPopup = MutableSharedFlow<CompanionEvolutionUseCase.EvolutionState>()
    val showEvolutionPopup: SharedFlow<CompanionEvolutionUseCase.EvolutionState> = _showEvolutionPopup.asSharedFlow()

    private var previousStageLevel: Int? = null

    fun loadData(userId: String, lastShownSleepDate: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Ensure profile exists
                userProfileRepository.getOrCreateProfile(userId)
                refreshData(userId, lastShownSleepDate)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun refreshData(userId: String, lastShownSleepDate: String?, triggerMorningPopup: Boolean = true) {
        try {
            val profile = userProfileRepository.getOrCreateProfile(userId)
            val latestSleep = sleepRecordRepository.getLatestSleepRecord(userId)
            val ledger = rewardRepository.getRewardLedger(userId)
            
            if (ledger != null) {
                val evolution = evolutionUseCase.execute(ledger.totalXp)
                
                // Evolution milestone check - ALWAYS check this when data refreshes
                if (previousStageLevel != null && evolution.stageLevel > previousStageLevel!!) {
                    _showEvolutionPopup.emit(evolution)
                }
                previousStageLevel = evolution.stageLevel

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        displayName = if (profile.displayName.isEmpty()) "User" else profile.displayName,
                        evolutionState = evolution,
                        tokenBalance = ledger.tokenBalance,
                        lastSleepScore = latestSleep?.compositeScore,
                        devolutionPending = ledger.devolutionPending,
                        error = null
                    )
                }

                // If devolution was detected, clear the flag in DB so it only shows once
                if (ledger.devolutionPending) {
                    rewardRepository.updateRewardLedger(ledger.copy(devolutionPending = false))
                }

                // Check for morning popup specifically
                if (triggerMorningPopup) {
                    val today = java.time.LocalDate.now().toString()
                    if (latestSleep != null && latestSleep.sessionDate == today && lastShownSleepDate != today) {
                        _showMorningPopup.emit(Pair(latestSleep.compositeScore ?: 0, 7))
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Reward ledger not found") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = "Refresh failed: ${e.message}") }
        }
    }

    fun seedDemoData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                seedingUseCase.seedMockData(userId)
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
                    
                    // Optimistic update
                    val newEvolution = evolutionUseCase.execute(newXp)
                    
                    // Check for evolution immediately in the optimistic step
                    if (previousStageLevel != null && newEvolution.stageLevel > previousStageLevel!!) {
                        _showEvolutionPopup.emit(newEvolution)
                    }
                    // Note: previousStageLevel will be updated again in refreshData
                    
                    _uiState.update { 
                        it.copy(evolutionState = newEvolution)
                    }

                    val updatedLedger = ledger.copy(
                        totalXp = newXp,
                        lastUpdated = OffsetDateTime.now().toString()
                    )
                    rewardRepository.updateRewardLedger(updatedLedger)
                    
                    // Final background refresh, but don't re-trigger morning popup
                    refreshData(userId, null, triggerMorningPopup = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Update XP failed: ${e.message}") }
                refreshData(userId, null, triggerMorningPopup = false)
            }
        }
    }
}
