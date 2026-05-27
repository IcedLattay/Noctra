package com.noctra.app.utils

import android.content.Context
import java.util.UUID

object UserSession {
    private const val PREF_NAME = "noctra_prefs"
    private const val KEY_USER_ID = "anonymous_user_id"

    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newId).apply()
            newId
        }
    }
}