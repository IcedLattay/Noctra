package com.noctra.app.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.noctra.app.NoctraApplication
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.domain.usecase.SleepQualityProcessingUseCase
import com.noctra.app.utils.HealthConnectPermissionHelper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * SleepSyncManager
 *
 * The "Syncer" of the audit flow. Queries Google Health Connect, implements the
 * "Wake-Up Anchor" windowing and aggregation logic, derives sleep quality scores,
 * and stores the result as an idempotent SleepRecord (upsert on user_id + session_date).
 */
class SleepSyncManager {

    private val sleepRecordRepository = SleepRecordRepository()
    private val scoreCalculator = SleepQualityProcessingUseCase()

    sealed class SyncResult {
        data class Synced(val record: SleepRecord, val isPartial: Boolean) : SyncResult()
        data object NoData : SyncResult()

        /**
         * Sleep permission not granted (HR-only or nothing granted). The syncer
         * exits BEFORE any read attempt, so callers must treat this as a quiet
         * no-op — never retry. Self-heals if the user grants access later.
         */
        data object PermissionDenied : SyncResult()
        data object HealthConnectUnavailable : SyncResult()
        data class Failed(val error: Throwable) : SyncResult()
    }

    companion object {
        private const val TAG = "SleepSyncManager"

        // Stage types representing sleep (used to derive onset, wake, and duration)
        // Includes STAGE_TYPE_UNKNOWN because Mi Fitness uses it instead of STAGE_TYPE_SLEEPING
        private val SLEEP_STAGE_TYPES = setOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_UNKNOWN,
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM
        )

        // Stage types with real granularity (vs. generic SLEEPING/UNKNOWN fillers)
        private val GRANULAR_STAGE_TYPES = setOf(
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM,
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )

        // Wake-family stages: the restlessness signal
        private val WAKE_STAGE_TYPES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun isHealthConnectAvailable(context: Context = NoctraApplication.instance): Boolean {
        return HealthConnectPermissionHelper.isAvailable(context)
    }

    /**
     * Calculates the "Wake-Up Anchor" window for a specific session date.
     * Rule: Sleep segments ending between 4:00 AM and 4:00 PM on the following day
     * (device-local time) are attributed to that session date.
     */
    fun getWakeUpAnchorWindow(sessionDate: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val dayAfter = sessionDate.plusDays(1)

        val startOfWindow = LocalDateTime.of(dayAfter, LocalTime.of(4, 0))
            .atZone(zone)
            .toInstant()

        val endOfWindow = LocalDateTime.of(dayAfter, LocalTime.of(16, 0))
            .atZone(zone)
            .toInstant()

        return Pair(startOfWindow, endOfWindow)
    }

    /**
     * True once the anchor window for this session date has fully elapsed,
     * i.e. the synced data can be treated as final (no late sleepers pending).
     */
    fun isAnchorWindowClosed(sessionDate: LocalDate): Boolean {
        return Instant.now() >= getWakeUpAnchorWindow(sessionDate).second
    }

    /**
     * Full sync pipeline for one session date:
     * Health Connect -> parse stages -> aggregate -> score -> idempotent upsert.
     *
     * @param hrBaseline the user's learned resting HR baseline, or null while
     *                   it is still being established (first ~7 nights).
     */
    suspend fun syncSessionDate(
        userId: String,
        sessionDate: LocalDate,
        hrBaseline: Double? = null
    ): SyncResult {
        Log.d(TAG, "syncSessionDate called for $sessionDate")

        if (!isHealthConnectAvailable()) {
            Log.w(TAG, "Health Connect unavailable")
            return SyncResult.HealthConnectUnavailable
        }

        return try {
            val client = HealthConnectClient.getOrCreate(NoctraApplication.instance)

            // PERMISSION GUARD: check grants BEFORE any read attempt.
            // Reading an ungranted type throws SecurityException, which callers
            // would misinterpret as a retryable failure. Sleep permission is
            // mandatory for the pipeline (HR alone feeds nothing); HR is optional.
            val granted = HealthConnectPermissionHelper.getGrantedPermissions(client)
            val sleepGranted = HealthConnectPermissionHelper.hasSleepPermission(granted)
            val hrGranted = HealthConnectPermissionHelper.hasHeartRatePermission(granted)
            Log.d(TAG, "Permissions — sleep: $sleepGranted, hr: $hrGranted")

            if (!sleepGranted) {
                Log.w(TAG, "Sleep permission denied — returning early")
                return SyncResult.PermissionDenied
            }

            val (windowStart, windowEnd) = getWakeUpAnchorWindow(sessionDate)
            Log.d(TAG, "Anchor window: $windowStart → $windowEnd")

            // 1. FETCH: all sleep sessions attributed to this session date
            val sleepResponse = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd)
                )
            )
            Log.d(TAG, "Raw sleep records fetched: ${sleepResponse.records.size}")

            // 2. PARSE: one RawSleepSegment per session (onset/wake from sleep stages)
            val segments = sleepResponse.records.mapNotNull(::parseSession)
                .filter { it.durationMinutes > 0 }
            Log.d(TAG, "Parsed segments: ${segments.size}")

            if (segments.isEmpty()) {
                Log.w(TAG, "No valid segments — returning NoData")
                return SyncResult.NoData
            }

            // 3. AGGREGATE: merge main sleep + naps into one night
            val aggregated = aggregateSegments(segments)
            if (aggregated == null) {
                Log.w(TAG, "Aggregation returned null — returning NoData")
                return SyncResult.NoData
            }

            // 4. HEART RATE: nightly average over the aggregated sleep interval
            // (only when the user granted heart-rate access; null otherwise —
            // calculateScores() redistributes the weight)
            val avgHeartRate = if (hrGranted) {
                readAverageHeartRate(client, aggregated.onset, aggregated.wake)
            } else {
                null
            }

            // 5. SCORE
            val scores = scoreCalculator.calculateScores(
                durationMinutes = aggregated.durationMinutes,
                avgHeartRate = avgHeartRate,
                movementCount = aggregated.movementCount,
                hrBaseline = hrBaseline
            )
            Log.d(TAG, "Scores — duration: ${scores.durationScore}, hr: ${scores.heartRateScore}, movement: ${scores.movementScore}, composite: ${scores.compositeScore}")

            // 6. UPSERT: reuse the existing record's id for this user+date so the
            //    write is idempotent (provisional pass -> finalization overwrite)
            val dateStr = sessionDate.toString()
            val existing = sleepRecordRepository
                .getRecordsInRange(userId, dateStr, dateStr)
                .firstOrNull()

            val isPartial = !isAnchorWindowClosed(sessionDate)

            val record = SleepRecord(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = userId,
                sessionDate = dateStr,
                sleepOnsetTime = aggregated.onset.toString(),
                wakeTime = aggregated.wake.toString(),
                sleepDurationMinutes = aggregated.durationMinutes,
                avgHeartRateBpm = avgHeartRate,
                movementEventCount = aggregated.movementCount,
                hrBaselineAtScoring = hrBaseline,
                durationScore = scores.durationScore,
                heartRateScore = scores.heartRateScore,
                movementScore = scores.movementScore,
                compositeScore = scores.compositeScore,
                isPartialData = isPartial,
                dataCaptureSuccess = true
            )

            sleepRecordRepository.insertRecord(record)
            Log.d(TAG, "Sync complete — record saved: ${record.sessionDate}, composite: ${record.compositeScore}")
            SyncResult.Synced(record, isPartial)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.Failed(e)
        }
    }

    // ---------------------------------------------------------------------
    // Parsing & aggregation
    // ---------------------------------------------------------------------

    /**
     * A single Health Connect sleep session parsed into Noctra's shape.
     *
     * @param onset start of the FIRST sleep-type stage (excludes pre-sleep awake time)
     * @param wake end of the LAST sleep-type stage (excludes morning awake-in-bed time)
     * @param durationMinutes sum of sleep-type stage durations
     * @param movementCount count of wake-family stages strictly between the first and
     *                      last sleep stage; null when the device only exports
     *                      coarse (SLEEPING/UNKNOWN) stages or no stages at all
     */
    data class RawSleepSegment(
        val onset: Instant,
        val wake: Instant,
        val durationMinutes: Int,
        val movementCount: Int?
    )

    data class AggregatedSleep(
        val onset: Instant,
        val wake: Instant,
        val durationMinutes: Int,
        val movementCount: Int?,
        val avgHeartRate: Double?
    )

    private fun parseSession(record: SleepSessionRecord): RawSleepSegment? {
        val stages = record.stages
        Log.d(TAG, "parseSession — ${stages.size} stages, session: ${record.startTime} → ${record.endTime}")

        // No stages exported: treat the whole session as one coarse sleep block
        if (stages.isEmpty()) {
            Log.d(TAG, "parseSession — no stages, treating as coarse block")
            return RawSleepSegment(
                onset = record.startTime,
                wake = record.endTime,
                durationMinutes = Duration.between(record.startTime, record.endTime)
                    .toMinutes().toInt(),
                movementCount = null
            )
        }

        // Log all stage types for debugging
        stages.forEach { stage ->
            Log.d(TAG, "parseSession — stage type: ${stage.stage}, start: ${stage.startTime}, end: ${stage.endTime}")
        }

        val sleepStages = stages.filter { it.stage in SLEEP_STAGE_TYPES }
        Log.d(TAG, "parseSession — sleepStages: ${sleepStages.size}")

        if (sleepStages.isEmpty()) {
            Log.w(TAG, "parseSession — no sleep-type stages found, returning null")
            return null
        }

        val onset = sleepStages.minOf { it.startTime }
        val wake = sleepStages.maxOf { it.endTime }

        // Calculate duration from onset → wake (not sum of individual stages)
        // This handles overlapping stages correctly (e.g., Mi Fitness writes
        // both SLEEPING and UNKNOWN for the same time range)
        val durationMinutes = Duration.between(onset, wake).toMinutes().toInt()

        // Coarse-device guard: only trust the movement count if the device
        // exported granular stages; SLEEPING-only exports carry no restlessness info
        val isGranular = stages.any { it.stage in GRANULAR_STAGE_TYPES }

        val movementCount = if (isGranular) {
            stages.count {
                it.stage in WAKE_STAGE_TYPES &&
                    it.startTime >= onset && it.endTime <= wake
            }
        } else {
            null
        }

        return RawSleepSegment(onset, wake, durationMinutes, movementCount)
    }

    /**
     * Aggregates multiple sleep segments (main sleep + naps) into a single night.
     */
    fun aggregateSegments(segments: List<RawSleepSegment>): AggregatedSleep? {
        if (segments.isEmpty()) return null

        val earliestOnset = segments.minOf { it.onset }
        val latestWake = segments.maxOf { it.wake }
        val totalDurationMinutes = segments.sumOf { it.durationMinutes }
        val totalMovement = segments.mapNotNull { it.movementCount }.sumOrNull()

        return AggregatedSleep(
            onset = earliestOnset,
            wake = latestWake,
            durationMinutes = totalDurationMinutes,
            movementCount = totalMovement,
            avgHeartRate = null // filled by readAverageHeartRate after aggregation
        )
    }

    private fun List<Int>.sumOrNull(): Int? {
        if (isEmpty()) return null
        return sum()
    }

    // ---------------------------------------------------------------------
    // Health Connect reads
    // ---------------------------------------------------------------------

    private suspend fun readAverageHeartRate(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val samples = response.records.flatMap { it.samples }
        if (samples.isEmpty()) return null
        return samples.map { it.beatsPerMinute.toDouble() }.average()
    }
}
