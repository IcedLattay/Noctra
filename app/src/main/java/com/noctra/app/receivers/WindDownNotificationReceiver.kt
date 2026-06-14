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

/**
 * WindDownNotificationReceiver
 *
 * This is triggered by AlarmManager even if the app is closed or the phone is in Doze mode.
 * Its only job is to build and display the wind-down notification.
 */
class WindDownNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Check if the user has disabled these notifications in Settings
        if (!com.noctra.app.utils.NotificationPreferences.isWindDownEnabled(context)) {
            return
        }

        val pendingIntent: PendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.routineStartFragment)
            .createPendingIntent()

        val notification = NotificationCompat.Builder(
            context,
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
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission not granted on API 33+
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
