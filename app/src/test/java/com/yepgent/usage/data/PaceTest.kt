package com.yepgent.usage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PaceTest {
    private val min = 60_000L

    @Test
    fun steadyBurnPredictsTimeToFull() {
        // 10 points per hour: 60% at t=60m leaves 4h to 100%.
        val samples = listOf(Sample(0, 50), Sample(30 * min, 55), Sample(60 * min, 60))
        val f = Pace.forecast(samples, Instant.ofEpochMilli(3 * 60 * min), now = 60 * min)!!

        assertEquals(4 * 60 * min, f.etaMs)
        assertEquals(false, f.beforeReset)
    }

    @Test
    fun hitsBeforeResetIsFlagged() {
        val samples = listOf(Sample(0, 60), Sample(15 * min, 70), Sample(30 * min, 80))
        val f = Pace.forecast(samples, Instant.ofEpochMilli(5 * 60 * min), now = 30 * min)!!

        assertEquals(true, f.beforeReset)
    }

    @Test
    fun tooLittleOrFlatDataGivesNoForecast() {
        assertNull(Pace.forecast(listOf(Sample(0, 1), Sample(20 * min, 2)), null, 20 * min))
        assertNull(Pace.forecast(listOf(Sample(0, 1), Sample(2 * min, 2), Sample(4 * min, 3)), null, 4 * min))
        assertNull(Pace.forecast(listOf(Sample(0, 40), Sample(20 * min, 40), Sample(40 * min, 40)), null, 40 * min))
    }

    @Test
    fun windowStartsAfterTheLastReset() {
        val samples = listOf(Sample(0, 90), Sample(1, 95), Sample(2, 3), Sample(3, 5))

        assertEquals(listOf(Sample(2, 3), Sample(3, 5)), Pace.currentWindow(samples))
    }

    @Test
    fun appendKeepsOnePointPerMinuteAndSixHours() {
        val start = listOf(Sample(0, 1), Sample(5 * 60 * min, 2))
        val replaced = Pace.append(start, Sample(5 * 60 * min + 30_000, 3))
        val trimmed = Pace.append(start, Sample(7 * 60 * min, 4))

        assertEquals(listOf(Sample(0, 1), Sample(5 * 60 * min + 30_000, 3)), replaced)
        assertTrue(trimmed.none { it.t == 0L })
    }
}
