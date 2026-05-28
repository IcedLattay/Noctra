package com.noctra.app.data.repository

import com.noctra.app.data.model.RoutineSession
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
                    eq("is_completed", true)
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
                    eq("is_completed", true)
                }
                order("completion_timestamp", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<RoutineSession>()
    }

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
                    eq("is_completed", false)
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
                    eq("is_completed", true)
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
                    eq("is_completed", true)
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
        @SerialName("is_completed") val isCompleted: Boolean = false
    )

    @Serializable
    private data class MissedSessionInsert(
        @SerialName("user_id") val userId: String,
        @SerialName("session_date") val sessionDate: String,
        @SerialName("start_timestamp") val startTimestamp: String,
        @SerialName("is_completed") val isCompleted: Boolean = false
    )

    @Serializable
    private data class SessionCompletion(
        @SerialName("is_completed") val isCompleted: Boolean = true,
        @SerialName("completion_timestamp") val completionTimestamp: String,
        @SerialName("streak_at_completion") val streakAtCompletion: Int,
        @SerialName("multiplier_applied") val multiplierApplied: Double,
        @SerialName("tokens_earned") val tokensEarned: Int,
        @SerialName("xp_earned") val xpEarned: Int
    )
}