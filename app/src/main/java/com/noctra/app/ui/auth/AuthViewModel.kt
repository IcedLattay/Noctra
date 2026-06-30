package com.noctra.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.AuthRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.PasswordStrength
import com.noctra.app.utils.PasswordStrengthEvaluator
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.OtpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val profileRepository = UserProfileRepository()
    private val passwordEvaluator = PasswordStrengthEvaluator()

    // Form fields
    val displayName = MutableStateFlow("")
    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // Reactive validation
    val passwordStrength: StateFlow<PasswordStrength> = password
        .map { passwordEvaluator.evaluate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PasswordStrength.EMPTY)

    val formIsValid: StateFlow<Boolean> = combine(
        displayName, email, password, confirmPassword
    ) { name, email, pass, confirm ->
        val nameTrimmed = name.trim()
        val emailTrimmed = email.trim()
        val nameValid = nameTrimmed.length >= 2
        val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()
        val passValid = pass.length >= 8
        val confirmValid = pass == confirm
        nameValid && emailValid && passValid && confirmValid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.login(email, pass)
                // On success, check onboarding status
                val userId = com.noctra.app.data.supabase.SupabaseClient.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    val profile = profileRepository.getOrCreateProfile(userId)
                    _authState.value = AuthState.Success(profile.onboardingCompleted)
                } else {
                    _authState.value = AuthState.Error("User session not found")
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("Email not confirmed", ignoreCase = true)) {
                    _authState.value = AuthState.UnverifiedEmail(email)
                } else {
                    _authState.value = AuthState.Error(msg.ifBlank { "Login failed" })
                }
            }
        }
    }

    fun signUp() {
        viewModelScope.launch {
            if (!formIsValid.value) return@launch
            
            _authState.value = AuthState.Loading
            try {
                authRepository.register(email.value, password.value, displayName.value)
                _authState.value = AuthState.Success(false) // New user
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.resetPassword(email)
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send reset email")
            }
        }
    }

    fun resendVerificationCode(email: String) {
        viewModelScope.launch {
            try {
                authRepository.sendVerificationEmail(email)
                _authState.value = AuthState.VerificationCodeSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to resend code")
            }
        }
    }

    fun verifyOtp(email: String, token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // For initial signup verification, Supabase expects the SIGNUP type
                authRepository.verifyOtp(email, token, OtpType.Email.SIGNUP)
                _authState.value = AuthState.OtpVerified
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Invalid or expired code")
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.updatePassword(newPassword)
                _authState.value = AuthState.PasswordUpdated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to update password")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object PasswordResetSent : AuthState()
        object PasswordUpdated : AuthState()
        object OtpVerified : AuthState()
        object VerificationCodeSent : AuthState()
        data class UnverifiedEmail(val email: String) : AuthState()
        data class Success(val onboardingCompleted: Boolean) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
