package com.noctra.app.domain.usecase

import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RewardLedgerRepository
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
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository(),
    private val shopRepository: ShopRepository = ShopRepository(),
    private val sleepQualityUseCase: SleepQualityProcessingUseCase = SleepQualityProcessingUseCase()
) {
    suspend fun seedMockData(userId: String) {
        // 1. Clear existing user data to allow re-testing
        try {
            SupabaseClient.client.from("sleep_records").delete { filter { eq("user_id", userId) } }
            SupabaseClient.client.from("user_inventory").delete { filter { eq("user_id", userId) } }
            // Clear existing shop items to ensure only demo items exist
            SupabaseClient.client.from("shop_items").delete { filter { gte("token_cost", 0) } }
        } catch (e: Exception) {
            android.util.Log.e("DataSeeding", "Cleanup failed: ${e.message}")
        }

        val today = LocalDate.now()
        
        // 2. Reset Ledger with tokens and XP
        val ledger = rewardRepository.getRewardLedger(userId)
        if (ledger != null) {
            rewardRepository.updateRewardLedger(ledger.copy(
                tokenBalance = 2500,
                totalXp = 0, // Reset to 0 so they can test evolution from scratch
                currentStreak = 0,
                devolutionPending = false,
                lastSessionDate = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                lastUpdated = java.time.OffsetDateTime.now().toString()
            ))
        }

        // 3. Re-seed Shop Items with valid UUIDs and exact names requested
        try {
            val mockItems = listOf(
                ShopItem("550e8400-e29b-41d4-a716-446655440001", "Yellow Beanie", "A cozy yellow hat", "HAT", 0, "hat_default_icon", "hat_default", 1),
                ShopItem("550e8400-e29b-41d4-a716-446655440002", "Clouds", "Soft and dreamy", "HAT", 1000, "hat_cloud_icon", "hat_cloud", 2),
                ShopItem("550e8400-e29b-41d4-a716-446655440003", "Flower Garland", "Garden fresh", "HAT", 800, "hat_garland_icon", "hat_garland", 3),
                ShopItem("550e8400-e29b-41d4-a716-446655440004", "Propeller Hat", "Fun and fast", "HAT", 500, "hat_propeller_icon", "hat_propeller", 4)
            )
            SupabaseClient.client.from("shop_items").insert(mockItems)
        } catch (e: Exception) {
            android.util.Log.e("DataSeeding", "Shop seeding failed: ${e.message}")
        }

        // 4. Seed 7 days of sleep data
        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            
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

            val record = SleepRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                sessionDate = dateString,
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
            sleepRecordRepository.insertSleepRecord(record)
        }
    }
}
