package com.noctra.app.data.repository

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.RoutineSessionStatus
import com.noctra.app.data.model.SessionCompletionStatus
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RoutineSessionRepository
 *
 * Data Access Layer for:
 *   - routine_sessions  (read + write)
 *
 * UPDATED per DB Migration 1 (8/28/26): the is_completed:Boolean column
 * was dropped and replaced with a status:String column
 * (PENDING/COMPLETED/MISSED). Every function below that used to filter or
 * write is_completed now uses SessionCompletionStatus instead — see
 * RoutineSession.kt for that enum's definition.
 */
class RoutineSessionRepository {

    private val client = SupabaseClient.client

    // ─── Session Lifecycle ───────────────────────────────────────────────────

    suspend fun startSession(
        userId: String,
        routineConfigId: String,
        sessionDate: String,
        startTimestamp: String
    ): RoutineSession {
        return client
            .from("routine_sessions")
            .insert(NewSessionInsert(
                userId = userId,
                routineConfigId = routineConfigId,
                sessionDate = sessionDate,
                startTimestamp = startTimestamp
            )) {
                select()
            }
            .decodeSingle<RoutineSession>()
    }

    suspend fun completeSession(
        sessionId: String,
        completionTimestamp: String,
        streakAtCompletion: Int,
        multiplierApplied: Double,
        tokensEarned: Int,
        xpEarned: Int
    ) {
        client
            .from("routine_sessions")
            .update(SessionCompletion(
                completionTimestamp = completionTimestamp,
                streakAtCompletion = streakAtCompletion,
                multiplierApplied = multiplierApplied,
                tokensEarned = tokensEarned,
                xpEarned = xpEarned
            )) {
                filter { eq("id", sessionId) }
            }
    }

    // ─── Safety Net / Abandonment ────────────────────────────────────────────

    /**
     * Marks a session as ABANDONED_PENDING_DIAGNOSIS — used when the 60-minute
     * Safety Net timer expires before the user finishes or explicitly resumes
     * the routine. This writes to session_status, NOT completionStatus/status
     * — those are separate concerns (see RoutineSession.kt).
     */
    suspend fun markSessionAsAbandoned(sessionId: String) {
        client
            .from("routine_sessions")
            .update(SessionAbandonment()) {
                filter { eq("id", sessionId) }
            }
    }

    // ─── Completion Checks ───────────────────────────────────────────────────

    suspend fun hasCompletedSessionForDate(
        userId: String,
        sessionDate: String
    ): Boolean {
        val result = client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", sessionDate)
                    eq("status", SessionCompletionStatus.COMPLETED)
                }
            }
            .decodeList<RoutineSession>()

        return result.isNotEmpty()
    }

    suspend fun getSessionById(sessionId: String): RoutineSession? {
        return client.from("routine_sessions")
            .select { filter { eq("id", sessionId) } }
            .decodeSingleOrNull<RoutineSession>()
    }

    suspend fun getLastCompletedSession(userId: String): RoutineSession? {
        return client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", SessionCompletionStatus.COMPLETED)
                }
                order("completion_timestamp", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<RoutineSession>()
    }

    /**
     * FLAG: previously filtered is_completed == false, which matched BOTH
     * "not yet done" and "missed" sessions. Now filters status == PENDING
     * specifically, which is narrower — a MISSED session for today's date
     * will no longer be returned here. This seems like the more correct
     * behavior (a MISSED session isn't "in progress"), but flagging the
     * behavior change in case something upstream relied on the old,
     * broader is_completed==false match.
     */
    suspend fun getInProgressSession(
        userId: String,
        sessionDate: String
    ): RoutineSession? {
        return client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", sessionDate)
                    eq("status", SessionCompletionStatus.PENDING)
                }
            }
            .decodeSingleOrNull<RoutineSession>()
    }

    fun getTodayDateString(): String {
        return java.time.LocalDate.now().toString()
    }

    suspend fun getSessionsByDate(
        userId: String,
        sessionDate: String
    ): List<RoutineSession> {
        return client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", sessionDate)
                }
            }
            .decodeList<RoutineSession>()
    }

    // ─── Streak ──────────────────────────────────────────────────────────────

    suspend fun countCompletedSessions(userId: String): Int {
        return client.from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", SessionCompletionStatus.COMPLETED)
                }
            }
            .decodeList<RoutineSession>()
            .size
    }

    suspend fun getCurrentStreak(userId: String): Int {
        val allSessions = client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", SessionCompletionStatus.COMPLETED)
                }
                order("session_date", Order.DESCENDING)
            }
            .decodeList<RoutineSession>()

        if (allSessions.isEmpty()) {
            return 0
        }

        val completedDates: Set<String> = allSessions.map { it.sessionDate }.toSet()
        val today = java.time.LocalDate.now().toString()
        var expectedDate = if (completedDates.contains(today)) {
            today
        } else {
            java.time.LocalDate.parse(today).minusDays(1).toString()
        }

        var streak = 0
        while (completedDates.contains(expectedDate)) {
            streak++
            expectedDate = java.time.LocalDate.parse(expectedDate).minusDays(1).toString()
        }

        return streak
    }

    // ─── Missed Session ──────────────────────────────────────────────────────

    suspend fun recordMissedSession(
        userId: String,
        sessionDate: String
    ) {
        val existing = client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", sessionDate)
                }
            }
            .decodeList<RoutineSession>()

        if (existing.isEmpty()) {
            client
                .from("routine_sessions")
                .insert(MissedSessionInsert(
                    userId = userId,
                    sessionDate = sessionDate,
                    startTimestamp = "${sessionDate}T00:00:00"
                ))
        }
    }

    suspend fun getSessionsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<RoutineSession> {
        return client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("session_date", startDate)
                    lte("session_date", endDate)
                }
                order("session_date", Order.ASCENDING)
            }
            .decodeList<RoutineSession>()
    }

    // ─── Database Sync Helpers ──────────────────────────────────────────────

    suspend fun insertSession(session: RoutineSession) {
        client.from("routine_sessions").insert(session)
    }

    suspend fun insertSessions(sessions: List<RoutineSession>) {
        client.from("routine_sessions").insert(sessions)
    }

    suspend fun deleteAllForUser(userId: String) {
        client.from("routine_sessions").delete {
            filter { eq("user_id", userId) }
        }
    }

    @Serializable
    private data class NewSessionInsert(
        @SerialName("user_id") val userId: String,
        @SerialName("routine_config_id") val routineConfigId: String,
        @SerialName("session_date") val sessionDate: String,
        @SerialName("start_timestamp") val startTimestamp: String,
        @SerialName("status") val status: SessionCompletionStatus = SessionCompletionStatus.PENDING
    )

    @Serializable
    private data class MissedSessionInsert(
        @SerialName("user_id") val userId: String,
        @SerialName("session_date") val sessionDate: String,
        @SerialName("start_timestamp") val startTimestamp: String,
        @SerialName("status") val status: SessionCompletionStatus = SessionCompletionStatus.MISSED
    )

    @Serializable
    private data class SessionCompletion(
        @SerialName("status") val status: SessionCompletionStatus = SessionCompletionStatus.COMPLETED,
        @SerialName("completion_timestamp") val completionTimestamp: String,
        @SerialName("streak_at_completion") val streakAtCompletion: Int,
        @SerialName("multiplier_applied") val multiplierApplied: Double,
        @SerialName("tokens_earned") val tokensEarned: Int,
        @SerialName("xp_earned") val xpEarned: Int
    )

    @Serializable
    private data class SessionAbandonment(
        @SerialName("session_status") val sessionStatus: RoutineSessionStatus = RoutineSessionStatus.ABANDONED_PENDING_DIAGNOSIS
    )
}