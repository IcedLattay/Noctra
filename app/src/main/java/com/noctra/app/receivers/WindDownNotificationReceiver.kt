package com.noctra.app.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import com.noctra.app.NoctraApplication
import com.noctra.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * WindDownNotificationReceiver
 *
 * This is triggered by AlarmManager even if the app is closed or the phone is in Doze mode.
 * Its only job is to build and display the wind-down notification.
 */
class WindDownNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // All async work shares this single goAsync window: schedule the
        // next notification, then gate THIS one on the routine window.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Stamp this app-day as popped BEFORE scheduling: the guard
                // in scheduleNext then rolls any same-day recompute forward.
                com.noctra.app.utils.NotificationPreferences.setLastWindDownFireDay(
                    context,
                    com.noctra.app.utils.NotificationPreferences.appDayFor()
                )
                com.noctra.app.workers.WindDownNotificationScheduler.scheduleNext(context)

                // Check if the user has disabled these notifications in Settings
                if (!com.noctra.app.utils.NotificationPreferences.isWindDownEnabled(context)) {
                    return@launch
                }

                // Window guard: an alarm can fire late (Doze, exact-alarm
                // delays) — posting outside the routine window would only
                // teach users that tapping the notification bounces them.
                // Debug force-flag keeps the manual fire button working.
                if (!isRoutineWindowOpen(context)) {
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.e("WindDownNotif", "Receiver failed; next-fire chain already stamped", e)
                return@launch
            } finally {
                pendingResult.finish()
            }

            val pendingIntent: PendingIntent = NavDeepLinkBuilder(context)
                .setGraph(R.navigation.nav_graph)
                .setDestination(R.id.routineStartFragment)
                .createPendingIntent()

            val notification = NotificationCompat.Builder(
                context,
                NoctraApplication.CHANNEL_WIND_DOWN
            )
                .setSmallIcon(R.drawable.ic_summary_moon)
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.noctra_purple))
                .setContentTitle("Time to wind down 🌙")
                .setContentText("Your routine starts now. Tap to begin.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission not granted on API 33+
            }
        }
    }

    /**
     * Same window math as the start screen (profile bedtime, active routine
     * duration), read fresh — the receiver can't reuse the ViewModel.
     */
    private suspend fun isRoutineWindowOpen(context: Context): Boolean {
        if (com.noctra.app.utils.DebugSettings.forceRoutineWindow.value) return true

        val userId = com.noctra.app.utils.UserSession.getUserIdAsync(context) ?: return false
        val profile = com.noctra.app.data.repository.UserProfileRepository()
            .getOrCreateProfile(userId)
        val routine = com.noctra.app.data.repository.RoutineRepository()
            .getActiveRoutine(userId) ?: return false

        return com.noctra.app.utils.RoutineWindowProvider.isTimeInWindow(
            now = java.time.LocalTime.now(),
            targetBedtime = profile.targetBedtime ?: "22:00:00",
            routineDurationMinutes = routine.totalDurationMinutes
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
