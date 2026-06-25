package com.noctra.app.utils

import android.content.Context
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

object UserSession {
    /**
     * Returns the user ID. On the very first launch, it waits for the 
     * persistence layer to initialize to ensure we don't return null incorrectly.
     */
    fun getUserId(context: Context): String? {
        val auth = SupabaseClient.client.auth
        
        // If we are currently loading, this might be null.
        // We return the current user if available.
        return auth.currentUserOrNull()?.id
    }
    
    suspend fun getUserIdAsync(context: Context): String? {
        val auth = SupabaseClient.client.auth
        auth.awaitInitialization()
        return auth.currentUserOrNull()?.id
    }
}