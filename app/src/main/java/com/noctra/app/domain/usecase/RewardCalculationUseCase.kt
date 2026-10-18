package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.utils.RoutineWindowProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
        const val BASE_TOKENS = 100

        /** Flat Growth Points (XP) earned per completed session. */
        const val BASE_XP = 100

        /**
         * Chain arithmetic for one completion, anchored on the NIGHT's own
         * date: consecutive night → +1, same night re-run → unchanged,
         * genuine gap (or first ever) → restart at 1. Pinned by unit test —
         * the old wall-clock version reset streaks for every routine that
         * finished after midnight.
         *
         * [gapNightsAllPending] covers the second false-gap: pending nights
         * never touch the ledger (they await their grace verdict), so an
         * unfinished-but-in-flight night between the last credited one and
         * tonight looks like a break here. When EVERY uncredited night is
         * still pending, the chain is suspended, not broken.
         */
        internal fun computeNewStreak(
            currentStreak: Int,
            lastSessionDate: LocalDate?,
            nightDate: LocalDate,
            gapNightsAllPending: Boolean = false
        ): Int {
            val nightBefore = nightDate.minusDays(1)
            return when {
                lastSessionDate == null -> 1
                lastSessionDate == nightBefore -> currentStreak + 1
                lastSessionDate == nightDate -> currentStreak
                // Count tonight only — the audit credits or penalizes each
                // pending night when it resolves (transition-gated there,
                // so no double-count).
                gapNightsAllPending -> currentStreak + 1
                else -> 1
            }
        }
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
        // XP economy: routine pays no XP — mornings pay by sleep tier.
        val xpEarned     = 0

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

        // The night is identified by ITS session_date — never the wall clock.
        // Bedtime routines legitimately finish after midnight (session dates
        // stamp pre-4 AM completions to the previous day), and a clock-based
        // "yesterday" then sees a fake gap, resetting the streak to 1 instead
        // of chaining — and stamping last_session_date with the next day,
        // which also mis-anchors the audit range.
        val nightDate = resolveNightDate(sessionId)
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        val lastSessionDate = ledger.lastSessionDate?.let { LocalDate.parse(it, dateFormatter) }

        val newStreak = computeNewStreak(
            currentStreak = ledger.currentStreak,
            lastSessionDate = lastSessionDate,
            nightDate = nightDate,
            gapNightsAllPending = gapNightsAllPending(userId, lastSessionDate, nightDate)
        )

        val result = calculate(newStreak - 1)

        val updatedLedger = ledger.copy(
            tokenBalance = ledger.tokenBalance + result.tokensEarned,
            totalXp = ledger.totalXp + result.xpEarned,
            currentStreak = newStreak,
            longestStreak = max(ledger.longestStreak, newStreak),
            lastSessionDate = nightDate.format(dateFormatter),
            lastUpdated = OffsetDateTime.now().toString(),
            hasFirstMiss = false
        )

        repo.updateRewardLedger(updatedLedger)

        return result
    }

    /**
     * The calendar date this completion belongs to: the session row's own
     * session_date (authoritative), falling back to the same pre-4 AM
     * boundary used when the session was created (covers a missing or
     * degenerate session id).
     */
    private suspend fun resolveNightDate(sessionId: String): LocalDate {
        val fromRow = runCatching {
            routineSessionRepository?.getSessionById(sessionId)?.sessionDate
        }.getOrNull()
        if (fromRow != null) {
            runCatching { LocalDate.parse(fromRow) }.getOrNull()?.let { return it }
        }
        return RoutineWindowProvider.resolveRoutineSessionDate(LocalDateTime.now())
    }

    /**
     * True when every uncredited night between the last credited one and
     * tonight is still PENDING. Pending nights never touch the ledger — they
     * await their grace-period verdict — so they leave a false "gap" here:
     * the chain is suspended, not broken. A missed or rowless night in the
     * gap is a genuine break (the morning audit normally judges those and
     * advances last_session_date past them before the evening completion).
     */
    private suspend fun gapNightsAllPending(
        userId: String,
        lastSessionDate: LocalDate?,
        nightDate: LocalDate
    ): Boolean {
        if (lastSessionDate == null) return false
        val firstGap = lastSessionDate.plusDays(1)
        val lastGap = nightDate.minusDays(1)
        if (!firstGap.isBefore(lastGap)) return false // no nights in between
        val repo = routineSessionRepository ?: return false
        val gap = runCatching {
            repo.getSessionsInRange(userId, firstGap.toString(), lastGap.toString())
        }.getOrNull() ?: return false
        val expectedNights = ChronoUnit.DAYS.between(firstGap, lastGap).toInt() + 1
        return gap.size == expectedNights &&
            gap.all { it.status.equals("PENDING", ignoreCase = true) }
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
