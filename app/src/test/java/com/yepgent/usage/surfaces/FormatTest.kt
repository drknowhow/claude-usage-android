package com.yepgent.usage.surfaces

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FormatTest {

    @Test
    fun spanIsCompact() {
        assertEquals("1m", Format.span(10_000))
        assertEquals("45m", Format.span(45 * 60_000L))
        assertEquals("2h14m", Format.span((2 * 60 + 14) * 60_000L))
        assertEquals("3d04h", Format.span((76 * 60) * 60_000L))
    }

    @Test
    fun glyphShowsPercentOrBangs() {
        assertEquals("0", Format.glyph(0))
        assertEquals("7", Format.glyph(7))
        assertEquals("62", Format.glyph(62))
        assertEquals("!!", Format.glyph(100))
        assertEquals("!!", Format.glyph(104))
    }

    @Test
    fun resetAtShowsDayOnlyBeyondADay() {
        val now = Instant.parse("2026-10-08T09:00:00Z")

        assertEquals("15:40", Format.resetAt(Instant.parse("2026-10-08T15:40:00Z"), now, ZoneOffset.UTC))
        assertEquals("mon 09:00", Format.resetAt(Instant.parse("2026-10-12T09:00:00Z"), now, ZoneOffset.UTC))
    }
}
