package com.noctra.app.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * WindDownNotificationScheduler
 *
 * Computes when the user's routine window opens
 * (target_bedtime − total_routine_duration_minutes) and enqueues a
 * OneTimeWorkRequest for WindDownNotificationWorker to fire at that time.
 *
 * Call sites:
 *   - After onboarding completion (first time bedtime + routine are saved)
 *   - After bedtime change in Settings
 *   - After routine edit (total duration may have changed)
 *   - From MainActivity.onCreate() as a safety net (work canceled by OS, fresh install, etc.)
 *   - From WindDownNotificationWorker.doWork() to chain the next day
 *
 * Idempotent: ExistingWorkPolicy.REPLACE means multiple calls collapse to
 * the most recent computation. Safe to call liberally.
 */
object WindDownNotificationScheduler {

    private const val UNIQUE_WORK_NAME = "noctra_wind_down_notification"

    fun scheduleNext(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val userId = UserSession.getUserId(context) ?: return@launch

            val profile = runCatching {
                UserProfileRepository().getOrCreateProfile(userId)
            }.getOrNull() ?: return@launch

            val bedtimeString = profile.targetBedtime ?: return@launch

            val routine = runCatching {
                RoutineRepository().getActiveRoutine(userId)
            }.getOrNull() ?: return@launch

            val triggerAt = computeNextTrigger(
                bedtimeString = bedtimeString,
                routineDurationMinutes = routine.totalDurationMinutes
            )
            val initialDelayMs = Duration.between(LocalDateTime.now(), triggerAt).toMillis()
            if (initialDelayMs <= 0) return@launch  // defensive

            val request = OneTimeWorkRequestBuilder<WindDownNotificationWorker>()
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Computes the next LocalDateTime at which the routine window opens.
     *
     * Algorithm:
     *   1. windowOpen = bedtime − routineDurationMinutes  (LocalTime math)
     *   2. candidate = today at windowOpen
     *   3. if candidate is in the past, add 1 day
     *
     * This correctly handles bedtimes after midnight (e.g., 1 AM target with
     * 90-min routine → 11:30 PM window open) because we never wrap dates
     * during the LocalTime arithmetic; the "is it past now?" check resolves it.
     */
    internal fun computeNextTrigger(
        bedtimeString: String,
        routineDurationMinutes: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val bedtime = LocalTime.parse(normalizeBedtime(bedtimeString))
        val windowOpenTime = bedtime.minusMinutes(routineDurationMinutes.toLong())
        val candidate = now.toLocalDate().atTime(windowOpenTime)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    /** Supabase may return "HH:mm" or "HH:mm:ss" — LocalTime parses both, but trim noise. */
    private fun normalizeBedtime(s: String): String {
        val parts = s.split(":")
        return if (parts.size >= 3) "${parts[0]}:${parts[1]}:${parts[2].take(2)}" else s
    }
}