package com.yepgent.usage.data

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** The highest threshold already notified for a limit in its current window. */
@Serializable
data class AlertMark(val resetsAt: Long?, val level: Int)

sealed interface AlertEvent {
    val limit: Limit

    data class Crossed(override val limit: Limit, val threshold: Int) : AlertEvent
    data class Reset(override val limit: Limit) : AlertEvent
}

object Alerts {
    /** A resets_at that moves by more than this is a new window, not jitter. */
    private const val WINDOW_MOVE_MS = 10 * 60_000L

    /**
     * Returns the events to post and the marks to store. Each threshold fires once per
     * window (only the highest one crossed); a reset fires only for a window that had
     * reached a threshold, so a quiet five-hour roll-over stays silent.
     */
    fun evaluate(
        marks: Map<String, AlertMark>,
        limits: List<Limit>,
        thresholds: Collection<Int>,
        resetNotices: Boolean,
    ): Pair<List<AlertEvent>, Map<String, AlertMark>> {
        val events = mutableListOf<AlertEvent>()
        val next = mutableMapOf<String, AlertMark>()
        for (limit in limits) {
            val resetsAt = limit.resetsAt?.toEpochMilli()
            val prev = marks[limit.key]
            val newWindow = prev?.resetsAt != null && resetsAt != null &&
                abs(resetsAt - prev.resetsAt) > WINDOW_MOVE_MS
            var level = if (prev == null || newWindow) 0 else prev.level
            if (newWindow && prev!!.level > 0 && resetNotices) events += AlertEvent.Reset(limit)

            val crossed = thresholds.filter { limit.percent >= it }.maxOrNull() ?: 0
            if (crossed > level) {
                events += AlertEvent.Crossed(limit, crossed)
                level = crossed
            }
            next[limit.key] = AlertMark(resetsAt, level)
        }
        return events to next
    }
}
