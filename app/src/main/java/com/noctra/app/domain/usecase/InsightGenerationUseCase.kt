package com.noctra.app.domain.usecase

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import kotlin.math.abs
import kotlin.math.roundToInt

class InsightGenerationUseCase {

    sealed class Result {
        data class Insight(val message: String) : Result()
        data class InsufficientData(val message: String) : Result()
    }

    companion object {
        private const val MIN_DATA_POINTS_PER_GROUP = 3
        // Anything below this point-difference is "basically the same"
        private const val MEANINGFUL_DIFFERENCE = 5.0
    }

    fun generate(
        sleepRecords: List<SleepRecord>,
        routineSessions: List<RoutineSession>
    ): Result {
        // Dates the user completed a routine
        val routineCompletedDates: Set<String> = routineSessions
            .filter { it.isCompleted }
            .map { it.sessionDate }
            .toSet()

        // Only consider nights with a usable composite score
        val scoredRecords = sleepRecords.filter { it.compositeScore != null }

        val (withRoutine, withoutRoutine) = scoredRecords.partition { record ->
            record.sessionDate in routineCompletedDates
        }

        if (withRoutine.size < MIN_DATA_POINTS_PER_GROUP ||
            withoutRoutine.size < MIN_DATA_POINTS_PER_GROUP
        ) {
            return Result.InsufficientData(
                "Need more data to generate an insight. " +
                        "Keep logging routines and sleep to unlock comparisons."
            )
        }

        val routineAvg = withRoutine.mapNotNull { it.compositeScore }.average()
        val noRoutineAvg = withoutRoutine.mapNotNull { it.compositeScore }.average()
        val diff = routineAvg - noRoutineAvg
        val diffRounded = abs(diff).roundToInt()

        val message = when {
            diff >= MEANINGFUL_DIFFERENCE ->
                "On nights you complete your routine, your sleep quality is " +
                        "about $diffRounded points higher on average. Keep it up!"

            diff <= -MEANINGFUL_DIFFERENCE ->
                "Your sleep quality this week was higher on nights without the " +
                        "routine. One off week doesn't mean much — keep building the habit."

            else ->
                "Your sleep quality is similar on nights with and without the " +
                        "routine this week. More consistency may reveal clearer patterns."
        }

        return Result.Insight(message)
    }
}