package com.noctra.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NoctraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WIND_DOWN,
                "Wind-Down Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nightly reminders to start your wind-down routine."
            }
        )

        // Reserved for morning sleep-score notification (Module 3 territory — safe to create now)
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