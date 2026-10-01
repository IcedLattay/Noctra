package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.ReconciliationAuditUseCase
import com.noctra.app.utils.UserSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Backfills routine history older than the open-path audit's 14-day cap.
 *
 * Coverage: [first eligible night, open-audit cap start). The open path
 * owns everything newer; this worker owns everything older — no overlap.
 * Bounded (30 days/run, oldest first) with a persisted cursor, so gaps of
 * any size converge without blocking app opens. Idempotent: reuses the
 * shared auditDate() verdict/write logic (check-then-insert inside
 * recordMissedSession), and the cursor only advances past completed dates.
 */
class AuditBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sessionRepository = RoutineSessionRepository()
    private val rewardRepository = RewardLedgerRepository()
    private val profileRepository = UserProfileRepository()
    private val auditUseCase = ReconciliationAuditUseCase()

    companion object {
        private const val PREFS = "noctra_backfill"
        private const val CHUNK_DAYS = 30L

        fun cursorKey(userId: String) = "cursor_$userId"
    }

    override suspend fun doWork(): Result {
        val userId = UserSession.getUserId(applicationContext) ?: return Result.success()

        return try {
            val profile = profileRepository.getOrCreateProfile(userId)
            val completedAt = profile.onboardingCompletedAt ?: return Result.success()

            // Lower bound: first eligible night (onboarding day if it beat
            // its window, else the next day). No stamp → nothing to bound
            // (legacy accounts keep current behavior: worker no-ops).
            val onboardDate = try {
                java.time.OffsetDateTime.parse(completedAt).toLocalDate()
            } catch (e: Exception) {
                try {
                    LocalDate.parse(completedAt.take(10))
                } catch (e2: Exception) {
                    return Result.success()
                }
            }
            // Lower bound is onboarding day itself: every night from
            // onboarding onward counts, no exemptions.
            val firstEligible = onboardDate

            // Upper bound (exclusive): where the open-path audit takes over.
            val capStart = LocalDate.now().minusDays(14)
            if (!firstEligible.isBefore(capStart)) return Result.success()

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cursor = prefs.getString(cursorKey(userId), firstEligible.toString())
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.coerceAtLeast(firstEligible)
                ?: firstEligible
            if (!cursor.isBefore(capStart)) {
                prefs.edit().remove(cursorKey(userId)).apply()
                return Result.success()
            }

            val endExclusive = minOf(cursor.plusDays(CHUNK_DAYS), capStart)
            var ledger = rewardRepository.getRewardLedger(userId) ?: return Result.success()

            var date = cursor
            while (date.isBefore(endExclusive)) {
                ledger = auditUseCase.auditDate(
                    userId = userId,
                    date = date,
                    currentLedger = ledger,
                    syncSleep = false
                )
                date = date.plusDays(1)
            }

            rewardRepository.updateRewardLedger(
                ledger.copy(lastUpdated = java.time.OffsetDateTime.now().toString())
            )
            if (endExclusive >= capStart) {
                prefs.edit().remove(cursorKey(userId)).apply()
            } else {
                prefs.edit().putString(cursorKey(userId), endExclusive.toString()).apply()
            }
            Result.success()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("AuditBackfill", "Backfill failed", e)
            Result.failure()
        }
    }
}
