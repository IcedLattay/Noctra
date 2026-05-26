package com.noctra.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserProfileViewModel : ViewModel() {

    private val userProfileRepository = UserProfileRepository()

    private val _profileData = MutableStateFlow(ProfileUiState())
    val profileData = _profileData.asStateFlow()

    fun loadProfile(context: Context) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                val profile = userProfileRepository.getOrCreateProfile(userId)

                // TODO: Once Person B's RewardLedgerRepository exists, read real values
                // For now, use placeholder zeros for stats
                val currentStreak = 0
                val longestStreak = 0
                val totalXp = 0
                val routinesCompleted = 0

                val (stageNumber, stageName, xpMessage) = computeStageInfo(totalXp)

                _profileData.value = ProfileUiState(
                    displayName = profile.displayName,
                    email = profile.email,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    routinesCompleted = routinesCompleted,
                    stageNumber = stageNumber,
                    stageName = stageName,
                    xpToNextStageMessage = xpMessage
                )
            } catch (e: Exception) {
                // Log and keep default empty state
                e.printStackTrace()
            }
        }
    }

    /**
     * Computes evolution stage info from total XP.
     * Mirrors Person B's CompanionEvolutionUseCase — duplicated here for now,
     * will be replaced with a direct call once that use case is built.
     */
    private fun computeStageInfo(totalXp: Int): Triple<Int, String, String> {
        return when {
            totalXp < 1_500 -> Triple(
                1,
                "The Depleted",
                "You need ${1_500 - totalXp} XP for your Shleepy to evolve"
            )
            totalXp < 5_000 -> Triple(
                2,
                "The Awakening",
                "You need ${5_000 - totalXp} XP for your Shleepy to evolve"
            )
            totalXp < 15_000 -> Triple(
                3,
                "The Charged",
                "You need ${15_000 - totalXp} XP for your Shleepy to evolve"
            )
            totalXp < 50_000 -> Triple(
                4,
                "The Peak Overdrive",
                "You need ${50_000 - totalXp} XP for your Shleepy to evolve"
            )
            else -> Triple(
                5,
                "The Zen Master",
                "Your Shleepy has reached its peak form!"
            )
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
    val xpToNextStageMessage: String = ""
)