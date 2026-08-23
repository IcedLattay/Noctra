package com.noctra.app.domain.usecase

import com.noctra.app.data.model.RewardLedger
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.SleepSyncManager
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * ReconciliationAuditUseCase
 *
 * The central brain of the Noctra Audit System. Iterates through the gap window,
 * syncs data, determines success/failure, and calculates final streaks.
 */
class ReconciliationAuditUseCase(
    private val auditRangeProvider: AuditRangeProvider = AuditRangeProvider(),
    private val sessionRepository: RoutineSessionRepository = RoutineSessionRepository(),
    private val sleepSyncManager: SleepSyncManager = SleepSyncManager(),
    private val sleepRecordRepository: SleepRecordRepository = SleepRecordRepository(),
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository()
) {

    suspend fun execute(userId: String) {
        // 1. Housekeeping: Finalize ancient pendings
        sessionRepository.finalizeOldPendingSessions(userId)

        // 2. Identify the gap
        val auditDates = auditRangeProvider.getAuditRange(userId)
        if (auditDates.isEmpty()) return

        val initialLedger = rewardRepository.getRewardLedger(userId) ?: return
        var currentLedger = initialLedger
        
        // Handle big gaps (>14 days) before starting loop
        val lastSessionDate = initialLedger.lastSessionDate?.let { LocalDate.parse(it) }
        if (lastSessionDate != null && ChronoUnit.DAYS.between(lastSessionDate, LocalDate.now()) > 14) {
            currentLedger = currentLedger.copy(
                currentStreak = 0,
                hasFirstMiss = true // Set warning immediately for long absence
            )
        }

        // 3. Sequential Audit Loop
        for (date in auditDates) {
            val dateString = date.toString()
            val session = sessionRepository.getSessionsByDate(userId, dateString).firstOrNull()
            
            // A. SYNC: Try to get/update sleep data for this night
            // Note: In a real app, this would query Health Connect via SleepSyncManager
            // For now, we assume we check the local SleepRecord table or perform a real fetch.
            
            // B. VERDICT: Determine if the day was a success
            val finalStatus = determineStatus(userId, date, session)
            
            // Update the session in DB if status changed
            if (session != null && session.status != finalStatus) {
                sessionRepository.insertSession(session.copy(status = finalStatus))
            } else if (session == null && date.isBefore(LocalDate.now())) {
                // Create a missed record for empty days in the past
                sessionRepository.recordMissedSession(userId, dateString)
            }

            // C. AUDIT: Apply Game Rules
            currentLedger = applyPenaltyChain(currentLedger, finalStatus, date)
        }

        // 4. Final Save
        rewardRepository.updateRewardLedger(currentLedger.copy(
            lastUpdated = OffsetDateTime.now().toString()
        ))
    }

    private suspend fun determineStatus(userId: String, date: LocalDate, session: RoutineSession?): String {
        if (session?.status == "COMPLETED") return "COMPLETED"
        if (session == null && date.isEqual(LocalDate.now())) return "PENDING" // Today isn't missed yet
        if (session == null) return "MISSED"

        // If PENDING, check for sleep verification
        if (session.status == "PENDING") {
            val sleepRecord = sleepRecordRepository.getRecordsInRange(userId, date.toString(), date.toString()).firstOrNull()
            
            if (sleepRecord != null) {
                // The 1-Hour Rule
                val routineStart = Instant.parse(session.startTimestamp)
                val sleepOnset = sleepRecord.sleepOnsetTime?.let { Instant.parse(it) }
                
                if (sleepOnset != null) {
                    val diffMinutes = ChronoUnit.MINUTES.between(routineStart, sleepOnset)
                    if (diffMinutes in 0..60) {
                        return "COMPLETED"
                    }
                }
            }

            // Grace Period: If no sleep found and day is > 24h old
            val dayAfter = date.plusDays(1)
            if (LocalDate.now().isAfter(dayAfter)) {
                return "MISSED"
            }
        }

        return session.status
    }

    private fun applyPenaltyChain(ledger: RewardLedger, status: String, date: LocalDate): RewardLedger {
        if (status == "PENDING") return ledger // Do nothing for pendings

        return if (status == "COMPLETED") {
            ledger.copy(
                currentStreak = ledger.currentStreak + 1,
                longestStreak = maxOf(ledger.longestStreak, ledger.currentStreak + 1),
                hasFirstMiss = false,
                lastSessionDate = date.toString()
            )
        } else {
            // MISSED
            if (ledger.hasFirstMiss) {
                // Consecutive Miss
                ledger.copy(
                    currentStreak = 0,
                    hasFirstMiss = true, // Maintain warning
                    lastSessionDate = date.toString()
                )
            } else {
                // First Miss (Warning)
                ledger.copy(
                    hasFirstMiss = true,
                    lastSessionDate = date.toString()
                )
            }
        }
    }
}
