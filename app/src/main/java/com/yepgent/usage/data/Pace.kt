package com.yepgent.usage.data

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Sample(val t: Long, val percent: Int)

data class Forecast(val etaMs: Long, val beforeReset: Boolean?)

/** Least-squares burn rate over the samples since the last reset. */
object Pace {
    private const val MIN_POINTS = 3
    private const val MIN_SPAN_MS = 10 * 60_000L
    private const val FLAT_PCT_PER_HOUR = 0.05
    private const val RESET_DROP_POINTS = 10

    fun currentWindow(samples: List<Sample>): List<Sample> {
        var start = 0
        for (i in 1 until samples.size) {
            if (samples[i - 1].percent - samples[i].percent >= RESET_DROP_POINTS) start = i
        }
        return samples.drop(start)
    }

    /** Null when there is too little data or usage is flat. */
    fun forecast(samples: List<Sample>, resetsAt: Instant?, now: Long): Forecast? {
        val pts = currentWindow(samples)
        if (pts.size < MIN_POINTS || pts.last().t - pts.first().t < MIN_SPAN_MS) return null

        val t0 = pts.first().t
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (p in pts) {
            val x = (p.t - t0) / 3_600_000.0
            sx += x
            sy += p.percent
            sxx += x * x
            sxy += x * p.percent
        }
        val n = pts.size
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return null
        val ratePerHour = (n * sxy - sx * sy) / denom
        if (ratePerHour < FLAT_PCT_PER_HOUR) return null

        val latest = pts.last().percent
        if (latest >= 100) return Forecast(0, true)
        val etaMs = ((100 - latest) / ratePerHour * 3_600_000).toLong()
        return Forecast(etaMs, resetsAt?.let { now + etaMs < it.toEpochMilli() })
    }

    /** Appends a sample, keeping the last six hours and at most one point a minute. */
    fun append(samples: List<Sample>, sample: Sample): List<Sample> {
        val kept = samples.filter { sample.t - it.t <= 6 * 3_600_000L }
        if (kept.isNotEmpty() && sample.t - kept.last().t < 60_000L) return kept.dropLast(1) + sample
        return (kept + sample).takeLast(120)
    }
}
