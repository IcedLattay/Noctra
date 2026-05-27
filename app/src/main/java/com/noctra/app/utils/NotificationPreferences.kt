package com.noctra.app.utils

import android.content.Context

object NotificationPreferences {
    private const val PREFS_NAME = "noctra_notification_prefs"
    private const val KEY_WIND_DOWN = "wind_down_reminders_enabled"
    private const val KEY_MORNING_SCORE = "morning_sleep_score_enabled"

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
}