package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "Sleepyhead",
    val email: String? = null,
    @SerialName("target_bedtime") val targetBedtime: String? = null,  // "HH:mm:ss"
    @SerialName("health_connect_granted") val healthConnectGranted: Boolean = false,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("onboarding_step") val onboardingStep: Int = 0,
    @SerialName("hr_baseline_bpm") val hrBaselineBpm: Double? = null,
    @SerialName("is_email_verified") val isEmailVerified: Boolean = false,
    @SerialName("draft_bedtime") val draftBedtime: String? = null,
    @SerialName("draft_activity_ids") val draftActivityIds: List<String>? = null,
    @SerialName("outfit_equipped") val outfitEquipped: String? = null
)