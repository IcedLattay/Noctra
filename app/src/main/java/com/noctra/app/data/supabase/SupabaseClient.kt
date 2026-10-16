package com.noctra.app.data.supabase

import android.content.Context
import com.noctra.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.gotrue.SettingsCodeVerifierCache
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

                // Scanner-proof password recovery: emailed links carry a
                // one-time ?code= instead of raw tokens, and redeeming it
                // requires the verifier stored on the device that requested
                // the reset. Mail scanners/previews that tap the link can no
                // longer consume it (observed in production: implicit links
                // were redeemed upstream, leaving the user an expired link).
                flowType = FlowType.PKCE
                // Persist the verifier (SharedPreferences), not just memory —
                // the recovery flow spans two apps (Noctra -> mail -> back),
                // and a process death in between must not kill the link.
                codeVerifierCache = SettingsCodeVerifierCache()
            }
        }
    }
}
