package com.noctra.app.utils

import android.content.Context
import android.util.Log
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class DemoDataCleaner {
    suspend fun clearAllDataForCurrentUser(context: Context) {
        val userId = UserSession.getUserId(context)

        try {
            // Wipe routine sessions for this user
            SupabaseClient.client.postgrest["routine_sessions"]
                .delete {
                    filter { eq("user_id", userId) }
                }

            // Wipe sleep records for this user
            SupabaseClient.client.postgrest["sleep_records"]
                .delete {
                    filter { eq("user_id", userId) }
                }

            Log.d("DemoDataSeeder", "Cleared all routine sessions and sleep records for user $userId")
        } catch (e: Exception) {
            Log.e("DemoDataSeeder", "Failed to clear data", e)
            throw e
        }
    }
}