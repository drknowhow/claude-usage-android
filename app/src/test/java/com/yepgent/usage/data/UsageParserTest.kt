package com.yepgent.usage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageParserTest {

    @Test
    fun webShapeReadsWindowsAndSkipsNulls() {
        val json = """
            {"five_hour":{"utilization":61.6,"resets_at":"2026-10-08T15:40:00.123456+00:00"},
             "seven_day":{"utilization":41.0,"resets_at":"2026-10-12T09:00:00+00:00"},
             "seven_day_oauth_apps":null,
             "seven_day_opus":{"utilization":78,"resets_at":"2026-10-12T09:00:00Z"},
             "seven_day_sonnet":null}
        """.trimIndent()

        val limits = UsageParser.parse(json)

        assertEquals(listOf("session", "weekly_opus", "weekly_all"), limits.map { it.key })
        assertEquals(62, limits[0].percent)
        assertEquals(Group.SESSION, limits[0].group)
        assertEquals(Instant.parse("2026-10-08T15:40:00.123456Z"), limits[0].resetsAt)
        assertEquals(Instant.parse("2026-10-12T09:00:00Z"), limits[1].resetsAt)
    }

    @Test
    fun limitsArrayWinsOverLegacyWindows() {
        val json = """
            {"five_hour":{"utilization":5},
             "limits":[
               {"kind":"weekly_all","percent":30,"resets_at":"2026-10-12T09:00:00Z"},
               {"kind":"session","percent":12.4},
               {"kind":"weekly_scoped","percent":55,"scope":{"model":{"display_name":"Opus"}}},
               {"kind":"weekly_scoped","percent":20,"scope":{"surface":"Claude Code"}},
               {"kind":"weekly_all"}
             ]}
        """.trimIndent()

        val limits = UsageParser.parse(json)

        assertEquals(listOf("Session", "Opus", "All models", "Claude Code"), limits.map { it.label })
        assertEquals(12, limits[0].percent)
        assertEquals("weekly_scoped:Opus", limits[1].key)
        assertNull(limits[0].resetsAt)
    }

    @Test
    fun explicitGroupOverridesKind() {
        val limits = UsageParser.parse("""{"limits":[{"kind":"burst","group":"session","percent":9}]}""")

        assertEquals(Group.SESSION, limits.single().group)
        assertEquals("Burst", limits.single().label)
    }

    @Test
    fun malformedInputYieldsNoLimits() {
        assertTrue(UsageParser.parse("not json").isEmpty())
        assertTrue(UsageParser.parse("[1,2]").isEmpty())
        assertTrue(UsageParser.parse("""{"five_hour":{"utilization":"62"}}""").isEmpty())
        assertNull(UsageParser.parse("""{"five_hour":{"utilization":1,"resets_at":"soon"}}""").single().resetsAt)
    }

    @Test
    fun headlineTakesHighestWeekly() {
        val h = UsageParser.parse(
            """{"five_hour":{"utilization":10},"seven_day":{"utilization":41},"seven_day_opus":{"utilization":78}}""",
        ).headline()

        assertEquals(10, h.session?.percent)
        assertEquals("opus weekly", h.weekly?.title)
    }
}
