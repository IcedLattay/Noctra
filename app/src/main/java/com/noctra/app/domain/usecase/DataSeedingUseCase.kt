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
        } catch (e: Exception) {}

        val today = LocalDate.now()
        
        // 2. Reset Ledger with tokens and XP
        val ledger = rewardRepository.getRewardLedger(userId)
        if (ledger != null) {
            rewardRepository.updateRewardLedger(ledger.copy(
                tokenBalance = 2500,
                totalXp = 1600, 
                currentStreak = 5,
                devolutionPending = false,
                lastSessionDate = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            ))
        }

        // 3. Seed some Shop Items if the table is empty
        val items = shopRepository.getAllShopItems()
        if (items.isEmpty()) {
            val mockItems = listOf(
                ShopItem("h1", "Yellow Beanie", "A cozy hat", "HAT", 200, "shleepyicon", "shleepyicon", 1),
                ShopItem("h2", "Cool Shades", "Sun protection", "ACCESSORY", 500, "shleepyicon", "shleepyicon", 2),
                ShopItem("o1", "Space Suit", "To the moon!", "OUTFIT", 1000, "shleepyicon", "shleepyicon", 3),
                ShopItem("f1", "Sleepy Slippers", "Soft feet", "FOOTWEAR", 300, "shleepyicon", "shleepyicon", 4)
            )
            mockItems.forEach { 
                try {
                    SupabaseClient.client.from("shop_items").insert(it)
                } catch (e: Exception) {}
            }
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
