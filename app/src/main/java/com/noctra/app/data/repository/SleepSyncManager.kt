package com.noctra.app.data.repository

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.Instant

/**
 * SleepSyncManager
 *
 * Handles queries to Google Health Connect and implements the 
 * "Wake-Up Anchor" windowing and aggregation logic.
 */
class SleepSyncManager {

    data class AggregatedSleep(
        val onset: Instant,
        val wake: Instant,
        val durationMinutes: Int,
        val avgHeartRate: Double,
        val movementCount: Int
    )

    /**
     * Calculates the "Wake-Up Anchor" window for a specific session date.
     * Rule: Sleep segments ending between 4:00 AM and 4:00 PM on the following day.
     * 
     * @param sessionDate The date the sleep is intended to be associated with (e.g., Monday).
     * @return A pair of Instants representing the start and end of the search window (Tuesday 4am - 4pm).
     */
    fun getWakeUpAnchorWindow(sessionDate: LocalDate): Pair<Instant, Instant> {
        val dayAfter = sessionDate.plusDays(1)
        
        val startOfWindow = LocalDateTime.of(dayAfter, LocalTime.of(4, 0))
            .toInstant(ZoneOffset.UTC)
        
        val endOfWindow = LocalDateTime.of(dayAfter, LocalTime.of(16, 0))
            .toInstant(ZoneOffset.UTC)
            
        return Pair(startOfWindow, endOfWindow)
    }

    /**
     * Aggregates multiple sleep segments into a single record.
     * 
     * @param segments A list of raw sleep data from Google Health.
     * @return A single AggregatedSleep object, or null if no segments exist.
     */
    fun aggregateSegments(segments: List<RawSleepSegment>): AggregatedSleep? {
        if (segments.isEmpty()) return null

        val earliestOnset = segments.minOf { it.startTime }
        val latestWake = segments.maxOf { it.endTime }
        
        val totalDurationMinutes = segments.sumOf { it.durationMinutes }
        val totalMovement = segments.sumOf { it.movementCount }
        
        // Mathematical average for heart rate
        val avgHeartRate = segments.map { it.avgHeartRate }.average()

        return AggregatedSleep(
            onset = earliestOnset,
            wake = latestWake,
            durationMinutes = totalDurationMinutes,
            avgHeartRate = avgHeartRate,
            movementCount = totalMovement
        )
    }

    /**
     * Mock model representing data from Health Connect before aggregation.
     */
    data class RawSleepSegment(
        val startTime: Instant,
        val endTime: Instant,
        val durationMinutes: Int,
        val avgHeartRate: Double,
        val movementCount: Int
    )
}
