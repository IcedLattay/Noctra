package com.noctra.app.domain.usecase

import kotlin.math.abs
import kotlin.math.max

class SleepQualityProcessingUseCase {

    data class SleepScores(
        val durationScore: Int,
        val heartRateScore: Int?,
        val movementScore: Int,
        val compositeScore: Int
    )

    fun calculateScores(
        durationMinutes: Int,
        avgHeartRate: Double,
        movementCount: Int,
        hrBaseline: Double?
    ): SleepScores {
        val hours = durationMinutes / 60.0
        
        val durationScore = when {
            hours in 7.0..9.0 -> 100
            hours < 7.0 -> ((hours / 7.0) * 100).toInt()
            else -> max(0, (100 - (hours - 9.0) * 20).toInt())
        }

        val heartRateScore = hrBaseline?.let {
            max(0, (100 - abs(avgHeartRate - it) * 5).toInt())
        }

        val movementScore = if (movementCount <= 10) {
            100
        } else {
            max(0, (100 - (movementCount - 10) * 0.5).toInt())
        }

        // Composite score calculation
        val compositeScore = if (heartRateScore != null) {
            (durationScore * 0.5 + heartRateScore * 0.3 + movementScore * 0.2).toInt()
        } else {
            // If no HR baseline, redistribute weights? 
            // PDF says null during first 7 nights. Let's assume 0.7/0.3 or similar if null, 
            // or just use 0.5/0.2 and normalize.
            // Let's use 0.7/0.3 for duration/movement as a fallback.
            (durationScore * 0.7 + movementScore * 0.3).toInt()
        }

        return SleepScores(
            durationScore = durationScore,
            heartRateScore = heartRateScore,
            movementScore = movementScore,
            compositeScore = compositeScore
        )
    }
}
