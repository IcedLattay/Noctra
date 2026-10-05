package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Replaces the old is_completed:Boolean column (dropped in DB Migration 1,
 * 8/28/26). Values match exactly what's stored in the DB's `status` column:
 * PENDING, COMPLETED, MISSED — used for sleep-data reconciliation/audit.
 */
@Serializable
enum class SessionCompletionStatus {
    @SerialName("PENDING") PENDING,
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("MISSED") MISSED
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
    @SerialName("streak_at_completion") val streakAtCompletion: Int? = null,
    @SerialName("multiplier_applied") val multiplierApplied: Double? = null,
    @SerialName("tokens_earned") val tokensEarned: Int? = null,
    @SerialName("xp_earned") val xpEarned: Int? = null
)