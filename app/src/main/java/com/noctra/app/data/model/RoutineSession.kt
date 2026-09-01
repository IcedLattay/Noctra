package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Distinguishes an in-progress routine session from one that timed out
 * without being finished or explicitly abandoned. Purely additive to the
 * Resume Logic feature — separate from `status` below (that one tracks
 * PENDING/COMPLETED/MISSED for sleep-data reconciliation, a different
 * concern owned by the Data Sync & Reconciliation feature).
 */
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
    val status: String = "PENDING",
    @SerialName("session_status") val sessionStatus: RoutineSessionStatus = RoutineSessionStatus.IN_PROGRESS,
    @SerialName("streak_at_completion") val streakAtCompletion: Int? = null,
    @SerialName("multiplier_applied") val multiplierApplied: Double? = null,
    @SerialName("tokens_earned") val tokensEarned: Int? = null,
    @SerialName("xp_earned") val xpEarned: Int? = null
)
