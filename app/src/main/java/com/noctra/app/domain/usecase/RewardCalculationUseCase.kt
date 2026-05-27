package com.noctra.app.domain.usecase

/**
 * RewardCalculationUseCase
 *
 * Computes Dream Tokens and Growth Points (XP) earned for a completed
 * nightly routine session.
 *
 * Rules (from Noctra context doc):
 *   - Dream Tokens  = BASE_TOKENS × streak multiplier tier  (spendable currency)
 *   - Growth Points = BASE_XP flat per session              (non-spendable, drives evolution)
 *
 * Streak Multiplier Tiers:
 *   0–2 nights   → ×1.0
 *   3–6 nights   → ×1.25
 *   7–13 nights  → ×1.5
 *   14+ nights   → ×2.0
 *
 * Usage:
 *   val result = RewardCalculationUseCase().calculate(currentStreak = 7)
 *   result.tokensEarned     // Dream Tokens to award
 *   result.xpEarned         // Growth Points to award
 *   result.multiplierApplied // The multiplier used (for display on completion overlay)
 */
class RewardCalculationUseCase {

    // ─── Reward Constants ────────────────────────────────────────────────────
    // TODO: Confirm these base values with the team before final demo.
    //       Adjust to balance economy (token spending vs earning rate in Sleep Shop).

    companion object {
        /** Base Dream Tokens earned per completed session before multiplier. */
        const val BASE_TOKENS = 10

        /** Flat Growth Points (XP) earned per completed session. Streak has no effect. */
        const val BASE_XP = 50
    }

    // ─── Result Data Class ───────────────────────────────────────────────────

    /**
     * The output of a reward calculation.
     *
     * @param tokensEarned      Final Dream Tokens after multiplier applied.
     * @param xpEarned          Flat XP regardless of streak.
     * @param multiplierApplied The multiplier tier used (e.g. 1.5) — shown on completion overlay.
     * @param streakTier        Human-readable tier label for UI display (e.g. "×1.5 Streak Bonus").
     */
    data class RewardResult(
        val tokensEarned: Int,
        val xpEarned: Int,
        val multiplierApplied: Double,
        val streakTier: String
    )

    // ─── Main Calculation ────────────────────────────────────────────────────

    /**
     * Calculates rewards for a completed session.
     *
     * @param currentStreak The user's streak count BEFORE tonight is added.
     *                      e.g. if this is their 7th consecutive night, pass 6.
     *                      The new streak (currentStreak + 1) determines the tier.
     * @return              A [RewardResult] containing tokens, XP, and multiplier info.
     */
    fun calculate(currentStreak: Int): RewardResult {
        // New streak after tonight's completion
        val newStreak = currentStreak + 1

        val multiplier = getMultiplier(newStreak)
        val tierLabel  = getTierLabel(newStreak)

        val tokensEarned = (BASE_TOKENS * multiplier).toInt()
        val xpEarned     = BASE_XP  // always flat

        return RewardResult(
            tokensEarned     = tokensEarned,
            xpEarned         = xpEarned,
            multiplierApplied = multiplier,
            streakTier       = tierLabel
        )
    }

    // ─── Multiplier Logic ────────────────────────────────────────────────────

    /**
     * Returns the streak multiplier for the given streak count.
     *
     * Tiers (inclusive):
     *   0–2   → 1.0
     *   3–6   → 1.25
     *   7–13  → 1.5
     *   14+   → 2.0
     */
    fun getMultiplier(streak: Int): Double {
        return when {
            streak >= 14 -> 2.0
            streak >= 7  -> 1.5
            streak >= 3  -> 1.25
            else         -> 1.0
        }
    }

    /**
     * Returns a human-readable multiplier tier label for the completion overlay.
     * e.g. "×1.5 Streak Bonus!"
     */
    fun getTierLabel(streak: Int): String {
        return when {
            streak >= 14 -> "×2.0 Streak Bonus!"
            streak >= 7  -> "×1.5 Streak Bonus!"
            streak >= 3  -> "×1.25 Streak Bonus!"
            else         -> "×1.0"
        }
    }
}