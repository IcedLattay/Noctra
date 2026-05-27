package com.noctra.app.data.repository

import com.noctra.app.data.model.UserProfile
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class UserProfileRepository {
    private val client = SupabaseClient.client

    suspend fun getOrCreateProfile(userId: String): UserProfile {
        val existing = client.from("user_profiles")
            .select { filter { eq("user_id", userId) }; limit(1) }
            .decodeSingleOrNull<UserProfile>()
        if (existing != null) return existing

        val newProfile = UserProfile(userId = userId)
        client.from("user_profiles").insert(newProfile)

        client.from("reward_ledger").insert(mapOf(
            "user_id" to userId,
            "last_updated" to "now()"
        ))

        return newProfile
    }

    suspend fun updateDisplayName(userId: String, newName: String) {
        client.from("user_profiles").update({
            set("display_name", newName)
            set("updated_at", "now()")
        }) { filter { eq("user_id", userId) } }
    }

    suspend fun updateTargetBedtime(userId: String, bedtime: String) {
        client.from("user_profiles").update({
            set("target_bedtime", bedtime)
            set("updated_at", "now()")
        }) { filter { eq("user_id", userId) } }
    }

    suspend fun markOnboardingComplete(userId: String) {
        client.from("user_profiles").update({
            set("onboarding_completed", true)
            set("updated_at", "now()")
        }) { filter { eq("user_id", userId) } }
    }

}