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
    fun resolveRoutineSessionDate(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): java.time.LocalDate {
        return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /**
     * True once [date] can no longer gain a routine row through
     * back-attribution — i.e. the 4 AM anchor has rolled past it and a
     * start can never again be dated to it.
     *
     * Miss judgers MUST consult this instead of the plain calendar:
     * between midnight and 4 AM the previous date is still a LIVE night
     * (post-midnight starts back-date to it), so marking an empty
     * previous date MISSED in that window both creates a phantom row —
     * which the later real session duplicates, desyncing every
     * first-row-wins consumer — and folds a streak penalty onto a night
     * the user hasn't had a chance to perform yet.
     */
    fun isSessionDateClosed(
        date: java.time.LocalDate,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): Boolean {
        return date.isBefore(resolveRoutineSessionDate(now))
    }

    /**
     * Which date the Routine screen treats as "today" for its completed
     * state. The latest possible window (2 AM bedtime) closes at 3 AM, so
     * the screen resets at 3 — not midnight (which forgets tonight's
     * completion and lets a moved bedtime reopen a second routine) and
     * not 2 AM (which would split that latest window mid-routine).
     */
    fun resolveRoutineScreenDate(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): java.time.LocalDate {
        return if (now.hour < 3) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /**
     * Dates a miss-judgement pass must consider: (anchor, yesterday],
     * capped at the 14-day open-path window.
     *
     * The old single-date check ("judge yesterday only") had a blind
     * spot: if that one 9:30 run didn't fire, the night was never
     * judged by anyone — and a later penalty's anchor-hop could then
     * step over it permanently (anchor moves to the newer date, the
     * audit starts at anchor+1, the gap is sealed). Walking the range
     * in order fixes this: each inserted miss advances the anchor onto
     * its own date *before* the next date is considered, so the anchor
     * can never jump over an empty night.
     *
     * The 14-day floor keeps the partition with AuditBackfillWorker
     * (which owns [onboarding, today-14)) and bounds a single pass.
     * Null anchor → just yesterday (fresh account: nothing to walk).
     * Callers still skip dates that already have a row (pending rows
     * belong to the audit; completed/missed rows are final) and must
     * consult [isSessionDateClosed] per date.
     */
    fun missJudgementDates(
        anchor: java.time.LocalDate?,
        today: java.time.LocalDate = java.time.LocalDate.now()
    ): List<java.time.LocalDate> {
        val yesterday = today.minusDays(1)
        val start = maxOf(anchor?.plusDays(1) ?: yesterday, today.minusDays(14))
        if (start.isAfter(yesterday)) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(yesterday) }
            .toList()
    }
}
