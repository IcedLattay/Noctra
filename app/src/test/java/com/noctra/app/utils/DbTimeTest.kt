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
}
