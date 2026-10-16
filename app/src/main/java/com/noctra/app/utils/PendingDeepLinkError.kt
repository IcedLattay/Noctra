package com.noctra.app.utils

/**
 * One-shot handoff from MainActivity's deep-link handler to the screen the
 * link opened (ResetPasswordFragment). Set when a recovery link arrives
 * without a usable token — consumed by an upstream click/scanner, expired,
 * or stripped in transit — so the destination screen can explain what
 * happened in its own inline error UI instead of letting the user type a
 * password into a session that doesn't exist.
 */
object PendingDeepLinkError {

    @Volatile
    var message: String? = null

    /** Read-and-clear: each pending error is shown exactly once. */
    fun consume(): String? {
        val value = message
        message = null
        return value
    }
}
