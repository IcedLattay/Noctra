package com.noctra.app.domain.usecase

import com.noctra.app.data.model.SleepRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Classifies each night's adherence to the user's target bedtime.
 *
 * Per the context PDF:
 *   ADHERENT          → sleep onset ≤30 min after target (or before target)
 *   SLIGHT_DELAY      → 30 < delay ≤60 min
 *   SIGNIFICANT_DELAY → delay >60 min
 *   NO_DATA           → no sleep_onset_time recorded
 *
 * Edge case: if sleep onset is BEFORE target bedtime (user fell asleep early),
 * treated as ADHERENT.
 */
class BedtimeAdherenceCalculator {

    enum class Adherence { ADHERENT, SLIGHT_DELAY, SIGNIFICANT_DELAY, NO_DATA }

    data class NightAdherence(
        val date: LocalDate,
        val adherence: Adherence,
        /** Minutes between target bedtime and actual sleep onset (negative if early, null if no data) */
        val delayMinutes: Long?
    )

    /**
     * Classify a single night.
     *
     * @param sessionDate the date the sleep session belongs to
     * @param targetBedtime user's configured target bedtime as a LocalTime ("HH:mm:ss")
     * @param sleepOnsetTime the actual ISO 8601 timestamp from sleep_records, or null
     */
    fun classify(
        sessionDate: LocalDate,
        targetBedtime: LocalTime?,
        sleepOnsetTime: String?
    ): NightAdherence {
        if (targetBedtime == null || sleepOnsetTime.isNullOrBlank()) {
            return NightAdherence(sessionDate, Adherence.NO_DATA, null)
        }

        return try {
            val targetInstant = computeTargetInstant(sessionDate, targetBedtime)
            val actualInstant = Instant.parse(sleepOnsetTime)

            val delayMinutes = (actualInstant.epochSecond - targetInstant.epochSecond) / 60L

            val classification = when {
                delayMinutes <= 30 -> Adherence.ADHERENT       // includes early sleep (negative delay)
                delayMinutes <= 60 -> Adherence.SLIGHT_DELAY
                else -> Adherence.SIGNIFICANT_DELAY
            }

            NightAdherence(sessionDate, classification, delayMinutes)
        } catch (e: Exception) {
            NightAdherence(sessionDate, Adherence.NO_DATA, null)
        }
    }

    /**
     * Convert a (sessionDate + targetBedtime) pair into the absolute Instant
     * representing when the user *intended* to fall asleep.
     *
     * If target is 8 PM–11:59 PM → same calendar day as sessionDate
     * If target is 12 AM–2 AM    → next calendar day (the early-morning portion)
     */
    private fun computeTargetInstant(
        sessionDate: LocalDate,
        targetBedtime: LocalTime
    ): Instant {
        val zone = ZoneId.systemDefault()
        return if (targetBedtime.hour >= 20) {
            sessionDate.atTime(targetBedtime).atZone(zone).toInstant()
        } else {
            sessionDate.plusDays(1).atTime(targetBedtime).atZone(zone).toInstant()
        }
    }

    /**
     * Convenience: classify all 7 days of a week.
     * Returns adherence for each day Mon→Sun, in order.
     */
    fun classifyWeek(
        weekStart: LocalDate,
        targetBedtime: LocalTime?,
        sleepRecords: List<SleepRecord>
    ): List<NightAdherence> {
        val byDate = sleepRecords.associateBy { it.sessionDate }
        return (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val record = byDate[date.toString()]
            classify(date, targetBedtime, record?.sleepOnsetTime)
        }
    }
}