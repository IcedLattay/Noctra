package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoutineActivityEntry(
    @SerialName("activity_id") val activityId: String,
    val order: Int
)
