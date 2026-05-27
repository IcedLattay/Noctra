package com.noctra.app.data.repository

import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.LocalDate

class SleepRecordRepository {
    private val client = SupabaseClient.client

    suspend fun getLatestSleepRecord(userId: String): SleepRecord? {
        return client.from("sleep_records")
            .select {
                filter {
                    eq("user_id", userId)
                }
                order("session_date", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }.decodeSingleOrNull<SleepRecord>()
    }
    
    suspend fun insertSleepRecord(record: SleepRecord) {
        client.from("sleep_records").insert(record)
    }
}
