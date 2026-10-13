package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * AuditRangeProvider
 *
 * Identifies the window of time that needs to be reviewed by the Reconciliation Audit.
 */
class AuditRangeProvider(
    private val sessionRepository: RoutineSessionRepository = RoutineSessionRepository(),
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository()
) {

    /**
     * Finds the starting point for the audit based on the Priority Search:
     * 1. Oldest PENDING record.
     * 2. Day after last_session_date.
     * 3. Max lookback of 14 days.
     */
    suspend fun getAuditRange(userId: String): List<LocalDate> {
        val today = LocalDate.now()
        val ledger = rewardRepository.getRewardLedger(userId)
        val oldestPending = sessionRepository.getOldestPendingSession(userId)

        // 1 & 2: Determine initial start date
        val candidateStartDate = when {
            oldestPending != null -> {
                LocalDate.parse(oldestPending.sessionDate)
            }
            ledger?.lastSessionDate != null -> {
                LocalDate.parse(ledger.lastSessionDate).plusDays(1)
            }
            else -> today // New user
        }

        // 3: Apply the Universal 14-Day Cap
        val fourteenDaysAgo = today.minusDays(14)
        val finalStartDate = if (candidateStartDate.isBefore(fourteenDaysAgo)) {
            fourteenDaysAgo
        } else {
            candidateStartDate
        }

        // Generate the list of dates from Start to Today (inclusive)
        val daysBetween = ChronoUnit.DAYS.between(finalStartDate, today).toInt()
        return (0..daysBetween).map { finalStartDate.plusDays(it.toLong()) }
    }
}
