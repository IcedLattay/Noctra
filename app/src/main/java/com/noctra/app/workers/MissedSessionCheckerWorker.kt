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
        var ledger = rewardRepository.getRewardLedger(userId) ?: return Result.failure()

        // Walk every open night after the ledger anchor (capped at 14
        // days) instead of judging "yesterday" alone: a single skipped
        // run used to leave that night permanently unjudged, and a later
        // penalty's anchor-hop could seal the gap (anchor jumps forward,
        // the audit starts at anchor+1 and never looks back). Dates are
        // walked in order and the anchor is saved after each miss, so it
        // advances night-by-night and can never step over an empty one.
        val anchor = ledger.lastSessionDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        for (date in RoutineWindowProvider.missJudgementDates(anchor)) {
            // Defensive: never judge while the night's 4 AM attribution
            // anchor is still ahead (WorkManager can deliver across
            // timezone changes/reboots) — a post-midnight start can
            // still be dated to it, and judging now would plant a
            // phantom MISSED row the real session then duplicates.
            if (!RoutineWindowProvider.isSessionDateClosed(date)) continue

            // Any existing row means this night is not ours to judge:
            // completed/missed are final, and pending rows belong to the
            // audit (sleep-verify / age-grace flip them).
            if (sessionRepository.getSessionsByDate(userId, date.toString()).isNotEmpty()) continue

            // Missed session! Record it in the DB so it shows up in Analytics
            sessionRepository.recordMissedSession(userId, date.toString())

            // Fold the verdict through the SAME penalty chain the audit
            // uses (shared applyMissPenalty): first miss = warning with the
            // anchor hopping onto the missed night, second consecutive =
            // reset. Saved immediately per date (audit's per-date
            // persistence rationale): a crash between insert and save
            // would otherwise lose that night's penalty forever, since
            // the row it belongs to already exists.
            ledger = RewardCalculationUseCase.applyMissPenalty(ledger, date)
            rewardRepository.updateRewardLedger(ledger.copy(
                lastUpdated = OffsetDateTime.now().toString()
            ))
        }

        return Result.success()
    }
}
