package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.repository.SleepSyncManager
import com.noctra.app.utils.UserSession
import java.time.LocalDate

/**
 * Finalization sync pass (~5:00 PM daily, after the 4:00 PM anchor window close).
 *
 * Re-syncs yesterday's session date now that its wake-up anchor window has fully
 * elapsed, so the record reflects complete data. The syncer's upsert overwrites
 * the provisional record written by MorningSyncWorker (same user_id + session_date).
 */
class SleepFinalizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sleepSyncManager = SleepSyncManager()

    override suspend fun doWork(): Result {
        val userId = UserSession.getUserId(applicationContext) ?: return Result.success()

        // Yesterday's anchor window closed at 4:00 PM today
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
