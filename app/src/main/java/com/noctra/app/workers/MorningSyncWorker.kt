package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.SleepSyncManager
import com.noctra.app.utils.UserSession
import java.time.LocalDate

/**
 * Provisional sync pass (~9:00 AM daily).
 *
 * Syncs last night's sleep (yesterday's session date) from Health Connect so the
 * morning recap popup has data to show. The wake-up anchor window (today 4:00 AM -
 * 4:00 PM) is still open at this point, so the record is written with
 * is_partial_data = true; the SleepFinalizationWorker overwrites it after 4:00 PM.
 *
 * All aggregation/scoring/upsert logic lives in the shared SleepSyncManager.
 */
class MorningSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sleepSyncManager = SleepSyncManager()

    override suspend fun doWork(): Result {
        val userId = UserSession.getUserId(applicationContext) ?: return Result.success()

        // Last night's sleep belongs to yesterday's session date
        val sessionDate = LocalDate.now().minusDays(1)

        return when (val result = sleepSyncManager.syncSessionDate(userId, sessionDate)) {
            is SleepSyncManager.SyncResult.Synced -> Result.success()
            is SleepSyncManager.SyncResult.NoData -> Result.success()
            is SleepSyncManager.SyncResult.PermissionDenied -> Result.success()
            is SleepSyncManager.SyncResult.HealthConnectUnavailable -> Result.success()
            is SleepSyncManager.SyncResult.Failed -> Result.retry()
        }
    }
}
