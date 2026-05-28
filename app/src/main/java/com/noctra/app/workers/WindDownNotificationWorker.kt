package com.noctra.app.workers

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.NoctraApplication
import com.noctra.app.R

/**
 * WindDownNotificationWorker
 *
 * Fires a single "your routine starts now" push notification at the moment
 * the routine window opens (target_bedtime − total_routine_duration).
 *
 * Scheduling is owned by WindDownNotificationScheduler — this worker just
 * builds the notification and posts it. After firing, the worker re-enqueues
 * itself for tomorrow, forming a self-perpetuating one-shot chain. This
 * avoids PeriodicWorkRequest's 15-minute minimum and lets us hit the exact
 * bedtime each day.
 *
 * Deep link target: routineStartFragment — bypasses Routine Home per
 * context doc §2.1.
 */
class WindDownNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pendingIntent: PendingIntent = NavDeepLinkBuilder(applicationContext)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.routineStartFragment)
            .createPendingIntent()

        val notification = NotificationCompat.Builder(
            applicationContext,
            NoctraApplication.CHANNEL_WIND_DOWN
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to wind down 🌙")
            .setContentText("Your routine starts now. Tap to begin.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat
                .from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS not granted on API 33+ — silently no-op
            // and still chain tomorrow's request so it works once granted.
        }

        // Chain the next day's notification regardless of post success
        WindDownNotificationScheduler.scheduleNext(applicationContext)

        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}