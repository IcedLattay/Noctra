package com.noctra.app.data.repository

import android.util.Log
import com.noctra.app.data.model.UserProfile
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant

class UserProfileRepository {
    private val client = SupabaseClient.client
    private val tag = "UserProfileRepo"

    suspend fun getOrCreateProfile(userId: String): UserProfile {
        return try {
            val existing = client.from("user_profiles")
                .select { filter { eq("user_id", userId) }; limit(1) }
                .decodeSingleOrNull<UserProfile>()
            if (existing != null) return existing

            // Pre-fill createdAt: explicitNulls=true would otherwise send
            // an explicit null and block the DB DEFAULT now().
            val newProfile = UserProfile(userId = userId, createdAt = Instant.now().toString())
            client.from("user_profiles").insert(newProfile)

            client.from("reward_ledger").insert(mapOf(
                "user_id" to userId,
                "last_updated" to "now()"
            ))

            newProfile
        } catch (e: IOException) {
            Log.e(tag, "Network error getting/creating profile", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting/creating profile", e)
            throw e
        }
    }

    suspend fun updateDisplayName(userId: String, newName: String) {
        try {
            client.from("user_profiles").update({
                set("display_name", newName)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error updating display name", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout updating display name", e)
            throw e
        }
    }

    suspend fun updateTargetBedtime(userId: String, bedtime: String) {
        try {
            client.from("user_profiles").update({
                set("target_bedtime", bedtime)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error updating target bedtime", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout updating target bedtime", e)
            throw e
        }
    }

    suspend fun markOnboardingComplete(userId: String) {
        try {
            client.from("user_profiles").update({
                set("onboarding_completed", true)
                set("onboarding_step", 5)
                set("onboarding_completed_at", "now()")
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error marking onboarding complete", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout marking onboarding complete", e)
            throw e
        }
    }

    suspend fun updateOnboardingStep(userId: String, step: Int) {
        try {
            client.from("user_profiles").update({
                set("onboarding_step", step)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error updating onboarding step", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout updating onboarding step", e)
            throw e
        }
    }

    suspend fun saveDraft(userId: String, bedtime: String, activityIds: List<String>) {
        try {
            client.from("user_profiles").update({
                set("draft_bedtime", bedtime)
                set<List<String>>("draft_activity_ids", activityIds)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error saving onboarding draft", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout saving onboarding draft", e)
            throw e
        }
    }

    suspend fun clearDraft(userId: String) {
        try {
            client.from("user_profiles").update({
                set<String?>("draft_bedtime", null)
                set<List<String>?>("draft_activity_ids", null)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error clearing onboarding draft", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout clearing onboarding draft", e)
            throw e
        }
    }

    suspend fun resetOnboarding(userId: String) {
        try {
            client.from("user_profiles").update({
                set("onboarding_completed", false)
                set("onboarding_step", 0)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error resetting onboarding", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout resetting onboarding", e)
            throw e
        }
    }

    suspend fun updateEmailVerificationStatus(userId: String, verified: Boolean) {
        try {
            client.from("user_profiles").update({
                set("is_email_verified", verified)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error updating email verification", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout updating email verification", e)
            throw e
        }
    }

    suspend fun updateEmail(userId: String, email: String) {
        try {
            client.from("user_profiles").update({
                set("email", email)
                set("updated_at", "now()")
            }) { filter { eq("user_id", userId) } }
        } catch (e: IOException) {
            Log.e(tag, "Network error updating email", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout updating email", e)
            throw e
        }
    }
}
