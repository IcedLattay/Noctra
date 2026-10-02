package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Backfills routine history older than the open-path audit's 14-day cap.
 *
 * Row-writing only: pendings flip straight to MISSED (no grace re-check —
 * long expired), empty past dates get MISSED rows. No verdicts, no ledger
 * folds; streaks belong to the open path + big-gap rule. Coverage:
 * [onboarding day, open-audit cap) in ≤30-day chunks with a persisted
 * per-user cursor. Idempotent throughout.
 */
class AuditBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sessionRepository = RoutineSessionRepository()
    private val profileRepository = UserProfileRepository()

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
                return Result.success()
            }

            val endExclusive = minOf(cursor.plusDays(CHUNK_DAYS), capStart)
            android.util.Log.d(
                "AuditBackfill",
                "run cursor=$cursor end=$endExclusive cap=$capStart"
            )

            // Row-writing only: pendings flip straight to MISSED (no grace
            // re-check — grace expired weeks ago by construction), empty
            // past dates get MISSED rows. No verdicts, no ledger folds;
            // streaks are owned exclusively by the open path + big-gap rule.
            var date = cursor
            while (date.isBefore(endExclusive)) {
                val dateString = date.toString()
                val session = sessionRepository.getSessionsByDate(userId, dateString).firstOrNull()
                if (session != null && session.status == "PENDING") {
                    sessionRepository.insertSession(session.copy(status = "MISSED"))
                } else if (session == null && date.isBefore(LocalDate.now())) {
                    sessionRepository.recordMissedSession(userId, dateString)
                }
                date = date.plusDays(1)
            }
            // Always persist the cursor (even on catch-up): deleting it
            // would restart every future run from onboarding day and
            // re-sweep ancient history daily. The cursor doubles as the
            // "caught up through" marker — tomorrow's cap moves one day
            // forward and exactly one new date falls due.
            prefs.edit().putString(cursorKey(userId), endExclusive.toString()).apply()
            Result.success()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("AuditBackfill", "Backfill failed", e)
            Result.failure()
        }
    }
}
