package com.noctra.app.domain.usecase

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compares sleep quality on routine-completion nights against non-routine
 * nights (ANALYTICS_SPEC.md §2.3).
 *
 * This returns an [Outcome] plus a number, never a finished sentence. §7 locks
 * the wording, and locked copy belongs in `strings.xml` — the SDD also requires
 * Use Cases to carry no Android framework dependency, so a Use Case cannot reach
 * a string resource. The Fragment maps the outcome to its locked string.
 *
 * §2.3 requires three outcomes plus a not-enough-data state, and says "a
 * verdict needs a clear gap (5+ points) — smaller differences report 'about the
 * same'."
 */
class InsightGenerationUseCase {

    sealed class Result {
        /**
         * @param points absolute score difference in whole points, meaningful
         *                only for [Outcome.BETTER]
         */
        data class Available(val outcome: Outcome, val points: Int) : Result()

        object InsufficientData : Result()
    }

    enum class Outcome { BETTER, WORSE, SIMILAR }

    companion object {
        /** §2.3: below three scored nights in either group, no verdict. */
        const val MIN_DATA_POINTS_PER_GROUP = 3

        /** §2.3: a verdict needs a clear gap of 5+ points. */
        const val MEANINGFUL_DIFFERENCE = 5.0
    }

    fun generate(
        sleepRecords: List<SleepRecord>,
        routineSessions: List<RoutineSession>
    ): Result {
        // §3 "Fair denominators": PENDING nights are still in flight, so they
        // are not routine nights either — they must not land in the
        // non-routine group and drag it down. Only COMPLETED counts as done.
        val routineCompletedDates: Set<String> = routineSessions
            .filter { it.status.equals("COMPLETED", ignoreCase = true) }
            .map { it.sessionDate }
            .toSet()

        val pendingDates: Set<String> = routineSessions
            .filter { it.status.equals("PENDING", ignoreCase = true) }
            .map { it.sessionDate }
            .toSet()

        // Only nights with a usable composite score can be compared at all.
        val scoredRecords = sleepRecords.filter { it.compositeScore != null }

        val withRoutine = scoredRecords.filter { it.sessionDate in routineCompletedDates }

        // Exclude pending nights entirely rather than counting them as misses.
        val withoutRoutine = scoredRecords.filter {
            it.sessionDate !in routineCompletedDates && it.sessionDate !in pendingDates
        }

        if (withRoutine.size < MIN_DATA_POINTS_PER_GROUP ||
            withoutRoutine.size < MIN_DATA_POINTS_PER_GROUP
        ) {
            return Result.InsufficientData
        }

        val diff = withRoutine.mapNotNull { it.compositeScore }.average() -
            withoutRoutine.mapNotNull { it.compositeScore }.average()

        val outcome = when {
            diff >= MEANINGFUL_DIFFERENCE -> Outcome.BETTER
            diff <= -MEANINGFUL_DIFFERENCE -> Outcome.WORSE
            else -> Outcome.SIMILAR
        }

        return Result.Available(outcome, abs(diff).roundToInt())
    }
}