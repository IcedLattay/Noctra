package com.noctra.app.utils

import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Inserts realistic-looking sleep + routine data for the past 7 days so the
 * Analytics dashboard has something to render during development and demos.
 *
 * Safe to run repeatedly: deletes existing rows for this user first, then re-seeds.
 *
 * Composite score is computed using the formula from the context PDF:
 *   composite = (duration_score × 0.50) + (heart_rate_score × 0.30) + (movement_score × 0.20)
 */
class DemoDataSeeder(
    private val sleepRepo: SleepRecordRepository,
    private val sessionRepo: RoutineSessionRepository
) {

    /** Fixed seed so the demo data looks the same each run — easier to reason about during development. */
    private val rng = Random(42)

    suspend fun seedLastSevenDays(userId: String, targetBedtime: LocalTime) {
        // Clear existing data so we don't get unique-constraint violations on re-seed.
        sleepRepo.deleteAllForUser(userId)
        sessionRepo.deleteAllForUser(userId)

        // Pre-baked baseline so HR score is meaningful (would normally be computed over 7 nights)
        val hrBaseline = 62.0

        val today = LocalDate.now()
        val sleepRecords = mutableListOf<SleepRecord>()
        val sessions = mutableListOf<RoutineSession>()

        // Generate 7 nights: 6 days ago through yesterday (we don't seed tonight)
        for (daysAgo in 7 downTo 1) {
            val sessionDate = today.minusDays(daysAgo.toLong())
            val scenario = pickScenario(daysAgo)

            // ── Sleep record ──
            val sleepRecord = buildSleepRecord(
                userId = userId,
                sessionDate = sessionDate,
                targetBedtime = targetBedtime,
                hrBaseline = hrBaseline,
                scenario = scenario
            )
            sleepRecords.add(sleepRecord)

            // ── Routine session (some nights completed, some not) ──
            sessions.add(
                buildRoutineSession(
                    userId = userId,
                    sessionDate = sessionDate,
                    targetBedtime = targetBedtime,
                    completed = scenario.routineCompleted
                )
            )
        }

        sleepRepo.insertRecords(sleepRecords)
        sessionRepo.insertSessions(sessions)
    }

    /**
     * Mix of scenarios so the charts show variety. Fixed per day-offset so the
     * demo is consistent between runs.
     */
    private fun pickScenario(daysAgo: Int): NightScenario = when (daysAgo) {
        7 -> NightScenario.GREAT       // Mon
        6 -> NightScenario.GOOD        // Tue
        5 -> NightScenario.POOR        // Wed (Skipped)
        4 -> NightScenario.GREAT       // Thu
        3 -> NightScenario.POOR        // Fri (Skipped)
        2 -> NightScenario.POOR        // Sat (Skipped)
        1 -> NightScenario.GREAT       // Sun
        else -> NightScenario.MODERATE
    }

    private fun buildSleepRecord(
        userId: String,
        sessionDate: LocalDate,
        targetBedtime: LocalTime,
        hrBaseline: Double,
        scenario: NightScenario
    ): SleepRecord {
        // ── Sleep onset: target ± deviation based on scenario ──
        val onsetDeviationMin = when (scenario) {
            NightScenario.GREAT -> rng.nextInt(-10, 15)     // on time or slightly early
            NightScenario.GOOD -> rng.nextInt(0, 30)        // within adherence window
            NightScenario.MODERATE -> rng.nextInt(30, 60)   // slight delay
            NightScenario.POOR -> rng.nextInt(70, 120)      // late
        }

        // Compute actual onset datetime
        // If target is after 8 PM (e.g., 22:00), it's same calendar day
        // If target is after midnight (e.g., 00:30 = 30 min after midnight), it's the next day
        val zoneId = java.time.ZoneId.systemDefault()
        val baseOnset = if (targetBedtime.hour >= 20) {
            sessionDate.atTime(targetBedtime).atZone(zoneId).toInstant()
        } else {
            sessionDate.plusDays(1).atTime(targetBedtime).atZone(zoneId).toInstant()
        }
        val onsetTime = baseOnset.plusSeconds((onsetDeviationMin * 60).toLong())

        // ── Sleep duration: scenario-driven ──
        val durationMinutes = when (scenario) {
            NightScenario.GREAT -> rng.nextInt(450, 510)    // 7.5h – 8.5h
            NightScenario.GOOD -> rng.nextInt(420, 480)     // 7h – 8h
            NightScenario.MODERATE -> rng.nextInt(360, 420) // 6h – 7h
            NightScenario.POOR -> rng.nextInt(270, 360)     // 4.5h – 6h
        }
        val wakeTime = onsetTime.plusSeconds((durationMinutes * 60).toLong())

        // ── Heart rate: closer to baseline = better ──
        val hrDeviation = when (scenario) {
            NightScenario.GREAT -> rng.nextDouble(-2.0, 3.0)
            NightScenario.GOOD -> rng.nextDouble(-3.0, 5.0)
            NightScenario.MODERATE -> rng.nextDouble(-6.0, 8.0)
            NightScenario.POOR -> rng.nextDouble(8.0, 15.0)
        }
        val avgHr = hrBaseline + hrDeviation

        // ── Movement events ──
        val movementCount = when (scenario) {
            NightScenario.GREAT -> rng.nextInt(0, 8)
            NightScenario.GOOD -> rng.nextInt(5, 15)
            NightScenario.MODERATE -> rng.nextInt(15, 35)
            NightScenario.POOR -> rng.nextInt(40, 70)
        }

        // ── Compute component scores per context PDF formula ──
        val durationHours = durationMinutes / 60.0
        val durationScore = when {
            durationHours in 7.0..9.0 -> 100
            durationHours < 7.0 -> ((durationHours / 7.0) * 100).toInt()
            else -> max(0, (100 - ((durationHours - 9) * 20)).toInt())
        }

        val heartRateScore = max(0, (100 - (kotlin.math.abs(avgHr - hrBaseline) * 5)).toInt())

        val movementScore = when {
            movementCount <= 10 -> 100
            else -> max(0, (100 - ((movementCount - 10) * 0.5)).toInt())
        }

        val composite = (durationScore * 0.50 + heartRateScore * 0.30 + movementScore * 0.20).toInt()

        return SleepRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            sessionDate = sessionDate.toString(),
            sleepOnsetTime = onsetTime.toString(),
            wakeTime = wakeTime.toString(),
            sleepDurationMinutes = durationMinutes,
            avgHeartRateBpm = avgHr,
            movementEventCount = movementCount,
            hrBaselineAtScoring = hrBaseline,
            durationScore = durationScore,
            heartRateScore = heartRateScore,
            movementScore = movementScore,
            compositeScore = min(100, max(0, composite)),
            isPartialData = false,
            dataCaptureSuccess = true
        )
    }

    private fun buildRoutineSession(
        userId: String,
        sessionDate: LocalDate,
        targetBedtime: LocalTime,
        completed: Boolean
    ): RoutineSession {
        // Routine starts ~30 min before target bedtime
        val zoneId = java.time.ZoneId.systemDefault()
        val routineStart = if (targetBedtime.hour >= 20) {
            sessionDate.atTime(targetBedtime).minusMinutes(30).atZone(zoneId).toInstant()
        } else {
            sessionDate.plusDays(1).atTime(targetBedtime).minusMinutes(30).atZone(zoneId).toInstant()
        }

        return RoutineSession(
            id = UUID.randomUUID().toString(),
            userId = userId,
            routineConfigId = null,  // OK for seed data; Person A will set in real flow
            sessionDate = sessionDate.toString(),
            startTimestamp = routineStart.toString(),
            completionTimestamp = if (completed) routineStart.plusSeconds(25 * 60).toString() else null,
            isCompleted = completed,
            streakAtCompletion = if (completed) rng.nextInt(1, 5) else null,
            multiplierApplied = if (completed) 1.0 else null,
            tokensEarned = if (completed) 50 else null,
            xpEarned = if (completed) 100 else null
        )
    }

    private enum class NightScenario(val routineCompleted: Boolean) {
        GREAT(true),
        GOOD(true),
        MODERATE(true),
        POOR(false)   // routine skipped → poor sleep
    }
}