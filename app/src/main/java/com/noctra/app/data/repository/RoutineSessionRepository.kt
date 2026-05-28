package com.noctra.app.data.repository

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class RoutineSessionRepository {
    private val client = SupabaseClient.client

    /**
     * Returns all sessions for the user within the date range (inclusive).
     * Used for the Routine Completion row in Analytics.
     */
    suspend fun getSessionsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<RoutineSession> {
        return client.from("routine_sessions")
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

    suspend fun getSessionById(sessionId: String): RoutineSession? {
        return client.from("routine_sessions")
            .select { filter { eq("id", sessionId) } }
            .decodeSingleOrNull<RoutineSession>()
    }

    suspend fun getSessionsByDate(userId: String, date: String): List<RoutineSession> {
        return client.from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", date)
                }
            }.decodeList<RoutineSession>()
    }

    /**
     * Count of completed sessions for the user (used on Profile screen).
     */
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

    /**
     * Insert a routine session row.
     */
    suspend fun insertSession(session: RoutineSession) {
        client.from("routine_sessions").insert(session)
    }

    suspend fun insertSessions(sessions: List<RoutineSession>) {
        client.from("routine_sessions").insert(sessions)
    }

    /**
     * Deletes all sessions for a user.
     */
    suspend fun deleteAllForUser(userId: String) {
        client.from("routine_sessions").delete {
            filter { eq("user_id", userId) }
        }
    }
}