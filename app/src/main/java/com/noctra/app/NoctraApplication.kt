package com.noctra.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.workers.MorningSyncWorker
import com.noctra.app.workers.MissedSessionCheckerWorker
import java.util.concurrent.TimeUnit

class NoctraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupMorningSyncWorker()
        setupMissedSessionWorker()
    }

    private fun setupMorningSyncWorker() {
        // ... (existing code)
    }

    private fun setupMissedSessionWorker() {
        val missedSessionRequest = PeriodicWorkRequestBuilder<MissedSessionCheckerWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(calculateMissedDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MissedSessionCheckerWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            missedSessionRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        return 0L 
    }

    private fun calculateMissedDelay(): Long {
        return 0L
    }
}
