package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import kotlin.math.max

/**
 * RewardCalculationUseCase
 *
 * Computes Dream Tokens and Growth Points (XP) earned for a completed
 * nightly routine session.
 */
class RewardCalculationUseCase(
    private val rewardRepository: RewardLedgerRepository? = null,
    private val routineSessionRepository: RoutineSessionRepository? = null
) {
    data class RewardResult(
        val tokensEarned: Int,
        val xpEarned: Int,
        val multiplierApplied: Double,
        val streakTier: String,
        val newStreak: Int
    )

    companion object {
        /** Base Dream Tokens earned per completed session before multiplier. */
        const val BASE_TOKENS = 50

        /** Flat Growth Points (XP) earned per completed session. */
        const val BASE_XP = 100
    }

    /**
     * Calculates rewards for a completed session based on streak count.
     *
     * @param currentStreak The user's streak count BEFORE tonight is added.
     * @return              A [RewardResult] containing tokens, XP, and multiplier info.
     */
    fun calculate(currentStreak: Int): RewardResult {
        // New streak after tonight's completion
        val newStreak = currentStreak + 1

        val multiplier = getMultiplier(newStreak)
        val tierLabel  = getTierLabel(newStreak)

        val tokensEarned = (BASE_TOKENS * multiplier).toInt()
        val xpEarned     = BASE_XP

        return RewardResult(
            tokensEarned     = tokensEarned,
            xpEarned         = xpEarned,
            multiplierApplied = multiplier,
            streakTier       = tierLabel,
            newStreak        = newStreak
        )
    }

    /**
     * Executes the reward calculation and updates the ledger in the repository.
     */
    suspend fun execute(
        userId: String,
        sessionId: String,
        completedOnTime: Boolean
    ): RewardResult {
        val repo = rewardRepository ?: throw IllegalStateException("RewardLedgerRepository required for execute()")
        val ledger = repo.getRewardLedger(userId) ?: throw IllegalStateException("Reward ledger not found")

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        val lastSessionDate = ledger.lastSessionDate?.let { LocalDate.parse(it, dateFormatter) }

        val newStreak = when {
            lastSessionDate == null -> 1
            lastSessionDate == yesterday -> ledger.currentStreak + 1
            lastSessionDate == today -> ledger.currentStreak
            else -> 1
        }

        val result = calculate(newStreak - 1)

        val updatedLedger = ledger.copy(
            tokenBalance = ledger.tokenBalance + result.tokensEarned,
            totalXp = ledger.totalXp + result.xpEarned,
            currentStreak = newStreak,
            longestStreak = max(ledger.longestStreak, newStreak),
            lastSessionDate = today.format(dateFormatter),
            lastUpdated = OffsetDateTime.now().toString(),
            devolutionPending = false
        )

        repo.updateRewardLedger(updatedLedger)

        return result
    }

    fun getMultiplier(streak: Int): Double {
        return when {
            streak >= 14 -> 2.0
            streak >= 7  -> 1.5
            streak >= 3  -> 1.25
            else         -> 1.0
        }
    }

    fun getTierLabel(streak: Int): String {
        return when {
            streak >= 14 -> "×2.0 Streak Bonus!"
            streak >= 7  -> "×1.5 Streak Bonus!"
            streak >= 3  -> "×1.25 Streak Bonus!"
            else         -> "×1.0"
        }
    }
}
