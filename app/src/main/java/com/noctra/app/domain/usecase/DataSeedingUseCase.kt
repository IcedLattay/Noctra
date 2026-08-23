package com.noctra.app.domain.usecase

import android.util.Log
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.ShopRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class DataSeedingUseCase(
    private val sleepRecordRepository: SleepRecordRepository = SleepRecordRepository(),
    private val routineSessionRepository: RoutineSessionRepository = RoutineSessionRepository(),
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository(),
    private val shopRepository: ShopRepository = ShopRepository(),
    private val sleepQualityUseCase: SleepQualityProcessingUseCase = SleepQualityProcessingUseCase()
) {
    suspend fun seedMockData(userId: String) {
        // 1. Clear existing user data to allow re-testing
        try {
            // Delete user-specific inventory first
            SupabaseClient.client.from("user_inventory").delete { filter { eq("user_id", userId) } }

            // Sleep and Routine sessions
            sleepRecordRepository.deleteAllForUser(userId)
            routineSessionRepository.deleteAllForUser(userId)
        } catch (e: Exception) {
            Log.e("DataSeeding", "Cleanup failed: ${e.message}")
        }

        val today = LocalDate.now()
        
        // 2. Reset Ledger with tokens and XP
        val ledger = rewardRepository.getRewardLedger(userId)
        if (ledger != null) {
            rewardRepository.updateRewardLedger(ledger.copy(
                tokenBalance = 2500,
                totalXp = 0,
                currentStreak = 0,
                hasFirstMiss = false,
                lastSessionDate = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                lastUpdated = java.time.OffsetDateTime.now().toString()
            ))
        }

        // 3. Re-seed Shop Items with valid UUIDs
        val mockItems = listOf(
            ShopItem("550e8400-e29b-41d4-a716-446655440001", "Yellow Beanie", "A cozy yellow hat", "HAT", 0, "hat_sleeping_hat_icon", "hat_sleeping_hat", 1),
            ShopItem("550e8400-e29b-41d4-a716-446655440002", "Clouds", "Soft and dreamy", "HAT", 1000, "hat_cloud_icon", "hat_cloud", 2),
            ShopItem("550e8400-e29b-41d4-a716-446655440003", "Flower Garland", "Garden fresh", "HAT", 800, "hat_floral_crown_icon", "hat_floral_crown", 3),
            ShopItem("550e8400-e29b-41d4-a716-446655440004", "Propeller Hat", "Fun and fast", "HAT", 500, "hat_propeller_hat_icon", "hat_propeller_hat", 4)
        )
        // Use upsert for shop items to avoid "already exists" if delete failed
        SupabaseClient.client.from("shop_items").upsert(mockItems, onConflict = "item_id")

        // 4. Seed 7 days of sleep and routine data
        val sleepRecords = mutableListOf<SleepRecord>()
        val routineSessions = mutableListOf<RoutineSession>()

        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            // Sleep Record
            val durationMinutes = Random.nextInt(360, 520)
            val avgHeartRate = Random.nextDouble(58.0, 72.0)
            val movementCount = Random.nextInt(5, 45)
            val hrBaseline = 62.0

            val scores = sleepQualityUseCase.calculateScores(
                durationMinutes = durationMinutes,
                avgHeartRate = avgHeartRate,
                movementCount = movementCount,
                hrBaseline = hrBaseline
            )

            sleepRecords.add(SleepRecord(
                id = UUID.nameUUIDFromBytes("sleep_${userId}_${dateString}".toByteArray()).toString(),
                userId = userId,
                sessionDate = dateString,
                sleepOnsetTime = dateString + "T22:15:00Z",
                sleepDurationMinutes = durationMinutes,
                avgHeartRateBpm = avgHeartRate,
                movementEventCount = movementCount,
                hrBaselineAtScoring = hrBaseline,
                durationScore = scores.durationScore,
                heartRateScore = scores.heartRateScore,
                movementScore = scores.movementScore,
                compositeScore = scores.compositeScore,
                dataCaptureSuccess = true
            ))

            // Routine Session (Randomly complete some)
            val isCompleted = Random.nextBoolean()
            routineSessions.add(RoutineSession(
                id = UUID.nameUUIDFromBytes("routine_${userId}_${dateString}".toByteArray()).toString(),
                userId = userId,
                sessionDate = dateString,
                startTimestamp = dateString + "T21:30:00Z",
                completionTimestamp = if (isCompleted) dateString + "T21:55:00Z" else null,
                status = if (isCompleted) "COMPLETED" else "MISSED",
                tokensEarned = if (isCompleted) 50 else null,
                xpEarned = if (isCompleted) 100 else null
            ))
        }

        // Bulk upsert to prevent duplicate key errors if cleanup was incomplete
        sleepRecordRepository.insertRecords(sleepRecords)
        routineSessionRepository.insertSessions(routineSessions)
    }
}
