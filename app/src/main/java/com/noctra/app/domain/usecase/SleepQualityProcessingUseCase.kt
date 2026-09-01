package com.noctra.app.domain.usecase

import kotlin.math.abs
import kotlin.math.max

class SleepQualityProcessingUseCase {

    data class SleepScores(
        val durationScore: Int,
        val heartRateScore: Int?,
        val movementScore: Int?,
        val compositeScore: Int
    )

    /**
     * Calculates per-night sleep quality scores.
     *
     * Heart rate and movement inputs are nullable: real Health Connect data may
     * lack a learned HR baseline (first ~7 nights) or stage data entirely
     * (coarse devices). Missing components are excluded and their weight is
     * redistributed to the remaining components rather than assumed perfect.
     */
    fun calculateScores(
        durationMinutes: Int,
        avgHeartRate: Double?,
        movementCount: Int?,
        hrBaseline: Double?
    ): SleepScores {
        val hours = durationMinutes / 60.0

        val durationScore = when {
            hours in 7.0..9.0 -> 100
            hours < 7.0 -> ((hours / 7.0) * 100).toInt()
            else -> max(0, (100 - (hours - 9.0) * 20).toInt())
        }

        val heartRateScore = if (avgHeartRate != null && hrBaseline != null) {
            max(0, (100 - abs(avgHeartRate - hrBaseline) * 5).toInt())
        } else {
            null
        }

        val movementScore = movementCount?.let { count ->
            if (count <= 10) {
                100
            } else {
                max(0, (100 - (count - 10) * 0.5).toInt())
            }
        }

        // Composite score: weights redistributed over the available components
        val compositeScore = when {
            heartRateScore != null && movementScore != null ->
                (durationScore * 0.5 + heartRateScore * 0.3 + movementScore * 0.2).toInt()
            movementScore != null ->
                (durationScore * 0.7 + movementScore * 0.3).toInt()
            heartRateScore != null ->
                (durationScore * 0.7 + heartRateScore * 0.3).toInt()
            else ->
                durationScore
        }

        return SleepScores(
            durationScore = durationScore,
            heartRateScore = heartRateScore,
            movementScore = movementScore,
            compositeScore = compositeScore
        )
    }
}
