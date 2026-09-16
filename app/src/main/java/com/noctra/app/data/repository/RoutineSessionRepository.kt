package com.noctra.app.data.repository

import android.util.Log
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * RoutineSessionRepository
 *
 * Data Access Layer for:
 *   - routine_sessions  (read + write)
 *
 * UPDATED per DB Migration 1 (8/28/26): the is_completed:Boolean column
 * was dropped and replaced with a status:String column
 * (PENDING/COMPLETED/MISSED).
 */
class RoutineSessionRepository {

    private val client = SupabaseClient.client
    private val tag = "RoutineSessionRepo"

    // ─── Session Lifecycle ───────────────────────────────────────────────────

    suspend fun startSession(
        userId: String,
        routineConfigId: String,
        sessionDate: String,
        startTimestamp: String
    ): RoutineSession {
        return try {
            client
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
        } catch (e: IOException) {
            Log.e(tag, "Network error starting session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout starting session", e)
            throw e
        }
    }

    suspend fun completeSession(
        sessionId: String,
        completionTimestamp: String,
        streakAtCompletion: Int,
        multiplierApplied: Double,
        tokensEarned: Int,
        xpEarned: Int
    ) {
        try {
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
        } catch (e: IOException) {
            Log.e(tag, "Network error completing session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout completing session", e)
            throw e
        }
    }

    // ─── Completion Checks ───────────────────────────────────────────────────

    suspend fun hasCompletedSessionForDate(
        userId: String,
        sessionDate: String
    ): Boolean {
        return try {
            val result = client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("session_date", sessionDate)
                        eq("status", "COMPLETED")
                    }
                }
                .decodeList<RoutineSession>()

            result.isNotEmpty()
        } catch (e: IOException) {
            Log.e(tag, "Network error checking completed session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout checking completed session", e)
            throw e
        }
    }

    suspend fun getSessionById(sessionId: String): RoutineSession? {
        return try {
            client.from("routine_sessions")
                .select { filter { eq("id", sessionId) } }
                .decodeSingleOrNull<RoutineSession>()
        } catch (e: IOException) {
            Log.e(tag, "Network error getting session by id", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting session by id", e)
            throw e
        }
    }

    suspend fun getLastCompletedSession(userId: String): RoutineSession? {
        return try {
            client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "COMPLETED")
                    }
                    order("completion_timestamp", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<RoutineSession>()
        } catch (e: IOException) {
            Log.e(tag, "Network error getting last completed session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting last completed session", e)
            throw e
        }
    }

    suspend fun getInProgressSession(
        userId: String,
        sessionDate: String
    ): RoutineSession? {
        return try {
            client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("session_date", sessionDate)
                        eq("status", "PENDING")
                    }
                }
                .decodeSingleOrNull<RoutineSession>()
        } catch (e: IOException) {
            Log.e(tag, "Network error getting in-progress session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting in-progress session", e)
            throw e
        }
    }

    suspend fun getOldestPendingSession(userId: String): RoutineSession? {
        return try {
            client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "PENDING")
                    }
                    order("session_date", Order.ASCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<RoutineSession>()
        } catch (e: IOException) {
            Log.e(tag, "Network error getting oldest pending session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting oldest pending session", e)
            throw e
        }
    }

    fun getTodayDateString(): String {
        return java.time.LocalDate.now().toString()
    }

    suspend fun getSessionsByDate(
        userId: String,
        sessionDate: String
    ): List<RoutineSession> {
        return try {
            client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("session_date", sessionDate)
                    }
                }
                .decodeList<RoutineSession>()
        } catch (e: IOException) {
            Log.e(tag, "Network error getting sessions by date", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting sessions by date", e)
            throw e
        }
    }

    // ─── Streak ──────────────────────────────────────────────────────────────

    suspend fun countCompletedSessions(userId: String): Int {
        return try {
            client.from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "COMPLETED")
                    }
                }
                .decodeList<RoutineSession>()
                .size
        } catch (e: IOException) {
            Log.e(tag, "Network error counting completed sessions", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout counting completed sessions", e)
            throw e
        }
    }

    suspend fun getCurrentStreak(userId: String): Int {
        return try {
            val allSessions = client
                .from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "COMPLETED")
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

            streak
        } catch (e: IOException) {
            Log.e(tag, "Network error getting current streak", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting current streak", e)
            throw e
        }
    }

    // ─── Missed Session ──────────────────────────────────────────────────────

    suspend fun recordMissedSession(
        userId: String,
        sessionDate: String
    ) {
        try {
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
        } catch (e: IOException) {
            Log.e(tag, "Network error recording missed session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout recording missed session", e)
            throw e
        }
    }

    suspend fun getSessionsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<RoutineSession> {
        return try {
            client
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
        } catch (e: IOException) {
            Log.e(tag, "Network error getting sessions in range", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout getting sessions in range", e)
            throw e
        }
    }

    // ─── Database Sync Helpers ──────────────────────────────────────────────

    suspend fun insertSession(session: RoutineSession) {
        try {
            client.from("routine_sessions").upsert(session, onConflict = "id")
        } catch (e: IOException) {
            Log.e(tag, "Network error inserting session", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout inserting session", e)
            throw e
        }
    }

    suspend fun insertSessions(sessions: List<RoutineSession>) {
        try {
            client.from("routine_sessions").upsert(sessions, onConflict = "id")
        } catch (e: IOException) {
            Log.e(tag, "Network error inserting sessions", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout inserting sessions", e)
            throw e
        }
    }

    suspend fun deleteAllForUser(userId: String) {
        try {
            client.from("routine_sessions").delete {
                filter { eq("user_id", userId) }
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error deleting sessions", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout deleting sessions", e)
            throw e
        }
    }

    suspend fun finalizeOldPendingSessions(userId: String) {
        try {
            val fourteenDaysAgo = java.time.LocalDate.now().minusDays(14).toString()
            client.from("routine_sessions")
                .update(mapOf("status" to "MISSED")) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "PENDING")
                        lt("session_date", fourteenDaysAgo)
                    }
                }
        } catch (e: IOException) {
            Log.e(tag, "Network error finalizing old pending sessions", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout finalizing old pending sessions", e)
            throw e
        }
    }

    @Serializable
    private data class NewSessionInsert(
        @SerialName("user_id") val userId: String,
        @SerialName("routine_config_id") val routineConfigId: String,
        @SerialName("session_date") val sessionDate: String,
        @SerialName("start_timestamp") val startTimestamp: String,
        val status: String = "PENDING"
    )

    @Serializable
    private data class MissedSessionInsert(
        @SerialName("user_id") val userId: String,
        @SerialName("session_date") val sessionDate: String,
        @SerialName("start_timestamp") val startTimestamp: String,
        val status: String = "MISSED"
    )

    @Serializable
    private data class SessionCompletion(
        val status: String = "COMPLETED",
        @SerialName("completion_timestamp") val completionTimestamp: String,
        @SerialName("streak_at_completion") val streakAtCompletion: Int,
        @SerialName("multiplier_applied") val multiplierApplied: Double,
        @SerialName("tokens_earned") val tokensEarned: Int,
        @SerialName("xp_earned") val xpEarned: Int
    )
}
