package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RoutineSessionStatus {
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("abandoned_pending_diagnosis") ABANDONED_PENDING_DIAGNOSIS
}
@Serializable
data class RoutineSession(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("routine_config_id") val routineConfigId: String? = null,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("start_timestamp") val startTimestamp: String,
    @SerialName("completion_timestamp") val completionTimestamp: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("session_status") val sessionStatus: RoutineSessionStatus = RoutineSessionStatus.IN_PROGRESS,
    @SerialName("streak_at_completion") val streakAtCompletion: Int? = null,
    @SerialName("multiplier_applied") val multiplierApplied: Double? = null,
    @SerialName("tokens_earned") val tokensEarned: Int? = null,
    @SerialName("xp_earned") val xpEarned: Int? = null
)
