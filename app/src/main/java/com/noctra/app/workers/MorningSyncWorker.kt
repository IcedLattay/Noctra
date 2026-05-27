package com.noctra.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.domain.usecase.SleepQualityProcessingUseCase
import com.noctra.app.utils.UserSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class MorningSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sleepRecordRepository = SleepRecordRepository()
    private val sleepQualityUseCase = SleepQualityProcessingUseCase()

    override suspend fun doWork(): Result {
        val userId = UserSession.getUserId(applicationContext)
        
        // Generate mock data
        val durationMinutes = Random.nextInt(330, 540) // 5.5 to 9 hours
        val avgHeartRate = Random.nextDouble(55.0, 75.0)
        val movementCount = Random.nextInt(0, 50)
        
        // Assume a baseline of 60 for HR if not available
        val hrBaseline = 60.0 

        val scores = sleepQualityUseCase.calculateScores(
            durationMinutes = durationMinutes,
            avgHeartRate = avgHeartRate,
            movementCount = movementCount,
            hrBaseline = hrBaseline
        )

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val mockRecord = SleepRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            sessionDate = today,
            sleepDurationMinutes = durationMinutes,
            avgHeartRateBpm = avgHeartRate,
            movementEventCount = movementCount,
            hrBaselineAtScoring = hrBaseline,
            durationScore = scores.durationScore,
            heartRateScore = scores.heartRateScore,
            movementScore = scores.movementScore,
            compositeScore = scores.compositeScore,
            dataCaptureSuccess = true
        )

        return try {
            sleepRecordRepository.insertSleepRecord(mockRecord)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
