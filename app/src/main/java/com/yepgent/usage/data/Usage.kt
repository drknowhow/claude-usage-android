package com.yepgent.usage.data

import java.time.Instant

enum class Group { SESSION, WEEKLY }

data class Limit(
    val key: String,
    val label: String,
    val group: Group,
    val percent: Int,
    val resetsAt: Instant?,
) {
    /** Lower-case name used in notifications: "session", "weekly", "opus weekly". */
    val title: String
        get() = when {
            group == Group.SESSION -> "session"
            label == ALL_MODELS -> "weekly"
            else -> "${label.lowercase()} weekly"
        }

    companion object {
        const val ALL_MODELS = "All models"
    }
}

data class Headline(val session: Limit?, val weekly: Limit?)

fun List<Limit>.headline() = Headline(
    session = filter { it.group == Group.SESSION }.maxByOrNull { it.percent },
    weekly = filter { it.group == Group.WEEKLY }.maxByOrNull { it.percent },
)
