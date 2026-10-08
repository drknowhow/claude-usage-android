package com.yepgent.usage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

    @Test
    fun failuresDoubleUpToTheCap() {
        var b = Backoff()
        val waits = (1..6).map {
            b = b.onFailure(now = 0, retryAfterMs = null, jitter = 0.0)
            b.retryAt
        }

        assertEquals(listOf(60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L), waits)
    }

    @Test
    fun retryAfterWinsAndIsClamped() {
        assertEquals(300_000L, Backoff().onFailure(0, 300_000L).retryAt)
        assertEquals(Backoff.BASE_MS, Backoff().onFailure(0, 1_000L).retryAt)
        assertEquals(Backoff.MAX_MS, Backoff().onFailure(0, 3_600_000L).retryAt)
        assertTrue(Backoff().onFailure(0, 5_000L).rateLimited)
    }

    @Test
    fun authFailureWaitsTheMaximum() {
        val b = Backoff().onAuthFailure(now = 1_000)

        assertEquals(1_000 + Backoff.MAX_MS, b.retryAt)
        assertFalse(b.rateLimited)
        assertTrue(b.shouldWait(1_000 + Backoff.MAX_MS - 1))
        assertFalse(b.shouldWait(1_000 + Backoff.MAX_MS))
    }

    @Test
    fun retryAfterParsesSecondsAndDates() {
        val now = 1_791_465_000_000L
        assertEquals(120_000L, parseRetryAfter("120", now))
        assertEquals(30_000L, parseRetryAfter("Thu, 08 Oct 2026 17:10:30 GMT", 1_791_479_400_000L))
        assertEquals(null, parseRetryAfter("soon", now))
        assertEquals(null, parseRetryAfter(null, now))
    }
}
