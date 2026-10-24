package com.noctra.app.domain.usecase

import com.noctra.app.data.model.RewardLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The streak chain arithmetic used to run on the wall clock
 * (LocalDate.now()), which reset the streak for every routine finished
 * after midnight: a session dated Oct 16 completed at 00:17 on Oct 17
 * compared last=Oct 15 against "yesterday = Oct 16", saw a fake gap, and
 * restarted at 1 instead of chaining to 2 — and stamped
 * last_session_date with Oct 17, mis-anchoring the audit too. The
 * night's own date is now the anchor; these cases pin that down.
 */
class RewardCalculationUseCaseTest {

    private val oct15: LocalDate = LocalDate.of(2026, 10, 15)
    private val oct16: LocalDate = LocalDate.of(2026, 10, 16)
    private val oct17: LocalDate = LocalDate.of(2026, 10, 17)
    private val oct20: LocalDate = LocalDate.of(2026, 10, 20)
    private val oct21: LocalDate = LocalDate.of(2026, 10, 21)
    private val oct22: LocalDate = LocalDate.of(2026, 10, 22)

    @Test
    fun `after-midnight completion still chains the previous night`() {
        // Night Oct 16, finished 00:17 on Oct 17 — anchor is the NIGHT.
        assertEquals(2, RewardCalculationUseCase.computeNewStreak(1, oct15, oct16))
    }

    @Test
    fun `chaining continues across midnight into the next night`() {
        // Night Oct 17 finished after midnight, last resolved night Oct 16.
        assertEquals(3, RewardCalculationUseCase.computeNewStreak(2, oct16, oct17))
    }

    @Test
    fun `same night never double counts`() {
        assertEquals(3, RewardCalculationUseCase.computeNewStreak(3, oct16, oct16))
    }

    @Test
    fun `a genuine gap restarts at one`() {
        assertEquals(1, RewardCalculationUseCase.computeNewStreak(5, oct15, oct17))
    }

    @Test
    fun `first completion starts at one`() {
        assertEquals(1, RewardCalculationUseCase.computeNewStreak(0, null, oct16))
    }

    @Test
    fun `in-flight night suspends the chain instead of breaking it`() {
        // Last credited Oct 15, Oct 16 still pending its grace verdict,
        // completing the Oct 17 night: count tonight, leave the pending
        // slot open for the audit to resolve later.
        assertEquals(3, RewardCalculationUseCase.computeNewStreak(2, oct15, oct17, gapNightsAllPending = true))
    }

    @Test
    fun `suspended chain still starts fresh when nothing precedes it`() {
        assertEquals(1, RewardCalculationUseCase.computeNewStreak(0, null, oct16, gapNightsAllPending = true))
    }

    @Test
    fun `a gap that is not in flight still resets`() {
        assertEquals(1, RewardCalculationUseCase.computeNewStreak(4, oct15, oct17, gapNightsAllPending = false))
    }

    // ── applyMissPenalty: shared by the audit AND the morning checker ────
    // The checker used to run a private copy that never moved the anchor,
    // so a single warning-miss broke the streak anyway through the gap
    // rule. These pin the unified behavior.

    private fun ledger(streak: Int, last: LocalDate?, firstMiss: Boolean) = RewardLedger(
        userId = "test-user",
        currentStreak = streak,
        hasFirstMiss = firstMiss,
        lastSessionDate = last?.toString(),
        lastUpdated = "2026-10-25T00:00:00+00:00"
    )

    @Test
    fun `first miss is a warning - streak kept and anchor hops onto the missed night`() {
        val out = RewardCalculationUseCase.applyMissPenalty(ledger(2, oct20, false), oct21)
        assertEquals(2, out.currentStreak)
        assertTrue(out.hasFirstMiss)
        assertEquals(oct21.toString(), out.lastSessionDate)
    }

    @Test
    fun `anchor hop lets the next completion chain instead of resetting`() {
        // The entire divergence in one test: with the anchor ON the missed
        // night, completing Oct 22 chains to +1 instead of restarting.
        val afterMiss = RewardCalculationUseCase.applyMissPenalty(ledger(2, oct20, false), oct21)
        val anchor = LocalDate.parse(afterMiss.lastSessionDate)
        assertEquals(3, RewardCalculationUseCase.computeNewStreak(afterMiss.currentStreak, anchor, oct22))
    }

    @Test
    fun `second consecutive miss resets the streak but keeps the warning`() {
        val out = RewardCalculationUseCase.applyMissPenalty(ledger(3, oct21, true), oct22)
        assertEquals(0, out.currentStreak)
        assertTrue(out.hasFirstMiss)
        assertEquals(oct22.toString(), out.lastSessionDate)
    }

    @Test
    fun `anchor never moves backward for an older miss`() {
        val out = RewardCalculationUseCase.applyMissPenalty(ledger(4, oct22, false), oct21)
        assertEquals(oct22.toString(), out.lastSessionDate)
        assertEquals(4, out.currentStreak)
    }
}
