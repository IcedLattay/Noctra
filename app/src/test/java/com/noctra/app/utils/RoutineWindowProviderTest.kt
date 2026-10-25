package com.noctra.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RoutineWindowProviderTest {

    private val oct21 = LocalDate.of(2026, 10, 21)
    private val oct22 = LocalDate.of(2026, 10, 22)
    private val oct23 = LocalDate.of(2026, 10, 23)

    @Test
    fun `session dates before 4am attribute to the previous day`() {
        assertEquals(oct22, RoutineWindowProvider.resolveRoutineSessionDate(LocalDateTime.of(2026, 10, 23, 0, 1)))
        assertEquals(oct22, RoutineWindowProvider.resolveRoutineSessionDate(LocalDateTime.of(2026, 10, 23, 3, 59)))
    }

    @Test
    fun `session dates from 4am attribute to the same day`() {
        assertEquals(oct23, RoutineWindowProvider.resolveRoutineSessionDate(LocalDateTime.of(2026, 10, 23, 4, 0)))
        assertEquals(oct23, RoutineWindowProvider.resolveRoutineSessionDate(LocalDateTime.of(2026, 10, 23, 22, 0)))
    }

    @Test
    fun `previous date is NOT closed between midnight and 4am - post-midnight starts still back-date into it`() {
        // The regression window: the audit used to judge an empty
        // "yesterday" missed right here, planting a phantom MISSED row
        // (and a streak penalty) before the user's real, back-dated
        // session could exist — producing duplicate rows per date.
        val now = LocalDateTime.of(2026, 10, 23, 0, 40)
        assertFalse(RoutineWindowProvider.isSessionDateClosed(oct22, now))
    }

    @Test
    fun `previous date IS closed once the 4am anchor rolls past`() {
        assertTrue(RoutineWindowProvider.isSessionDateClosed(oct22, LocalDateTime.of(2026, 10, 23, 4, 0)))
        assertTrue(RoutineWindowProvider.isSessionDateClosed(oct22, LocalDateTime.of(2026, 10, 23, 9, 30)))
    }

    @Test
    fun `older dates stay closed even inside the midnight-4am window`() {
        assertTrue(RoutineWindowProvider.isSessionDateClosed(oct21, LocalDateTime.of(2026, 10, 23, 0, 40)))
    }

    @Test
    fun `today is never closed`() {
        assertFalse(RoutineWindowProvider.isSessionDateClosed(oct23, LocalDateTime.of(2026, 10, 23, 23, 0)))
        assertFalse(RoutineWindowProvider.isSessionDateClosed(oct23, LocalDateTime.of(2026, 10, 23, 0, 40)))
    }

    // --- missJudgementDates: the checker's range walk ---

    @Test
    fun `walk covers every night between anchor and yesterday - the skipped-run gap heals`() {
        // The Oct-22 regression: anchor 10-21, a single missed 9:30 run
        // left 10-22 unjudged while 10-23 got judged. Judging "yesterday"
        // only never revisits it; the walk must include it.
        assertEquals(
            listOf(oct22, oct23, LocalDate.of(2026, 10, 24)),
            RoutineWindowProvider.missJudgementDates(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 25))
        )
    }

    @Test
    fun `walk is empty when the anchor already sits on yesterday or later`() {
        val today = LocalDate.of(2026, 10, 25)
        assertTrue(RoutineWindowProvider.missJudgementDates(LocalDate.of(2026, 10, 24), today).isEmpty())
        assertTrue(RoutineWindowProvider.missJudgementDates(LocalDate.of(2026, 10, 25), today).isEmpty())
    }

    @Test
    fun `null anchor judges only yesterday - fresh account fallback`() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 24)),
            RoutineWindowProvider.missJudgementDates(null, LocalDate.of(2026, 10, 25))
        )
    }

    @Test
    fun `walk floor holds at 14 days so backfill keeps the older partition`() {
        // anchor far behind: AuditBackfillWorker owns [onboarding, today-14)
        val dates = RoutineWindowProvider.missJudgementDates(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 10, 11), dates.first())
        assertEquals(LocalDate.of(2026, 10, 24), dates.last())
        assertEquals(14, dates.size)
        // ordered, no gaps, no duplicates
        assertEquals(dates.distinct(), dates)
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `anchor exactly at the floor boundary still yields the full 14 nights`() {
        val today = LocalDate.of(2026, 10, 25)
        val dates = RoutineWindowProvider.missJudgementDates(today.minusDays(15), today)
        assertEquals(LocalDate.of(2026, 10, 11), dates.first())
        assertEquals(14, dates.size)
    }
}
