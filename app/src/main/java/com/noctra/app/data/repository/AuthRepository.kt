package com.noctra.app.data.repository

import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.OtpType
import kotlinx.coroutines.flow.Flow

class AuthRepository {
    private val auth = SupabaseClient.client.auth
    private val profileRepository = UserProfileRepository()

    /**
     * Authenticates a user with email and password.
     */
    suspend fun login(email: String, pass: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
    }

    /**
     * Registers a new user, creates their profile, and sets their display name.
     */
    suspend fun register(email: String, pass: String, displayName: String) {
        val userInfo = auth.signUpWith(Email) {
            this.email = email
            this.password = pass
        }

        val userId = userInfo?.id ?: throw Exception("User creation failed")
        
        // Initialize user profile
        profileRepository.getOrCreateProfile(userId)
        profileRepository.updateDisplayName(userId, displayName)
        profileRepository.updateEmail(userId, email)
    }

    /**
     * Triggers a password reset email via Supabase.
     */
    suspend fun resetPassword(email: String) {
        auth.resetPasswordForEmail(email)
    }

    /**
     * Signs the user out of the current session.
     */
    suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Updates the user's password.
     */
    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }

    /**
     * Verifies a 6-digit OTP code sent via email.
     */
    suspend fun verifyOtp(email: String, token: String, type: OtpType.Email) {
        auth.verifyEmailOtp(type, email, token)
    }

    /**
     * Triggers a verification email to the user.
     */
    suspend fun sendVerificationEmail(email: String) {
        auth.resendEmail(OtpType.Email.SIGNUP, email = email)
    }

    /**
     * Exposes the current authentication status as a Flow.
     */
    fun sessionStatus(): Flow<SessionStatus> {
        return auth.sessionStatus
    }
}
