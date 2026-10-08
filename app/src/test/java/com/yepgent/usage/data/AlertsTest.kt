package com.yepgent.usage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertsTest {
    private val thresholds = listOf(75, 90, 100)
    private val reset = Instant.parse("2026-10-08T15:40:00Z")

    private fun session(percent: Int, resetsAt: Instant? = reset) =
        Limit("session", "Session", Group.SESSION, percent, resetsAt)

    private fun run(marks: Map<String, AlertMark>, limit: Limit, resetNotices: Boolean = true) =
        Alerts.evaluate(marks, listOf(limit), thresholds, resetNotices)

    @Test
    fun crossingFiresOncePerThreshold() {
        val (first, marks) = run(emptyMap(), session(76))
        val (again, _) = run(marks, session(80))

        assertEquals(listOf(AlertEvent.Crossed(session(76), 75)), first)
        assertTrue(again.isEmpty())
    }

    @Test
    fun jumpPastSeveralThresholdsFiresOnlyTheHighest() {
        val (events, marks) = run(emptyMap(), session(95))

        assertEquals(90, (events.single() as AlertEvent.Crossed).threshold)
        assertEquals(90, marks.getValue("session").level)
    }

    @Test
    fun resetFiresOnlyAfterAThresholdWasReached() {
        val later = reset.plusSeconds(5 * 3600)
        val (_, alerted) = run(emptyMap(), session(91))
        val (quiet, calm) = run(emptyMap(), session(40))

        val (afterAlert, _) = run(alerted, session(3, later))
        val (afterCalm, _) = run(calm, session(3, later))

        assertEquals(listOf(AlertEvent.Reset(session(3, later))), afterAlert)
        assertTrue(quiet.isEmpty())
        assertTrue(afterCalm.isEmpty())
    }

    @Test
    fun resetNoticesCanBeTurnedOff() {
        val (_, alerted) = run(emptyMap(), session(91))
        val (events, marks) = run(alerted, session(3, reset.plusSeconds(5 * 3600)), resetNotices = false)

        assertTrue(events.isEmpty())
        assertEquals(0, marks.getValue("session").level)
    }

    @Test
    fun resetJitterIsNotANewWindow() {
        val (_, marks) = run(emptyMap(), session(76))
        val (events, _) = run(marks, session(77, reset.plusSeconds(30)))

        assertTrue(events.isEmpty())
    }

    @Test
    fun newWindowCanAlertAgain() {
        val (_, marks) = run(emptyMap(), session(76))
        val (events, _) = run(marks, session(80, reset.plusSeconds(5 * 3600)))

        assertEquals(2, events.size)
        assertEquals(75, (events[1] as AlertEvent.Crossed).threshold)
    }

    @Test
    fun noThresholdsMeansNoAlerts() {
        val (events, _) = Alerts.evaluate(emptyMap(), listOf(session(99)), emptyList(), true)

        assertTrue(events.isEmpty())
    }
}
