package com.noctra.app.domain.usecase

import org.junit.Assert.assertEquals
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
}
