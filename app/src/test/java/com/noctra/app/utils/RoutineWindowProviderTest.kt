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
}
