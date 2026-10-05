package com.noctra.app.data.repository

import android.util.Log
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

class SleepRecordRepository {
    private val client = SupabaseClient.client
    private val tag = "SleepRecordRepo"

    /**
     * Returns all sleep records for the given user within the date range (inclusive).
     * Dates are in YYYY-MM-DD format.
     */
    suspend fun getRecordsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<SleepRecord> {
        return try {
            client.from("sleep_records")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("session_date", startDate)
                        lte("session_date", endDate)
                    }
                    order("session_date", Order.ASCENDING)
                }
                .decodeList<SleepRecord>()
        } catch (e: IOException) {
            Log.e(tag, "Network error fetching records in range", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout fetching records in range", e)
            throw e
        }
    }

    /**
     * Returns the most recent sleep record for the user, or null if none exist.
     * Used by the Last Night card.
     */
    suspend fun getMostRecentRecord(userId: String): SleepRecord? {
        return try {
            client.from("sleep_records")
                .select {
                    filter { eq("user_id", userId) }
                    order("session_date", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<SleepRecord>()
        } catch (e: IOException) {
            Log.e(tag, "Network error fetching most recent record", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout fetching most recent record", e)
            throw e
        }
    }

    /**
     * Returns the earliest session_date the user has ever recorded, as an
     * ISO date string ("YYYY-MM-DD"), or null if they have no rows at all.
     *
     * ANALYTICS_SPEC.md §3: the 7-day window, the 30-day window and the
     * "Week N" counter all anchor to the first recorded night rather than
     * to profile creation — the journey starts when tracking starts.
     */
    suspend fun getEarliestSessionDate(userId: String): String? {
        return try {
            client.from("sleep_records")
                .select {
                    filter { eq("user_id", userId) }
                    order("session_date", Order.ASCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<SessionDateOnly>()
                ?.sessionDate
        } catch (e: Exception) {
            // A missing column or table must not take the whole screen down;
            // callers fall back to user_profiles.created_at.
            Log.w(tag, "Could not read earliest sleep_records date", e)
            null
        }
    }

    @kotlinx.serialization.Serializable
    private data class SessionDateOnly(
        @kotlinx.serialization.SerialName("session_date") val sessionDate: String
    )

    /**
     * Returns the sleep record for a specific date, or null if none exists.
     */
    suspend fun getSleepRecordForDate(userId: String, date: String): SleepRecord? {
        return client.from("sleep_records")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("session_date", date)
                }
                limit(1)
            }
            .decodeSingleOrNull<SleepRecord>()
    }

    suspend fun getLatestSleepRecord(userId: String): SleepRecord? {
        return getMostRecentRecord(userId)
    }

    /**
     * Inserts a single record. Uses upsert to handle re-syncs or duplicate simulation.
     */
    suspend fun insertRecord(record: SleepRecord) {
        try {
            client.from("sleep_records").upsert(record, onConflict = "id")
        } catch (e: IOException) {
            Log.e(tag, "Network error inserting record", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout inserting record", e)
            throw e
        }
    }

    suspend fun insertSleepRecord(record: SleepRecord) {
        insertRecord(record)
    }

    /**
     * Fills only the bedtime stamp on one night's row. Used by the sync's
     * NoData path to converge pre-feature rows — never overwrites, never
     * touches scores or times.
     */
    suspend fun stampTargetBedtime(userId: String, sessionDate: String, bedtime: String) {
        try {
            client.from("sleep_records").update(
                {
                    set("target_bedtime", bedtime)
                }
            ) {
                filter {
                    eq("user_id", userId)
                    eq("session_date", sessionDate)
                }
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error stamping bedtime", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout stamping bedtime", e)
            throw e
        }
    }

    /**
     * Inserts multiple records in one call. Used by dev seed function.
     */
    suspend fun insertRecords(records: List<SleepRecord>) {
        try {
            client.from("sleep_records").upsert(records, onConflict = "id")
        } catch (e: IOException) {
            Log.e(tag, "Network error inserting records", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout inserting records", e)
            throw e
        }
    }

    /**
     * Deletes all sleep records for a user. Used by dev seed function (re-seed).
     */
    suspend fun deleteAllForUser(userId: String) {
        try {
            client.from("sleep_records").delete {
                filter { eq("user_id", userId) }
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error deleting records", e)
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Timeout deleting records", e)
            throw e
        }
    }
}
