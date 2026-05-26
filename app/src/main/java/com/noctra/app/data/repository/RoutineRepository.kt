package com.noctra.app.data.repository

import com.noctra.app.data.model.Activity
import com.noctra.app.data.model.RoutineActivityEntry
import com.noctra.app.data.model.RoutineConfiguration
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * RoutineRepository
 *
 * Data Access Layer for:
 *   - activity_library  (read-only — seeded by backend)
 *   - routine_configurations  (read + write)
 *
 * Follows the exact pattern established in UserProfileRepository.
 * All functions are suspend — call from a coroutine scope (ViewModel or Use Case).
 */
class RoutineRepository {

    private val client = SupabaseClient.client

    // ─── Activity Library ────────────────────────────────────────────────────

    /**
     * Returns all 9 activities sorted by sort_order ascending.
     * This is the seeded, read-only catalog — never written by the client.
     */
    suspend fun getActivityLibrary(): List<Activity> {
        return client
            .from("activity_library")
            .select {
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<Activity>()
    }

    /**
     * Returns a single activity by its UUID.
     * Used when hydrating an activity_sequence JSONB array into full Activity objects.
     */
    suspend fun getActivityById(activityId: String): Activity? {
        return client
            .from("activity_library")
            .select {
                filter { eq("activity_id", activityId) }
            }
            .decodeSingleOrNull<Activity>()
    }

    /**
     * Returns the full Activity objects for a given list of RoutineActivityEntry items,
     * preserving the order field from the sequence.
     *
     * Loads all activities then maps them — avoids N+1 queries.
     */
    suspend fun hydrateActivitySequence(
        entries: List<RoutineActivityEntry>
    ): List<Activity> {
        val allActivities = getActivityLibrary()
        val activityMap = allActivities.associateBy { it.activityId }
        return entries
            .sortedBy { it.order }
            .mapNotNull { entry -> activityMap[entry.activityId] }
    }

    // ─── Routine Configuration ───────────────────────────────────────────────

    /**
     * Returns the active routine configuration for the given user, or null if
     * no configuration exists yet (e.g. fresh install, pre-onboarding).
     */
    suspend fun getActiveRoutine(userId: String): RoutineConfiguration? {
        return client
            .from("routine_configurations")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("is_active", true)
                }
            }
            .decodeSingleOrNull<RoutineConfiguration>()
    }

    /**
     * Parses the activity_sequence JsonArray from a RoutineConfiguration into
     * a typed list of RoutineActivityEntry objects.
     *
     * activity_sequence JSONB format:
     * [{"activity_id": "uuid", "order": 1}, ...]
     */
    fun parseActivitySequence(jsonArray: JsonArray): List<RoutineActivityEntry> {
        return jsonArray.map { element ->
            Json.decodeFromJsonElement<RoutineActivityEntry>(element)
        }
    }

    /**
     * Saves a new routine configuration for the user.
     *
     * Steps:
     *   1. Deactivate all existing configurations for this user.
     *   2. Insert the new configuration with is_active = true.
     *
     * This is intentionally two separate operations rather than an RPC because
     * the MVP runs without auth and RLS is disabled — atomic consistency is
     * acceptable at this scope.
     *
     * @param userId            The anonymous user UUID from UserSession.
     * @param activitySequence  Ordered list of activity entries (already sorted by .order).
     * @param totalDurationMinutes  Sum of default_duration_minutes for selected activities.
     * @return The newly created RoutineConfiguration row.
     */
    suspend fun saveRoutineConfiguration(
        userId: String,
        activitySequence: List<RoutineActivityEntry>,
        totalDurationMinutes: Int
    ): RoutineConfiguration {
        // Step 1 — deactivate any existing active config
        deactivateExistingConfigs(userId)

        // Step 2 — build the JSONB array as a raw JsonArray
        val sequenceJson = buildSequenceJson(activitySequence)

        // Step 3 — insert new config
        val newConfig = mapOf(
            "user_id" to userId,
            "activity_sequence" to sequenceJson,
            "total_duration_minutes" to totalDurationMinutes,
            "is_active" to true
        )

        return client
            .from("routine_configurations")
            .insert(newConfig) {
                select()
            }
            .decodeSingle<RoutineConfiguration>()
    }

    /**
     * Updates an existing routine configuration (edit flow).
     * Deactivates all existing configs and inserts a new active one —
     * same as save, since we always create a new row rather than mutating in place.
     * This preserves the history of routine changes for potential future analytics.
     */
    suspend fun updateRoutineConfiguration(
        userId: String,
        activitySequence: List<RoutineActivityEntry>,
        totalDurationMinutes: Int
    ): RoutineConfiguration {
        // Reuse save — always creates a fresh row and deactivates old ones
        return saveRoutineConfiguration(userId, activitySequence, totalDurationMinutes)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private suspend fun deactivateExistingConfigs(userId: String) {
        client
            .from("routine_configurations")
            .update(mapOf("is_active" to false)) {
                filter {
                    eq("user_id", userId)
                    eq("is_active", true)
                }
            }
    }

    /**
     * Serializes a List<RoutineActivityEntry> into the JSONB format expected
     * by the activity_sequence column:
     *   [{"activity_id": "uuid", "order": 1}, ...]
     *
     * Returns a JsonArray that PostgREST will store as native JSONB.
     */
    private fun buildSequenceJson(entries: List<RoutineActivityEntry>): JsonArray {
        val jsonString = Json.encodeToString(entries)
        return Json.parseToJsonElement(jsonString).jsonArray
    }
}