package com.noctra.app.domain.usecase

import android.util.Log
import com.noctra.app.data.repository.SleepRecordRepository
import java.time.LocalDate

/**
 * Learns the user's personal sleeping heart-rate baseline from their own
 * sleep history.
 *
 * The baseline is a ROLLING MEDIAN of the last [WINDOW_NIGHTS] nights that
 * have a valid average heart rate, strictly BEFORE the night being scored.
 * Median (not mean) because sleeping HR carries occasional outliers —
 * alcohol, illness, late caffeine, a loose watch band — which would drag a
 * mean and then penalise the following "normal" night.
 *
 * Deliberately minimal: activates from a single historical night (n >= 1) so
 * the HR dimension is live early; early baselines are noisy, which the caller
 * surfaces honestly rather than hiding behind a long gate. Snapshot the value
 * used into sleep_records.hr_baseline_at_scoring so any score stays auditable
 * against the baseline in force at the time.
 *
 * Returns null — meaning "no HR score this night, redistribute the weight" —
 * when the user has no prior nights with HR data or the history read fails.
 * A failed baseline read must never take down the sync itself.
 */
class HrBaselineUseCase(
    private val sleepRecordRepository: SleepRecordRepository = SleepRecordRepository()
) {

    companion object {
        private const val TAG = "HrBaselineUseCase"

        /** Trailing window: the last N nights WITH valid HR data (not calendar nights). */
        private const val WINDOW_NIGHTS = 7
    }

    /**
     * Computes the rolling-median HR baseline for scoring [beforeDate].
     *
     * @param beforeDate the session date being scored; only strictly earlier
     *                   nights are considered, so a night can never influence
     *                   its own baseline (which would force its deviation to
     *                   zero and fake a perfect HR score).
     * @return the baseline in BPM, or null while no HR history exists.
     */
    suspend fun computeBaseline(userId: String, beforeDate: LocalDate): Double? {
        return try {
            // Ordered ASC by the repository, so takeLast() grabs the most
            // recent valid nights within the window.
            val priorNights = sleepRecordRepository
                .getRecordsInRange(userId, "2000-01-01", beforeDate.minusDays(1).toString())
                .filter { it.avgHeartRateBpm != null }
                .takeLast(WINDOW_NIGHTS)

            if (priorNights.isEmpty()) {
                Log.d(TAG, "No prior HR nights before $beforeDate — baseline stays null")
                return null
            }

            val baseline = median(priorNights.mapNotNull { it.avgHeartRateBpm })
            Log.d(TAG, "Baseline from ${priorNights.size} night(s): $baseline BPM")
            baseline
        } catch (e: Exception) {
            Log.w(TAG, "Baseline read failed — scoring without HR this night", e)
            null
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            // Even count: average of the two middle values.
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
