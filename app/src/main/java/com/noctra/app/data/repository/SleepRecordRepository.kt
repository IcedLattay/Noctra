package com.noctra.app.data.repository

import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate

class SleepRecordRepository {
    private val client = SupabaseClient.client

    /**
     * Returns all sleep records for the given user within the date range (inclusive).
     * Dates are in YYYY-MM-DD format.
     */
    suspend fun getRecordsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<SleepRecord> {
        return client.from("sleep_records")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("session_date", startDate)
                    lte("session_date", endDate)
                }
                order("session_date", Order.ASCENDING)
            }
            .decodeList<SleepRecord>()
    }

    /**
     * Returns the most recent sleep record for the user, or null if none exist.
     * Used by the Last Night card.
     */
    suspend fun getMostRecentRecord(userId: String): SleepRecord? {
        return client.from("sleep_records")
            .select {
                filter { eq("user_id", userId) }
                order("session_date", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<SleepRecord>()
    }

    suspend fun getLatestSleepRecord(userId: String): SleepRecord? {
        return getMostRecentRecord(userId)
    }

    /**
     * Inserts a single record.
     */
    suspend fun insertRecord(record: SleepRecord) {
        client.from("sleep_records").insert(record)
    }

    suspend fun insertSleepRecord(record: SleepRecord) {
        insertRecord(record)
    }

    /**
     * Inserts multiple records in one call. Used by dev seed function.
     */
    suspend fun insertRecords(records: List<SleepRecord>) {
        client.from("sleep_records").insert(records)
    }

    /**
     * Deletes all sleep records for a user. Used by dev seed function (re-seed).
     */
    suspend fun deleteAllForUser(userId: String) {
        client.from("sleep_records").delete {
            filter { eq("user_id", userId) }
        }
    }
}