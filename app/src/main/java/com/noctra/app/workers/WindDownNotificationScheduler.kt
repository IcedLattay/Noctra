package com.noctra.app.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.receivers.WindDownNotificationReceiver
import com.noctra.app.utils.UserSession
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * WindDownNotificationScheduler
 *
 * Computes when the user's routine window opens
 * (target_bedtime − total_routine_duration_minutes) and schedules an
 * AlarmManager intent for WindDownNotificationReceiver to fire at that time.
 *
 * Call sites:
 *   - After onboarding completion (first time bedtime + routine are saved)
 *   - After bedtime change in Settings
 *   - After routine edit (total duration may have changed)
 *   - From MainActivity.onCreate() as a safety net
 *   - From MissedSessionCheckerWorker (Morning Refresh logic)
 *
 * Idempotent: AlarmManager with the same PendingIntent replaces the previous alarm.
 */
object WindDownNotificationScheduler {

    suspend fun scheduleNext(context: Context) {
        // Only schedule if the user actually wants notifications
        if (!com.noctra.app.utils.NotificationPreferences.isWindDownEnabled(context)) {
            cancel(context)
            return
        }

        val userId = UserSession.getUserId(context) ?: return

        val profile = runCatching {
            UserProfileRepository().getOrCreateProfile(userId)
        }.getOrNull() ?: return

        val bedtimeString = profile.targetBedtime ?: return

        val routine = runCatching {
            RoutineRepository().getActiveRoutine(userId)
        }.getOrNull() ?: return

        val triggerAt = computeNextTrigger(
            bedtimeString = bedtimeString,
            routineDurationMinutes = routine.totalDurationMinutes
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WindDownNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Fallback to non-exact alarm to avoid SecurityException
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WindDownNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Computes the next LocalDateTime at which the routine window opens.
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
