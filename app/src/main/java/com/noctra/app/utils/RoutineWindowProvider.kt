package com.noctra.app.utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * RoutineWindowProvider
 *
 * Centralized logic for determining if the user is currently within their
 * allowed "Noctra Window" for performing their routine.
 */
object RoutineWindowProvider {

    private const val WINDOW_CLOSE_BUFFER_MINUTES = 60L

    /**
     * Determines if the current time is within the routine window.
     * 
     * @param now Current time
     * @param targetBedtime Bedtime string (HH:mm:ss or HH:mm)
     * @param routineDurationMinutes Total duration of activities
     * @return True if within [bedtime - duration, bedtime + 60m]
     */
    fun isTimeInWindow(
        now: LocalTime,
        targetBedtime: String,
        routineDurationMinutes: Int
    ): Boolean {        val bedtime = parseTime(targetBedtime)
        val windowOpen = bedtime.minusMinutes(routineDurationMinutes.toLong())
        val windowClose = bedtime.plusMinutes(WINDOW_CLOSE_BUFFER_MINUTES)

        return if (!windowOpen.isAfter(windowClose)) {
            // Simple window: 22:00 to 23:30
            !now.isBefore(windowOpen) && !now.isAfter(windowClose)
        } else {
            // Midnight crossing window: 23:30 to 01:00
            !now.isBefore(windowOpen) || !now.isAfter(windowClose)
        }
    }

    private fun parseTime(raw: String): LocalTime {
        return try {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    /**
     * Resolves which calendar date a routine session belongs to.
     *
     * Sessions starting before 4 AM attribute to the previous calendar
     * day — mirroring the sleep pipeline's wake-up-anchor boundary so
     * both pipelines agree on what "last night" means (a 1 AM routine
     * and its sleep both land on the same session_date, keeping streaks
     * and insight joins aligned). Given the bedtime picker caps at
     * 2 AM (latest start ~3 AM), every post-midnight start falls on the
     * correct side of this line — no ambiguity remains.
     */
    fun resolveRoutineSessionDate(now: java.time.LocalDateTime): java.time.LocalDate {
        return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }
}
