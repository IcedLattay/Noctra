package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Activity(
    @SerialName ("activity_id") val activityId: String,
    val label: String,
    val description: String,
    val instruction: String,
    @SerialName("activity_type") val activityType: String,
    @SerialName("default_duration_minutes") val defaultDurationMinutes: Int,
    @SerialName("icon_asset") val iconAsset: String,
    @SerialName("sort_order") val sortOrder: Int
)
