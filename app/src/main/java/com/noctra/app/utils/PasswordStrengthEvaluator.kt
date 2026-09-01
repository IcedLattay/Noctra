package com.noctra.app.utils

enum class PasswordStrength {
    EMPTY, WEAK, FAIR, STRONG
}

class PasswordStrengthEvaluator {
    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY

        // SDD: Enforces a minimum of eight characters
        if (password.length < 8) return PasswordStrength.WEAK
        
        var score = 0
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.length >= 12) score++

        return when {
            score >= 2 -> PasswordStrength.STRONG
            score >= 1 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}
