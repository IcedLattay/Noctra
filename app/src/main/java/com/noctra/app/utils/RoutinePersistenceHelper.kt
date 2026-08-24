package com.noctra.app.data.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * RoutinePersistenceHelper
 *
 * Lightweight local cache (SharedPreferences) for routine resume state.
 * This is separate from RoutineSession in Supabase — that's the source of
 * truth for completed/abandoned sessions, while this is purely local,
 * fast-access state used to answer "was the user mid-routine, and where,
 * the last time the app was open?" without a network round-trip.
 *
 * Values are cleared via clear() once a session is completed, abandoned
 * (Safety Net expiry), or cleaned up by the Morning After check.
 *
 * Usage: call RoutinePersistenceHelper.init(context) once, e.g. in
 * NoctraApplication.onCreate(), before any get/set calls.
 */
object RoutinePersistenceHelper {

    private const val PREFS_NAME = "routine_persistence_prefs"

    private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    private const val KEY_CURRENT_STEP_INDEX = "current_step_index"
    private const val KEY_LAST_ACTIVITY_TIMESTAMP = "last_activity_timestamp"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ─── Active Session ID ────────────────────────────────────────────────

    fun setActiveSessionId(sessionId: String?) {
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
    }

    fun getActiveSessionId(): String? {
        return prefs.getString(KEY_ACTIVE_SESSION_ID, null)
    }

    // ─── Current Step Index ───────────────────────────────────────────────

    fun setCurrentStepIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_STEP_INDEX, index).apply()
    }

    fun getCurrentStepIndex(): Int {
        return prefs.getInt(KEY_CURRENT_STEP_INDEX, 0)
    }

    // ─── Last Activity Timestamp ──────────────────────────────────────────

    fun setLastActivityTimestamp(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_ACTIVITY_TIMESTAMP, timestampMillis).apply()
    }

    fun getLastActivityTimestamp(): Long {
        // 0L default means "no timestamp recorded" — callers should treat
        // this as "no resumable session" rather than a real elapsed-time gap.
        return prefs.getLong(KEY_LAST_ACTIVITY_TIMESTAMP, 0L)
    }

    // ─── Convenience ──────────────────────────────────────────────────────

    /**
     * True if there's a session ID cached locally — i.e. a routine was
     * started and not yet cleared. Does NOT check timing/expiry; callers
     * (e.g. checkRecoveryState()) are responsible for the gap-based logic.
     */
    fun hasActiveSession(): Boolean {
        return getActiveSessionId() != null
    }

    /**
     * Clears all cached resume state. Call this once a session is
     * completed, marked abandoned, or cleaned up by the Morning After check.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACTIVE_SESSION_ID)
            .remove(KEY_CURRENT_STEP_INDEX)
            .remove(KEY_LAST_ACTIVITY_TIMESTAMP)
            .apply()
    }
}