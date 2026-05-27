package com.noctra.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.utils.DemoDataSeeder

class SettingsViewModel : ViewModel() {

    private val userProfileRepository = UserProfileRepository()

    private val _profileState = MutableStateFlow(SettingsUiState())
    val profileState = _profileState.asStateFlow()

    fun loadProfile(context: Context) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                val profile = userProfileRepository.getOrCreateProfile(userId)
                _profileState.value = SettingsUiState(
                    displayName = profile.displayName,
                    email = profile.email,
                    targetBedtime = profile.targetBedtime
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateTargetBedtime(context: Context, newBedtime: String) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                userProfileRepository.updateTargetBedtime(userId, newBedtime)
                _profileState.value = _profileState.value.copy(targetBedtime = newBedtime)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seedDemoData(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)

                // Read current target bedtime, default to 10 PM if not set
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val targetBedtime = try {
                    profile.targetBedtime?.let { java.time.LocalTime.parse(it) }
                        ?: java.time.LocalTime.of(22, 0)
                } catch (e: Exception) {
                    java.time.LocalTime.of(22, 0)
                }

                val seeder = DemoDataSeeder(
                    sleepRepo = SleepRecordRepository(),
                    sessionRepo = RoutineSessionRepository()
                )
                seeder.seedLastSevenDays(userId, targetBedtime)
                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("DemoSeeder", "Seed failed", e)
                android.util.Log.e("DemoSeeder", "Error message: ${e.message}")
                android.util.Log.e("DemoSeeder", "Cause: ${e.cause?.message}")
                onComplete(false)
            }
        }
    }

    fun clearAnalyticsData(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context)
                val sleepRepo = SleepRecordRepository()
                val sessionRepo = RoutineSessionRepository()

                sleepRepo.deleteAllForUser(userId)
                sessionRepo.deleteAllForUser(userId)

                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to clear data", e)
                onComplete(false)
            }
        }
    }
}

data class SettingsUiState(
    val displayName: String = "",
    val email: String? = null,
    val targetBedtime: String? = null
)