package com.noctra.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.annotation.DrawableRes
import com.noctra.app.R

import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.domain.usecase.CompanionEvolutionUseCase

class UserProfileViewModel : ViewModel() {

    private val userProfileRepository = UserProfileRepository()
    private val rewardLedgerRepository = RewardLedgerRepository()
    private val routineSessionRepository = RoutineSessionRepository()
    private val evolutionUseCase = CompanionEvolutionUseCase()

    private val _profileData = MutableStateFlow(ProfileUiState())
    val profileData = _profileData.asStateFlow()

    fun loadProfile(context: Context) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val ledger = rewardLedgerRepository.getRewardLedger(userId)

                val currentStreak = ledger?.currentStreak ?: 0
                val longestStreak = ledger?.longestStreak ?: 0
                val totalXp = ledger?.totalXp ?: 0
                val routinesCompleted = routineSessionRepository.countCompletedSessions(userId)

                val evolution = evolutionUseCase.execute(totalXp)
                val stageAvatarRes = getAvatarResForStage(evolution.stageLevel)
                
                val nextStageXp = when (evolution.stageLevel) {
                    1 -> 1500
                    2 -> 5000
                    3 -> 15000
                    4 -> 50000
                    else -> 0
                }
                
                val xpMessage = if (evolution.stageLevel < 5) {
                    "You need ${nextStageXp - totalXp} XP for your Shleepy to evolve"
                } else {
                    "Your Shleepy has reached its peak form!"
                }

                _profileData.value = ProfileUiState(
                    displayName = profile.displayName,
                    email = profile.email,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    routinesCompleted = routinesCompleted,
                    stageNumber = evolution.stageLevel,
                    stageName = evolution.stageName,
                    xpToNextStageMessage = xpMessage,
                    stageAvatarRes = stageAvatarRes,
                    mainAvatarRes = R.drawable.ic_shleepy_avatar // Detailed artwork
                )
            } catch (e: Exception) {
                // Log and keep default empty state
                e.printStackTrace()
            }
        }
    }

    @DrawableRes
    private fun getAvatarResForStage(stageNumber: Int): Int {
        return when (stageNumber) {
            1 -> R.drawable.ic_shleepy_stage_1  // The Depleted
            2 -> R.drawable.ic_shleepy_stage_2  // The Awakening
            3 -> R.drawable.ic_shleepy_stage_3  // The Charged
            4 -> R.drawable.ic_shleepy_stage_4  // The Peak Overdrive
            5 -> R.drawable.ic_shleepy_stage_5  // The Zen Master
            else -> R.drawable.ic_shleepy_stage_1
        }
    }

    fun updateDisplayName(context: Context, newName: String) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                userProfileRepository.updateDisplayName(userId, newName)
                // Update local state so the UI reflects the change immediately
                _profileData.value = _profileData.value.copy(displayName = newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class ProfileUiState(
    val displayName: String = "",
    val email: String? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val routinesCompleted: Int = 0,
    val stageNumber: Int = 1,
    val stageName: String = "The Depleted",
    val xpToNextStageMessage: String = "",
    @DrawableRes val stageAvatarRes: Int = R.drawable.ic_shleepy_stage_1,
    @DrawableRes val mainAvatarRes: Int = R.drawable.ic_shleepy_avatar
)
