package com.noctra.app.data.repository

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class RoutineSessionRepository {
    private val client = SupabaseClient.client

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
}
