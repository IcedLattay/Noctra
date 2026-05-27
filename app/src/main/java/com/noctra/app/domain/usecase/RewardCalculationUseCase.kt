package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import kotlin.math.max

class RewardCalculationUseCase(
    private val rewardRepository: RewardLedgerRepository,
    private val routineSessionRepository: RoutineSessionRepository
) {
    suspend fun execute(
        userId: String,
        sessionId: String,
        completedOnTime: Boolean
    ): RewardResult {
        val ledger = rewardRepository.getRewardLedger(userId) ?: throw IllegalStateException("Reward ledger not found")
        
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        
        val lastSessionDate = ledger.lastSessionDate?.let { LocalDate.parse(it, dateFormatter) }
        
        val newStreak = when {
            lastSessionDate == null -> 1
            lastSessionDate == yesterday -> ledger.currentStreak + 1
            lastSessionDate == today -> ledger.currentStreak // Already incremented today? 
            else -> 1
        }
        
        val multiplier = when {
            newStreak >= 14 -> 2.0
            newStreak >= 7 -> 1.5
            newStreak >= 3 -> 1.25
            else -> 1.0
        }
        
        val baseTokens = 50
        val earnedTokens = (baseTokens * multiplier).toInt()
        val earnedXp = 100
        
        val updatedLedger = ledger.copy(
            tokenBalance = ledger.tokenBalance + earnedTokens,
            totalXp = ledger.totalXp + earnedXp,
            currentStreak = newStreak,
            longestStreak = max(ledger.longestStreak, newStreak),
            lastSessionDate = today.format(dateFormatter),
            lastUpdated = OffsetDateTime.now().toString(),
            devolutionPending = false
        )
        
        rewardRepository.updateRewardLedger(updatedLedger)
        
        return RewardResult(
            dreamTokensEarned = earnedTokens,
            xpEarned = earnedXp,
            newStreak = newStreak,
            streakMultiplier = multiplier
        )
    }
}

data class RewardResult(
    val dreamTokensEarned: Int,
    val xpEarned: Int,
    val newStreak: Int,
    val streakMultiplier: Double
)
