package com.noctra.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.workers.MissedSessionCheckerWorker
import com.noctra.app.workers.MorningSyncWorker
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class NoctraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupMorningSyncWorker()
        setupMissedSessionWorker()
        createNotificationChannels()
    }

    private fun setupMorningSyncWorker() {
        val syncRequest = PeriodicWorkRequestBuilder<MorningSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateDelayUntil(9, 0), TimeUnit.MILLISECONDS)
            .addTag("MorningSyncWorker")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MorningSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun setupMissedSessionWorker() {
        val missedSessionRequest = PeriodicWorkRequestBuilder<MissedSessionCheckerWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateDelayUntil(9, 30), TimeUnit.MILLISECONDS)
            .addTag("MissedSessionCheckerWorker")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MissedSessionCheckerWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            missedSessionRequest
        )
    }

    private fun calculateDelayUntil(hour: Int, minute: Int): Long {
        val now = LocalTime.now()
        val target = LocalTime.of(hour, minute)
        var delay = Duration.between(now, target).toMillis()
        if (delay < 0) {
            delay += Duration.ofDays(1).toMillis()
        }
        return delay
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        // Wind-down channel
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WIND_DOWN,
                "Wind-Down Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nightly reminders to start your wind-down routine."
            }
        )

        // Morning score channel
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MORNING,
                "Morning Sleep Score",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your sleep quality recap and XP earned overnight."
            }
        )
    }

    companion object {
        const val CHANNEL_WIND_DOWN = "wind_down_reminders"
        const val CHANNEL_MORNING = "morning_sleep_score"
    }
}