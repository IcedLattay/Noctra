package com.noctra.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DbTimeTest {

    @Test
    fun `postgrest offset string parses`() {
        // The exact wire format that threw on pre-Android-14 runtimes
        // (strict-Z Instant.parse) and bricked the companion screen.
        val expected = LocalDateTime.parse("2026-10-17T18:56:00").toInstant(ZoneOffset.UTC)
        assertEquals(expected, DbTime.parseInstantOrNull("2026-10-17T18:56:00+00:00"))
    }

    @Test
    fun `zulu string parses`() {
        assertEquals(
            Instant.parse("2026-10-17T18:56:00Z"),
            DbTime.parseInstantOrNull("2026-10-17T18:56:00Z")
        )
    }

    @Test
    fun `nonzero offset converts correctly`() {
        assertEquals(
            Instant.parse("2026-10-17T18:56:00Z"),
            DbTime.parseInstantOrNull("2026-10-17T20:56:00+02:00")
        )
    }

    @Test
    fun `legacy local datetime stamp parses as utc`() {
        // The app's own write format: LocalDateTime.toString() pushed into
        // a timestamptz column (the "00:17 quirk").
        assertEquals(
            Instant.parse("2026-10-17T00:17:00Z"),
            DbTime.parseInstantOrNull("2026-10-17T00:17:00")
        )
    }

    @Test
    fun `space separated offset form parses`() {
        val expected = LocalDateTime.parse("2026-10-17T18:56:00").toInstant(ZoneOffset.UTC)
        assertEquals(expected, DbTime.parseInstantOrNull("2026-10-17 18:56:00+00:00"))
    }

    @Test
    fun `garbage and blanks return null`() {
        assertNull(DbTime.parseInstantOrNull("not a time"))
        assertNull(DbTime.parseInstantOrNull(""))
        assertNull(DbTime.parseInstantOrNull("   "))
        assertNull(DbTime.parseInstantOrNull(null))
    }

    @Test
    fun `session start stamp is local time mislabeled utc`() {
        // The app writes LocalDateTime text into a timestamptz column;
        // PostgREST reads it back with a bogus +00. Recovery = strip the
        // zone and anchor in the system zone.
        val expected = LocalDateTime.parse("2026-10-18T21:18:46.09551")
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        assertEquals(
            expected,
            DbTime.parseSessionInstantOrNull("2026-10-18 21:18:46.09551+00")
        )
    }

    @Test
    fun `session stamp with plain iso form and no fraction parses`() {
        val expected = LocalDateTime.parse("2026-10-17T00:17:05")
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        assertEquals(
            expected,
            DbTime.parseSessionInstantOrNull("2026-10-17T00:17:05+00:00")
        )
    }

    @Test
    fun `session stamp gap against true utc sleep recovers the real window`() {
        // Frame reconciliation, timezone-independent: whatever the device
        // zone, the recovered session instant must differ from its raw
        // (literal-UTC) reading by exactly the system offset — that delta
        // is what the 1-hour rule was missing.
        val rawStart = DbTime.parseInstantOrNull("2026-10-18T21:18:46+00")
        val fixedStart = DbTime.parseSessionInstantOrNull("2026-10-18T21:18:46+00")
        val zone = java.time.ZoneId.systemDefault()
        val expectedDelta = java.time.Duration.ofSeconds(
            zone.rules.getOffset(java.time.LocalDateTime.parse("2026-10-18T21:18:46")).totalSeconds.toLong()
        )
        assertEquals(expectedDelta, java.time.Duration.between(fixedStart, rawStart))
    }

    @Test
    fun `session stamp garbage returns null`() {
        assertNull(DbTime.parseSessionInstantOrNull("not a time"))
        assertNull(DbTime.parseSessionInstantOrNull(null))
    }
}
