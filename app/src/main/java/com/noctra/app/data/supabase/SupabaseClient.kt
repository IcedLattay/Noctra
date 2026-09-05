package com.noctra.app.data.supabase

import android.content.Context
import com.noctra.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClient {
    lateinit var client: io.github.jan.supabase.SupabaseClient

    fun init(context: Context) {
        if (::client.isInitialized) return

        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(Json {
                encodeDefaults = true
                explicitNulls = true
                ignoreUnknownKeys = true
            })
            install(Postgrest)
            install(Realtime)
            install(Auth) {
                // Use the default session manager - it's the most compatible in v2.4.0
                // It automatically handles SharedPreferences for you.
                autoLoadFromStorage = true
                autoSaveToStorage = true
                sessionManager = SettingsSessionManager()
                
                scheme = "noctra"
                host = "reset-password"
            }
        }
    }
}
