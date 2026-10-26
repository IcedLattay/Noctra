package com.noctra.app.domain.usecase

import android.util.Log
import com.noctra.app.data.model.RewardLedger
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.repository.AppLogRepository
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.SleepSyncManager
import com.noctra.app.utils.DbTime
import com.noctra.app.utils.RoutineWindowProvider
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
        // Admin feed: the in-app (synchronous) check. Everything this run
        // writes — housekeeping flips, sleep pulls, verdicts, ledger moves —
        // nests under this id so the panel shows it as one thread.
        val checkLogId = AppLogRepository.logCheck(userId = userId, trigger = "synchronous")

        // 1. Housekeeping: Finalize ancient pendings
        sessionRepository.finalizeOldPendingSessions(userId, checkLogId)

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

        // 2b. Sleep refresh: sweep the trailing 14-day cap for nights
        // missing sleep rows or flagged partial, and pull each from
        // Health Connect. Verdicts below then judge on fresh data.
        // Skips nights with final rows — nothing to heal.
        refreshSleepWindow(userId, checkLogId)

        // 3. Sequential Audit Loop
        for (date in auditDates) {
            currentLedger = auditDate(
                userId = userId,
                date = date,
                currentLedger = currentLedger,
                syncSleep = true,
                logParentId = checkLogId
            )
            // Persist after every date. Verdicts are transition-gated (see
            // auditDate), so a crash after a row write but before the ledger
            // save would make the rerun see "no transition" and silently drop
            // that night's verdict — per-date saves shrink that window to
            // milliseconds.
            rewardRepository.updateRewardLedger(
                currentLedger.copy(lastUpdated = OffsetDateTime.now().toString()),
                logParentId = checkLogId
            )
        }

        // 4. Final Save (covers a range that changed nothing, plus the
        //    >14-day absence reset written before the loop)
        rewardRepository.updateRewardLedger(
            currentLedger.copy(lastUpdated = OffsetDateTime.now().toString()),
            logParentId = checkLogId
        )
    }

    /**
     * Step 1–2 of the audit: for each night in the audit-anchored window
     * missing a sleep row or flagged partial, pull Health Connect once.
     * Found data upserts (or finalizes partials); absent data writes
     * nothing — no empty sleep rows are ever fabricated.
     *
     * Window = max(audit anchor, today−14) → today. The anchor (last
     * session date, seeded at onboarding completion) is the journey
     * start: fresh accounts must NOT backfill pre-signup nights —
     * that flooded day-1 accounts with up to 14 ghost rows, froze XP
     * on nights the journey never covered, and paid it out at the
     * next morning popup as a windfall.
     */
    private suspend fun refreshSleepWindow(userId: String, logParentId: String? = null) {
        val today = LocalDate.now()
        val anchor = rewardRepository.getRewardLedger(userId)?.lastSessionDate
            ?.let { LocalDate.parse(it) }
        val uncappedStart = maxOf(anchor ?: today.minusDays(14), today.minusDays(14))
        val start = if (uncappedStart.isAfter(today)) today else uncappedStart
        Log.d("ReconciliationAudit", "Sleep refresh window: $start → $today (anchor=$anchor)")
        val existingByDate = sleepRecordRepository
            .getRecordsInRange(userId, start.toString(), today.toString())
            .associateBy { it.sessionDate }
        var date = start
        while (!date.isAfter(today)) {
            val dateStr = date.toString()
            val record = existingByDate[dateStr]
            val needsPull = record == null || record.isPartialData
            if (needsPull) {
                try {
                    sleepSyncManager.syncSessionDate(userId, date, logParentId = logParentId)
                } catch (e: Exception) {
                    Log.w("ReconciliationAudit", "Sleep refresh failed for $dateStr", e)
                }
            }
            date = date.plusDays(1)
        }
    }

    /**
     * Audits one date: sleep sync (optional) → verdict → write/update row →
     * penalty fold. Shared by the open-path audit and the backfill worker
     * so both apply identical rules.
     */
    suspend fun auditDate(
        userId: String,
        date: LocalDate,
        currentLedger: RewardLedger,
        syncSleep: Boolean,
        logParentId: String? = null
    ): RewardLedger {
        val dateString = date.toString()
        val session = sessionRepository.getSessionsByDate(userId, dateString).firstOrNull()

        // A. SYNC: Fetch/refresh this night's sleep data from Health Connect.
        // The syncer handles aggregation, scoring, and idempotent upsert.
        // The verdict below always re-runs, even when the sync finds nothing new.
        // Skipped by the backfill worker (ancient HC data is gone; judge stored rows).
        if (syncSleep) {
            syncSleepForDate(userId, date, logParentId)
        }

        // B. VERDICT: Determine if the day was a success
        val finalStatus = determineStatus(userId, date, session)

        // The ledger only sees verdicts that CHANGE stored state. A row
        // already COMPLETED was credited when the app finished it in-app —
        // and the audit's range gets dragged back over such dates whenever
        // an older night is still pending, so re-applying would double-count.
        // An already-MISSED row must never re-penalize either (a repeat of
        // an old miss could zero a streak a second time). Transitions
        // (pending→completed, no-row→missed, pending→missed) are the only
        // nights the counter hasn't seen yet.
        val verdictChangedState = when {
            session == null -> {
                // Create a missed record for empty days in the past — but
                // only nights whose 4 AM attribution anchor has rolled
                // past. Before it, the previous date is still a LIVE
                // night (post-midnight starts back-date into it): judging
                // it now plants a phantom MISSED row the real session then
                // duplicates, plus a streak penalty for a night not yet
                // performed. (See RoutineWindowProvider.isSessionDateClosed.)
                if (RoutineWindowProvider.isSessionDateClosed(date)) {
                    sessionRepository.recordMissedSession(
                        userId = userId,
                        sessionDate = dateString,
                        logParentId = logParentId
                    )
                    true
                } else {
                    false
                }
            }
            session.status != finalStatus -> {
                sessionRepository.insertSession(session.copy(status = finalStatus))
                // PENDING -> COMPLETED/MISSED transition, spelled out for
                // the admin panel.
                sessionRepository.logStatusChanged(
                    userId, dateString, finalStatus, logParentId
                )
                true
            }
            else -> false
        }

        // C. AUDIT: Apply Game Rules — transitions only
        if (!verdictChangedState) return currentLedger
        return applyPenaltyChain(currentLedger, finalStatus, date)
    }

    /**
     * Syncs this date's sleep data from Health Connect before the verdict runs.
     * Skipped when the anchor window hasn't opened yet (today's window starts
     * tomorrow at 4:00 AM). All outcomes are non-fatal: the verdict still
     * evaluates on whatever data is stored.
     */
    private suspend fun syncSleepForDate(
        userId: String,
        date: LocalDate,
        logParentId: String? = null
    ) {
        val windowStart = sleepSyncManager.getWakeUpAnchorWindow(date).first
        if (Instant.now() < windowStart) return

        when (val result = sleepSyncManager.syncSessionDate(
            userId, date, logParentId = logParentId
        )) {
            is SleepSyncManager.SyncResult.Synced -> Unit // record written/refreshed
            is SleepSyncManager.SyncResult.NoData -> Unit // nothing new; verdict still re-runs
            is SleepSyncManager.SyncResult.PermissionDenied -> Unit // no sleep access; judge on stored data
            is SleepSyncManager.SyncResult.HealthConnectUnavailable -> Unit // judge on stored data
            is SleepSyncManager.SyncResult.Failed ->
                Log.w("ReconciliationAudit", "Sleep sync failed for $date", result.error)
        }
    }

    private suspend fun determineStatus(userId: String, date: LocalDate, session: RoutineSession?): String {
        if (session?.status == "COMPLETED") return "COMPLETED"
        // Not missed yet: today, plus the previous date while its 4 AM
        // attribution anchor is still ahead (post-midnight starts back-date
        // into it — see RoutineWindowProvider.isSessionDateClosed).
        if (session == null && !RoutineWindowProvider.isSessionDateClosed(date)) return "PENDING"
        if (session == null) return "MISSED"

        // If PENDING, check for sleep verification
        if (session.status == "PENDING") {
            val sleepRecord = sleepRecordRepository.getRecordsInRange(userId, date.toString(), date.toString()).firstOrNull()
            
            if (sleepRecord != null) {
                // The 1-Hour Rule
                // Lenient parse (DbTime): PostgREST returns offset strings
                // ("...+00:00") and strict Instant.parse throws on pre-Android-14
                // runtimes — an unparseable stamp must skip the rule, never
                // crash the audit (it bricked the companion screen for
                // old-runtime users with an abandoned night).
                // Session stamps are LOCAL wall-time mislabeled "+00", while
                // sleep stamps are true UTC (see DbTime.parseSessionInstantOrNull).
                // Parsing both the same way made this gap 8h too negative at
                // UTC+8 — a 21:18 start asleep at 21:56 read as -422 min instead
                // of +38, so sleep verification could never pass east of UTC.
                val routineStart = DbTime.parseSessionInstantOrNull(session.startTimestamp)
                val sleepOnset = DbTime.parseInstantOrNull(sleepRecord.sleepOnsetTime)
                
                if (routineStart != null && sleepOnset != null) {
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

        // The watermark never moves backward: an older night can resolve
        // (sleep-verified, or grace-expired) AFTER newer nights were already
        // credited in-app. Rewinding last_session_date would hand the next
        // completion a false gap and reset the streak — the very bug the
        // completion-side chain fix just closed.
        val previousWatermark = ledger.lastSessionDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val watermark = maxOf(previousWatermark ?: date, date)

        return if (status == "COMPLETED") {
            ledger.copy(
                currentStreak = ledger.currentStreak + 1,
                longestStreak = maxOf(ledger.longestStreak, ledger.currentStreak + 1),
                hasFirstMiss = false,
                lastSessionDate = watermark.toString()
            )
        } else {
            // MISSED — folded by the SAME shared chain the morning checker
            // calls, so both judgers produce identical ledgers (they used
            // to diverge on whether the anchor hops over the missed night).
            RewardCalculationUseCase.applyMissPenalty(ledger, date)
        }
    }
}
