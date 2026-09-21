package com.noctra.app.domain.usecase

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Computes a month-level trend insight by comparing performance between
 * Week 1 (days 1–7) and Week 4 (days 22–28) of the selected month.
 *
 * Requires at least 3 data points in each week to generate a comparison.
 * Returns a plain-language insight string comparing adherence and sleep quality
 * between the first and fourth weeks — directly operationalizing GO3.
 */
class MonthlyInsightGenerationUseCase {

    sealed class Result {
        data class Insight(val message: String) : Result()
        data class InsufficientData(val message: String) : Result()
    }

    companion object {
        private const val MIN_DATA_POINTS = 3
        private const val MEANINGFUL_DIFFERENCE = 5.0
    }

    data class WeekData(
        val sleepRecords: List<SleepRecord>,
        val routineSessions: List<RoutineSession>
    )

    /**
     * Generate a month-level insight comparing Week 1 vs Week 4.
     *
     * @param week1Data sleep records and sessions from days 1–7 of the month
     * @param week4Data sleep records and sessions from days 22–28 of the month
     */
    fun generate(week1Data: WeekData, week4Data: WeekData): Result {
        val w1Scores = week1Data.sleepRecords
            .mapNotNull { it.compositeScore }
        val w4Scores = week4Data.sleepRecords
            .mapNotNull { it.compositeScore }

        if (w1Scores.size < MIN_DATA_POINTS || w4Scores.size < MIN_DATA_POINTS) {
            return Result.InsufficientData(
                "Keep logging your routine and sleep for a few more weeks " +
                        "to see your monthly progress trend."
            )
        }

        val w1AvgScore = w1Scores.average()
        val w4AvgScore = w4Scores.average()

        // Calculate routine completion rates
        val w1Days = week1Data.sleepRecords.size.coerceAtLeast(1)
        val w4Days = week4Data.sleepRecords.size.coerceAtLeast(1)
        val w1Completed = week1Data.routineSessions.count { it.status == "COMPLETED" }
        val w4Completed = week4Data.routineSessions.count { it.status == "COMPLETED" }
        val w1CompletionRate = (w1Completed * 100.0 / w1Days)
        val w4CompletionRate = (w4Completed * 100.0 / w4Days)

        val scoreDiff = w4AvgScore - w1AvgScore
        val completionDiff = w4CompletionRate - w1CompletionRate
        val scoreDiffRounded = abs(scoreDiff).roundToInt()
        val completionDiffRounded = abs(completionDiff).roundToInt()

        val message = when {
            scoreDiff > MEANINGFUL_DIFFERENCE && completionDiff > 0 ->
                "Comparing Week 1 to Week 4: Your sleep quality improved by " +
                        "$scoreDiffRounded% and routine completion increased by " +
                        "$completionDiffRounded%. Great progress this month!"

            scoreDiff > MEANINGFUL_DIFFERENCE ->
                "Comparing Week 1 to Week 4: Your sleep quality improved by " +
                        "$scoreDiffRounded%. Keep maintaining your routine for even better results!"

            completionDiff > 5.0 ->
                "Comparing Week 1 to Week 4: Your routine completion improved by " +
                        "$completionDiffRounded%. Better consistency leads to better sleep!"

            scoreDiff < -MEANINGFUL_DIFFERENCE ->
                "Comparing Week 1 to Week 4: Your sleep scores dipped slightly this month. " +
                        "Try to stick to your routine every night for more consistent results."

            else ->
                "Your sleep quality and routine completion have been fairly consistent " +
                        "this month. Try increasing your routine frequency for even better results."
        }

        return Result.Insight(message)
    }
}
