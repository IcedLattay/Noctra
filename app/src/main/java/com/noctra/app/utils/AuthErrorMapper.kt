package com.noctra.app.utils

import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * AuthErrorMapper
 *
 * Converts raw backend exceptions into safe, user-facing messages.
 *
 * WHY: supabase-kt exception messages can embed the full request URL —
 * including the `apikey=` query parameter — plus raw response bodies.
 * Surfacing e.message in toasts/snackbars leaks internals to users
 * (observed in production: apikey shown after a same-password reset).
 *
 * The raw error is ALWAYS logged to logcat under our own tag, so
 * debugging loses nothing; the UI only ever sees friendly copy.
 */
object AuthErrorMapper {

    private const val TAG = "NoctraApiError"

    /**
     * @param e        the caught backend exception
     * @param fallback generic copy for unmatched errors — must never
     *                 interpolate e.message
     */
    fun userMessage(e: Throwable, fallback: String): String {
        // Full detail stays in logcat — the only place raw errors belong.
        Log.e(TAG, "Backend error (${e::class.simpleName}): ${e.message}", e)

        if (e is SocketTimeoutException || e is IOException) {
            return "No connection — check your internet and try again."
        }

        // Match on supabase error codes ("error_code":"same_password") and
        // human-readable phrases (older GoTrue responses) alike.
        val raw = (e.message ?: "").lowercase()

        return when {
            "same_password" in raw ||
                "different from the old" in raw ||
                "different from the current" in raw ->
                "Your new password must be different from your current one."

            "weak_password" in raw ||
                "password should be at least" in raw ->
                "Password is too weak — use at least 8 characters."

            "user_already_exists" in raw ||
                "already registered" in raw ||
                "has already been registered" in raw ->
                "An account with this email already exists."

            "invalid_credentials" in raw ||
                "invalid login credentials" in raw ->
                "Incorrect email or password."

            "email_not_confirmed" in raw ||
                "email not confirmed" in raw ->
                "Please verify your email before signing in."

            "user_not_found" in raw || "no user found" in raw ->
                "No account found for this email."

            "rate limit" in raw || "rate_limit" in raw ->
                "Too many attempts — wait a minute and try again."

            ("otp" in raw || "token" in raw || "code" in raw) && "expired" in raw ->
                "That code has expired — request a new one."

            ("otp" in raw || "token" in raw || "code" in raw) && "invalid" in raw ->
                "That code is incorrect — check your inbox and try again."

            "validation" in raw ->
                "Please check your entries and try again."

            else -> fallback
        }
    }
}
