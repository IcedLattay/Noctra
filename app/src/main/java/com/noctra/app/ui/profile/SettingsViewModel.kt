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
import com.noctra.app.data.repository.AuthRepository
import com.noctra.app.utils.DemoDataSeeder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.receivers.WindDownNotificationReceiver

class SettingsViewModel : ViewModel() {

    private val userProfileRepository = UserProfileRepository()
    private val authRepository = AuthRepository()

    private val _profileState = MutableStateFlow(SettingsUiState())
    val profileState = _profileState.asStateFlow()

    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState.Idle)
    val settingsState = _settingsState.asStateFlow()

    fun loadProfile(context: Context) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context) ?: return@launch
                val profile = userProfileRepository.getOrCreateProfile(userId)
                _profileState.value = SettingsUiState(
                    displayName = profile.displayName,
                    email = profile.email,
                    targetBedtime = profile.targetBedtime,
                    isEmailVerified = profile.isEmailVerified
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateTargetBedtime(context: Context, newBedtime: String) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context) ?: return@launch
                userProfileRepository.updateTargetBedtime(userId, newBedtime)
                _profileState.value = _profileState.value.copy(targetBedtime = newBedtime)
                
                // Update local cache
                com.noctra.app.utils.NotificationPreferences.updateCachedSettings(context, bedtime = newBedtime)
                
                // Refresh the notification schedule whenever bedtime changes
                com.noctra.app.workers.WindDownNotificationScheduler.scheduleNext(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seedDemoData(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = UserSession.getUserId(context) ?: run {
                    onComplete(false)
                    return@launch
                }

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
                val userId = UserSession.getUserId(context) ?: run {
                    onComplete(false)
                    return@launch
                }
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

    fun triggerTestNotification(context: Context) {
        val intent = android.content.Intent(context, WindDownNotificationReceiver::class.java)
        context.sendBroadcast(intent)
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _settingsState.value = SettingsState.Loading
            try {
                authRepository.resetPassword(email)
                _settingsState.value = SettingsState.PasswordResetSent
            } catch (e: Exception) {
                _settingsState.value = SettingsState.Error(e.message ?: "Failed to send reset link")
            }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            val email = _profileState.value.email ?: return@launch
            _settingsState.value = SettingsState.Loading
            try {
                authRepository.sendVerificationEmail(email)
                _settingsState.value = SettingsState.VerificationSent
            } catch (e: Exception) {
                _settingsState.value = SettingsState.Error(e.message ?: "Failed to send verification email")
            }
        }
    }

    fun resetState() {
        _settingsState.value = SettingsState.Idle
    }

    sealed class SettingsState {
        object Idle : SettingsState()
        object Loading : SettingsState()
        object PasswordResetSent : SettingsState()
        object VerificationSent : SettingsState()
        data class Error(val message: String) : SettingsState()
    }
}

data class SettingsUiState(
    val displayName: String = "",
    val email: String? = null,
    val targetBedtime: String? = null,
    val isEmailVerified: Boolean = false
)