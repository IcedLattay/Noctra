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
    @SerialName("onboarding_step") val onboardingStep: Int = 0,
    @SerialName("hr_baseline_bpm") val hrBaselineBpm: Double? = null
)