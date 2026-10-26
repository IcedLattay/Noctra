package com.noctra.app.data.repository

import android.util.Log
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * AppLogRepository
 *
 * Fire-and-forget writes to `public.app_log` — the admin panel's activity
 * feed. Logging must NEVER break the feature it is observing: every entry
 * point swallows its own failure, logs to logcat, and returns null.
 *
 * `parentId` links a sub-activity back to the event that caused it (e.g.
 * every `missed_insert` / `streak_change` written during a checker run hangs
 * off that run's `checker` row), which is what the panel renders as a thread.
 *
 * Run types (Postgres enum `run_type`):
 *   account_created, email_verified, onboarding_complete,
 *   start_routine, complete_routine, checker, sleep_insert,
 *   session_status, missed_insert, streak_change, xp_change
 */
object AppLogRepository {

    private const val TAG = "AppLogRepo"

    /** Minimal row used only to read back the generated id. */
    @Serializable
    private data class IdOnly(val id: String)

    /**
     * Writes one log row and returns its id, or null if logging failed.
     *
     * @param parentId id of the parent row when this entry is a sub-activity.
     */
    suspend fun log(
        userId: String,
        runType: String,
        eventDetail: String,
        details: JsonObject? = null,
        parentId: String? = null
    ): String? {
        return try {
            val now = LocalDateTime.now()
            val row = buildJsonObject {
                put("user_id", userId)
                put("run_type", runType)
                put("event_detail", eventDetail)
                // Written explicitly so the columns work whether they are
                // typed as date/time or as plain text.
                put("event_date", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
                put(
                    "event_time",
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                )
                if (details != null) put("details", details)
                if (parentId != null) put("parent_id", parentId)
            }

            SupabaseClient.client
                .from("app_log")
                .insert(row) { select() }
                .decodeSingle<IdOnly>()
                .id
        } catch (e: Exception) {
            Log.w(TAG, "app_log write failed (runType=$runType)", e)
            null
        }
    }

    /**
     * Convenience overload for details built from primitives — keeps call
     * sites from having to import buildJsonObject every time. Named
     * [logWith] so it never collides with the primary `log` overload.
     */
    suspend fun logWith(
        userId: String,
        runType: String,
        eventDetail: String,
        vararg pairs: Pair<String, String>,
        parentId: String? = null
    ): String? {
        if (pairs.isEmpty()) return log(userId, runType, eventDetail, details = null, parentId = parentId)
        val details = buildJsonObject {
            pairs.forEach { (k, v) -> put(k, v) }
        }
        return log(userId, runType, eventDetail, details, parentId)
    }

    /**
     * "2026-10-03" -> "October 3". No year: the panel groups entries by day.
     * Returns the input unchanged if it isn't a parseable date.
     */
    fun prettyDate(iso: String): String = try {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMMM d"))
    } catch (e: Exception) {
        iso
    }

    /**
     * Display name for log sentences like "User, Shleepy, finished
     * verification." Falls back to the app's default nickname rather than
     * failing the write it is decorating.
     */
    suspend fun displayNameOf(userId: String): String = try {
        UserProfileRepository().getOrCreateProfile(userId).displayName
            .ifBlank { "Sleepyhead" }
    } catch (e: Exception) {
        "Sleepyhead"
    }

    /**
     * A scheduled/in-app check firing. The trigger ("9:30 am", "5:00 pm",
     * "synchronous") rides in `details.trigger` — the sentence itself is
     * always the same, per the admin-panel spec.
     *
     * Returns the row id so sub-activity logs can hang off this run.
     */
    suspend fun logCheck(userId: String, trigger: String): String? =
        log(
            userId = userId,
            runType = "checker",
            eventDetail = "System performed check.",
            details = buildJsonObject { put("trigger", trigger) }
        )
}
