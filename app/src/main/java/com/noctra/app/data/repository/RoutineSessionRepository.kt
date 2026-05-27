package com.noctra.app.data.repository

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

/**
 * RoutineSessionRepository
 *
 * Data Access Layer for:
 *   - routine_sessions  (read + write)
 *
 * Responsibilities:
 *   - Starting a new session when the user taps "Start My Routine"
 *   - Completing a session when the user taps "Complete Routine"
 *   - Checking tonight's completion status for RoutineHomeFragment
 *   - Fetching the current streak count for display and reward calculation
 *   - Recording a missed session (called by MissedSessionCheckerWorker)
 *   - Resetting streak on consecutive missed nights
 *
 * All functions are suspend — call from a coroutine scope (ViewModel or Worker).
 */
class RoutineSessionRepository {

    private val client = SupabaseClient.client

    // ─── Session Lifecycle ───────────────────────────────────────────────────

    /**
     * Creates a new routine_sessions row when the user taps "Start My Routine."
     * Returns the created session (with its generated UUID from Supabase).
     *
     * @param userId            Anonymous UUID from UserSession / SharedPreferences.
     * @param routineConfigId   The active routine_configurations row UUID.
     * @param sessionDate       ISO date string for today (e.g. "2025-05-27").
     * @param startTimestamp    ISO datetime string of when the user tapped Start.
     */
    suspend fun startSession(
        userId: String,
        routineConfigId: String,
        sessionDate: String,
        startTimestamp: String
    ): RoutineSession {
        val newSession = mapOf(
            "user_id"           to userId,
            "routine_config_id" to routineConfigId,
            "session_date"      to sessionDate,
            "start_timestamp"   to startTimestamp,
            "is_completed"      to false
        )

        return client
            .from("routine_sessions")
            .insert(newSession) {
                select()
            }
            .decodeSingle<RoutineSession>()
    }

    /**
     * Marks an existing session as completed.
     * Called when the user taps "Complete Routine" OR when MorningSyncWorker
     * detects sleep onset within 60 minutes of routine start.
     *
     * @param sessionId             The UUID of the session row to update.
     * @param completionTimestamp   ISO datetime string of completion.
     * @param streakAtCompletion    The user's streak count at time of completion.
     * @param multiplierApplied     The streak multiplier tier used for reward calc.
     * @param tokensEarned          Dream Tokens awarded for this session.
     * @param xpEarned              Growth Points (XP) awarded for this session.
     */
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
            .update(
                mapOf(
                    "is_completed"          to true,
                    "completion_timestamp"  to completionTimestamp,
                    "streak_at_completion"  to streakAtCompletion,
                    "multiplier_applied"    to multiplierApplied,
                    "tokens_earned"         to tokensEarned,
                    "xp_earned"             to xpEarned
                )
            ) {
                filter { eq("id", sessionId) }
            }
    }

    // ─── Completion Checks ───────────────────────────────────────────────────

    /**
     * Returns true if the user already has a completed session for the given date.
     * Used by RoutineHomeFragment to show "Routine Complete" state instead of
     * "Start My Routine."
     *
     * @param userId        The anonymous user UUID.
     * @param sessionDate   ISO date string for today (e.g. "2025-05-27").
     */
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

    /**
     * Returns the most recent completed session for the user, or null if none exist.
     * Used by MorningSyncWorker to check if last night was completed.
     */
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

    /**
     * Returns the in-progress session for the user on a given date (is_completed = false).
     * Used to resume a session if the app is killed mid-routine.
     *
     * @param userId        The anonymous user UUID.
     * @param sessionDate   ISO date string for today.
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
                    eq("is_completed", false)
                }
            }
            .decodeSingleOrNull<RoutineSession>()
    }

    // ─── Streak ──────────────────────────────────────────────────────────────

    /**
     * Computes the current streak by counting consecutive completed sessions
     * working backwards from yesterday.
     *
     * Logic:
     *   - Fetch all sessions ordered by session_date DESC.
     *   - Walk back from yesterday; count consecutive completed dates.
     *   - Stop the moment a date is missing or is_completed = false.
     *
     * NOTE: today's session is NOT counted here — streak_at_completion in the
     * completed session row already reflects the correct streak including today.
     * This function is used by RoutineHomeViewModel to display the pre-completion
     * streak on the home screen.
     *
     * @param userId    The anonymous user UUID.
     * @return          Current streak count (0 if no completed sessions).
     */
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

        if (allSessions.isEmpty()) return 0

        var streak = 0
        var expectedDate = getPreviousDateString(getTodayDateString())

        for (session in allSessions) {
            if (session.sessionDate == expectedDate) {
                streak++
                expectedDate = getPreviousDateString(expectedDate)
            } else if (session.sessionDate < expectedDate) {
                // Gap found — streak is broken
                break
            }
            // Skip duplicate dates (edge case)
        }

        return streak
    }

    // ─── Missed Session ──────────────────────────────────────────────────────

    /**
     * Records a missed session for a given date.
     * Called by MissedSessionCheckerWorker at 9:30 AM when no completed session
     * is found for the previous date.
     *
     * Inserts a row with is_completed = false and no completion_timestamp,
     * which serves as the missed-night record for analytics.
     *
     * @param userId        The anonymous user UUID.
     * @param sessionDate   ISO date string for the missed date (yesterday).
     */
    suspend fun recordMissedSession(
        userId: String,
        sessionDate: String
    ) {
        // Only insert if a record doesn't already exist for that date
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
            val missedSession = mapOf(
                "user_id"      to userId,
                "session_date" to sessionDate,
                // start_timestamp is required by schema — use midnight of that date
                "start_timestamp" to "${sessionDate}T00:00:00",
                "is_completed" to false
            )
            client
                .from("routine_sessions")
                .insert(missedSession)
        }
    }

    /**
     * Returns all sessions for the user within a date range (inclusive).
     * Used by AnalyticsViewModel and WeeklyReportViewModel to build charts.
     *
     * @param userId    The anonymous user UUID.
     * @param from      Start date string (ISO, e.g. "2025-05-20").
     * @param to        End date string (ISO, e.g. "2025-05-26").
     */
    suspend fun getSessionsInRange(
        userId: String,
        from: String,
        to: String
    ): List<RoutineSession> {
        return client
            .from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("session_date", from)
                    lte("session_date", to)
                }
                order("session_date", Order.ASCENDING)
            }
            .decodeList<RoutineSession>()
    }

    // ─── Date Helpers ────────────────────────────────────────────────────────

    /**
     * Returns today's date as an ISO string (e.g. "2025-05-27").
     * Using java.time.LocalDate — minSdk 26+ required (matches project target).
     */
    fun getTodayDateString(): String {
        return java.time.LocalDate.now().toString()
    }

    /**
     * Returns the date one day before the given ISO date string.
     * e.g. "2025-05-27" → "2025-05-26"
     */
    private fun getPreviousDateString(dateString: String): String {
        return java.time.LocalDate.parse(dateString).minusDays(1).toString()
    }
}