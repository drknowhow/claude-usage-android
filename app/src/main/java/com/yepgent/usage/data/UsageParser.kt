package com.yepgent.usage.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * Flattens a usage payload into limits, session first, then weekly by descending percent.
 *
 * `limits[]` (the OAuth endpoint's shape) is preferred when present; otherwise the
 * claude.ai web endpoint's top-level windows are read. Unknown or malformed entries are
 * skipped, never thrown on.
 */
object UsageParser {
    private val LEGACY = listOf(
        Window("five_hour", "session", "Session", Group.SESSION),
        Window("seven_day", "weekly_all", Limit.ALL_MODELS, Group.WEEKLY),
        Window("seven_day_opus", "weekly_opus", "Opus", Group.WEEKLY),
        Window("seven_day_sonnet", "weekly_sonnet", "Sonnet", Group.WEEKLY),
    )

    private val order = compareBy<Limit> { it.group != Group.SESSION }.thenByDescending { it.percent }

    fun parse(json: String): List<Limit> =
        runCatching { parse(Json.parseToJsonElement(json)) }.getOrDefault(emptyList())

    fun parse(root: JsonElement): List<Limit> {
        val obj = root as? JsonObject ?: return emptyList()
        val fromLimits = (obj["limits"] as? JsonArray).orEmpty().mapNotNull(::limitOf)
        val limits = fromLimits.ifEmpty { LEGACY.mapNotNull { it.read(obj[it.field]) } }
        return limits.sortedWith(order)
    }

    private fun limitOf(element: JsonElement): Limit? {
        val o = element as? JsonObject ?: return null
        val percent = o.number("percent") ?: return null
        val kind = o.string("kind") ?: "limit"
        val scope = o["scope"] as? JsonObject
        val model = (scope?.get("model") as? JsonObject)?.string("display_name")
        val surface = when (val s = scope?.get("surface")) {
            is JsonObject -> s.string("display_name")
            is JsonPrimitive -> s.contentOrNull
            else -> null
        }
        val label = when (kind) {
            "session" -> "Session"
            "weekly_all" -> Limit.ALL_MODELS
            "weekly_scoped" -> model ?: surface ?: "Scoped"
            else -> kind.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        val group = when (o.string("group")) {
            null -> if (kind == "session") Group.SESSION else Group.WEEKLY
            "session" -> Group.SESSION
            else -> Group.WEEKLY
        }
        return Limit("$kind:${model.orEmpty()}", label, group, percent.roundToInt(), instantOf(o.string("resets_at")))
    }

    private data class Window(val field: String, val key: String, val label: String, val group: Group) {
        fun read(element: JsonElement?): Limit? {
            val o = element as? JsonObject ?: return null
            val utilization = o.number("utilization") ?: return null
            return Limit(key, label, group, utilization.roundToInt(), instantOf(o.string("resets_at")))
        }
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

internal fun instantOf(value: String?): Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
