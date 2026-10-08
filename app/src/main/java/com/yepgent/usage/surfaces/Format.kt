package com.yepgent.usage.surfaces

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mono's lower-case, 24-hour wording, shared by the app, notifications and widget. */
object Format {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayClock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "15:40" within a day, else "mon 09:00". */
    fun resetAt(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val formatter = if (at.toEpochMilli() - now.toEpochMilli() < 24 * 3_600_000L) clock else dayClock
        return formatter.format(at.atZone(zone)).lowercase(Locale.US)
    }

    /** "45m", "2h14m", "3d04h". */
    fun span(ms: Long): String {
        val mins = maxOf(1, Math.round(ms / 60_000.0).toInt())
        if (mins < 60) return "${mins}m"
        val hours = mins / 60
        if (hours < 48) return "${hours}h%02dm".format(mins % 60)
        return "${hours / 24}d%02dh".format(hours % 24)
    }

    /** Status-bar glyph: the percent, or "!!" once a limit is reached. */
    fun glyph(percent: Int): String = if (percent >= 100) "!!" else percent.coerceAtLeast(0).toString()
}
