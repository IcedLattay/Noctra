package com.noctra.app.utils

import android.content.Context

object NotificationPreferences {
    private const val PREFS_NAME = "noctra_notification_prefs"
    private const val KEY_WIND_DOWN = "wind_down_reminders_enabled"
    private const val KEY_MORNING_SCORE = "morning_sleep_score_enabled"
    private const val KEY_CACHED_BEDTIME = "cached_target_bedtime"
    private const val KEY_CACHED_DURATION = "cached_routine_duration"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isWindDownEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIND_DOWN, true)  // default ON

    fun setWindDownEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIND_DOWN, enabled).apply()
    }

    fun isMorningScoreEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MORNING_SCORE, true)  // default ON

    fun setMorningScoreEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MORNING_SCORE, enabled).apply()
    }

    // ─── Local Caching for Alarm Scheduling ──────────────────────────────────

    fun getCachedBedtime(context: Context): String? =
        prefs(context).getString(KEY_CACHED_BEDTIME, null)

    fun getCachedDuration(context: Context): Int =
        prefs(context).getInt(KEY_CACHED_DURATION, 0)

    /**
     * Updates the local "sticky note" with the latest settings.
     * Pass null for values that haven't changed.
     */
    fun updateCachedSettings(context: Context, bedtime: String? = null, durationMinutes: Int? = null) {
        val editor = prefs(context).edit()
        bedtime?.let { editor.putString(KEY_CACHED_BEDTIME, it) }
        durationMinutes?.let { editor.putInt(KEY_CACHED_DURATION, it) }
        editor.apply()
    }
}