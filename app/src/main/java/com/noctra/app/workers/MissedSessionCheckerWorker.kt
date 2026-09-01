package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.utils.UserSession
import com.noctra.app.workers.WindDownNotificationScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

class MissedSessionCheckerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sessionRepository = RoutineSessionRepository()
    private val rewardRepository = RewardLedgerRepository()

    override suspend fun doWork(): Result {
        // 1. Refresh the notification schedule for today (Self-healing logic)
        WindDownNotificationScheduler.scheduleNext(applicationContext)

        val userId = UserSession.getUserId(applicationContext) ?: return Result.success() // Silent exit if not logged in
        val ledger = rewardRepository.getRewardLedger(userId) ?: return Result.failure()

        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sessions = sessionRepository.getSessionsByDate(userId, yesterday)

        if (sessions.isEmpty()) {
            // Missed session! Record it in the DB so it shows up in Analytics
            sessionRepository.recordMissedSession(userId, yesterday)

            var newCurrentStreak = ledger.currentStreak
            
            if (ledger.hasFirstMiss) {
                // Second consecutive miss -> Reset streak
                // Note: User decided no XP loss for routine miss
                newCurrentStreak = 0
            }

            val updatedLedger = ledger.copy(
                currentStreak = newCurrentStreak,
                hasFirstMiss = true, // Set/maintain warning
                lastUpdated = OffsetDateTime.now().toString()
            )
            rewardRepository.updateRewardLedger(updatedLedger)
        }

        return Result.success()
    }
}
