package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class RoutineConfiguration(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("activity_sequence") val activitySequence: JsonArray,
    @SerialName("total_duration_minutes") val totalDurationMinutes: Int,
    @SerialName("is_active") val isActive: Boolean = true
)
