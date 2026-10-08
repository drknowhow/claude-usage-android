package com.yepgent.usage.data

import kotlin.math.max
import kotlin.math.min

/**
 * Poll gate after failures. A server Retry-After wins over the exponential schedule;
 * auth failures wait the maximum since they will not fix themselves.
 */
data class Backoff(val failures: Int = 0, val retryAt: Long = 0, val rateLimited: Boolean = false) {

    fun shouldWait(now: Long): Boolean = now < retryAt

    fun onFailure(now: Long, retryAfterMs: Long?, jitter: Double = Math.random()): Backoff {
        val n = failures + 1
        val exp = min(MAX_MS, BASE_MS shl min(n - 1, 20))
        val wait = if (retryAfterMs != null) min(MAX_MS, max(retryAfterMs, BASE_MS)) else exp + (exp * 0.1 * jitter).toLong()
        return Backoff(n, now + wait, rateLimited = retryAfterMs != null)
    }

    fun onAuthFailure(now: Long): Backoff = Backoff(failures + 1, now + MAX_MS)

    companion object {
        const val BASE_MS = 60_000L
        const val MAX_MS = 15 * 60_000L
    }
}
