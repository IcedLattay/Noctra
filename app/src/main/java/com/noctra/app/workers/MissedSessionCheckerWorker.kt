package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.domain.usecase.RewardCalculationUseCase
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.utils.RoutineWindowProvider
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

        val yesterdayDate = LocalDate.now().minusDays(1)

        // Defensive: never judge while the previous night's 4 AM
        // attribution anchor is still ahead (WorkManager can deliver
        // across timezone changes/reboots) — a post-midnight start can
        // still be dated to yesterday, and judging now would plant a
        // phantom MISSED row the real session then duplicates.
        if (!RoutineWindowProvider.isSessionDateClosed(yesterdayDate)) {
            return Result.success()
        }

        val yesterday = yesterdayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sessions = sessionRepository.getSessionsByDate(userId, yesterday)

        if (sessions.isEmpty()) {
            // Missed session! Record it in the DB so it shows up in Analytics
            sessionRepository.recordMissedSession(userId, yesterday)

            // Fold the verdict through the SAME penalty chain the audit
            // uses (shared applyMissPenalty): first miss = warning with the
            // anchor hopping onto the missed night, second consecutive =
            // reset. This checker's old private copy never moved the
            // anchor, so a warning miss broke the streak anyway via the
            // gap rule — identical behavior, two outcomes depending on
            // which judger reached the night first.
            val updatedLedger = RewardCalculationUseCase.applyMissPenalty(ledger, yesterdayDate)
            rewardRepository.updateRewardLedger(updatedLedger.copy(
                lastUpdated = OffsetDateTime.now().toString()
            ))
        }

        return Result.success()
    }
}
