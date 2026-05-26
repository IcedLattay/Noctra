package com.noctra.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
}

data class SettingsUiState(
    val displayName: String = "",
    val email: String? = null,
    val targetBedtime: String? = null
)