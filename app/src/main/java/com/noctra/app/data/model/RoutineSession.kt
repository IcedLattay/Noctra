package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Distinguishes an in-progress routine session from one that timed out
 * without being finished or explicitly abandoned.
 *
 * NOTE: this is separate from SessionCompletionStatus below — that field
 * tracks PENDING / COMPLETED / MISSED for sleep-data reconciliation (Data
 * Sync & Reconciliation feature), a different concern (was sleep detected
 * for this session?) from this one (did the user finish the routine before
 * the Safety Net expired?). Named sessionStatus specifically to avoid a
 * field-name collision with that other field.
 */
@Serializable
enum class RoutineSessionStatus {
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("abandoned_pending_diagnosis") ABANDONED_PENDING_DIAGNOSIS
}

/**
 * Replaces the old is_completed:Boolean column (dropped in DB Migration 1,
 * 8/28/26). Values match exactly what's stored in the DB's `status` column:
 * PENDING, COMPLETED, MISSED — used for sleep-data reconciliation/audit,
 * not for resume-logic tracking (see RoutineSessionStatus above for that).
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
    // REMOVED per DB Migration 1 (8/28/26): is_completed column no longer
    // exists — replaced by completionStatus below, mapped to the new
    // `status` column (PENDING/COMPLETED/MISSED).
    @SerialName("status") val completionStatus: SessionCompletionStatus = SessionCompletionStatus.PENDING,
    @SerialName("session_status") val sessionStatus: RoutineSessionStatus = RoutineSessionStatus.IN_PROGRESS,
    @SerialName("streak_at_completion") val streakAtCompletion: Int? = null,
    @SerialName("multiplier_applied") val multiplierApplied: Double? = null,
    @SerialName("tokens_earned") val tokensEarned: Int? = null,
    @SerialName("xp_earned") val xpEarned: Int? = null
)