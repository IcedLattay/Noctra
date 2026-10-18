package com.noctra.app.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * DbTime — the one place raw DB timestamp strings get parsed.
 *
 * WHY: supabase-kt models decode timestamptz columns as plain Strings in
 * PostgREST wire format ("2026-10-17T18:56:00+00:00"). java.time's
 * Instant.parse is strict-Z-only on pre-Android-14 runtimes (OpenJDK 11
 * libs), so offset strings throw DateTimeParseException there while
 * parsing fine on Android 14+. That device split bricked the companion
 * screen for old-runtime users with an abandoned (PENDING) night: the
 * audit's unguarded Instant.parse threw, and the failure surfaced as the
 * generic "Couldn't load your data." toast on every open.
 *
 * OffsetDateTime.parse has always accepted offsets on every runtime, and
 * the LocalDateTime leg covers the app's own legacy write format
 * (LocalDateTime.toString() pushed into timestamptz). Anything
 * unparseable returns null — callers must degrade (skip the rule, show
 * nothing), never crash.
 */
object DbTime {

    fun parseInstantOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        // Fast path: Z strings (and offsets, on lenient runtimes).
        runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
        // PostgREST timestamptz: ISO with offset, valid on every runtime.
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()?.let { return it }
        // Space-separated forms (psql text output pasted around).
        runCatching { OffsetDateTime.parse(raw.replace(' ', 'T')).toInstant() }.getOrNull()?.let { return it }
        // Legacy no-offset stamps: the app wrote these as UTC-labeled local times.
        runCatching {
            LocalDateTime.parse(raw.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
        }.getOrNull()?.let { return it }
        return null
    }
}
