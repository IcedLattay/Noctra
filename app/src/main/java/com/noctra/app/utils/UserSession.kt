package com.noctra.app.utils

import android.content.Context
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

object UserSession {
    fun getUserId(context: Context): String? {
        // Return Supabase Auth User ID if logged in, otherwise null
        return SupabaseClient.client.auth.currentUserOrNull()?.id
    }
}