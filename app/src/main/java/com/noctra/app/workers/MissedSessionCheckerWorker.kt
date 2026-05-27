package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.utils.UserSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MissedSessionCheckerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sessionRepository = RoutineSessionRepository()
    private val rewardRepository = RewardLedgerRepository()

    override suspend fun doWork(): Result {
        val userId = UserSession.getUserId(applicationContext)
        val ledger = rewardRepository.getRewardLedger(userId) ?: return Result.failure()

        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sessions = sessionRepository.getSessionsByDate(userId, yesterday)

        if (sessions.isEmpty()) {
            // Missed session!
            var newXp = ledger.totalXp
            var newCurrentStreak = 0
            
            if (ledger.devolutionPending) {
                // Second consecutive miss -> Devolve
                newXp = calculateDevolvedXp(ledger.totalXp)
            }

            val updatedLedger = ledger.copy(
                currentStreak = newCurrentStreak,
                devolutionPending = true, // Mark for potential devolution next time
                totalXp = newXp
            )
            rewardRepository.updateRewardLedger(updatedLedger)
        }

        return Result.success()
    }

    private fun calculateDevolvedXp(currentXp: Int): Int {
        return when {
            currentXp >= 50000 -> 15000 // To Stage 4
            currentXp >= 15000 -> 5000  // To Stage 3
            currentXp >= 5000 -> 1500   // To Stage 2
            currentXp >= 1500 -> 0      // To Stage 1
            else -> 0
        }
    }
}
