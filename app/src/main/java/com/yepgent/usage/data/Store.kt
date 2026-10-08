package com.yepgent.usage.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json

enum class IconDriver(val label: String) { SESSION("session"), WEEKLY("weekly"), HIGHEST("highest") }

/** Everything the app persists. Reads are cheap SharedPreferences lookups. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)

    var orgId: String?
        get() = prefs.getString("org_id", null)
        set(v) = prefs.edit { putString("org_id", v) }

    var lastJson: String?
        get() = prefs.getString("last_json", null)
        set(v) = prefs.edit { putString("last_json", v) }

    var fetchedAt: Long
        get() = prefs.getLong("fetched_at", 0)
        set(v) = prefs.edit { putLong("fetched_at", v) }

    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(v) = prefs.edit { putString("last_error", v) }

    var authExpired: Boolean
        get() = prefs.getBoolean("auth_expired", false)
        set(v) = prefs.edit { putBoolean("auth_expired", v) }

    var backoff: Backoff
        get() = Backoff(prefs.getInt("bo_failures", 0), prefs.getLong("bo_retry_at", 0), prefs.getBoolean("bo_rate", false))
        set(v) = prefs.edit {
            putInt("bo_failures", v.failures)
            putLong("bo_retry_at", v.retryAt)
            putBoolean("bo_rate", v.rateLimited)
        }

    var marks: Map<String, AlertMark>
        get() = decode("marks") ?: emptyMap()
        set(v) = prefs.edit { putString("marks", Json.encodeToString(v)) }

    var samples: List<Sample>
        get() = decode("samples") ?: emptyList()
        set(v) = prefs.edit { putString("samples", Json.encodeToString(v)) }

    var statusIcon: Boolean
        get() = prefs.getBoolean("status_icon", true)
        set(v) = prefs.edit { putBoolean("status_icon", v) }

    var iconDriver: IconDriver
        get() = runCatching { IconDriver.valueOf(prefs.getString("icon_driver", null)!!) }.getOrDefault(IconDriver.SESSION)
        set(v) = prefs.edit { putString("icon_driver", v.name) }

    var alerts: Boolean
        get() = prefs.getBoolean("alerts", true)
        set(v) = prefs.edit { putBoolean("alerts", v) }

    var thresholds: Set<Int>
        get() = prefs.getStringSet("thresholds", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: THRESHOLDS.toSet()
        set(v) = prefs.edit { putStringSet("thresholds", v.map { it.toString() }.toSet()) }

    var resetNotices: Boolean
        get() = prefs.getBoolean("reset_notices", true)
        set(v) = prefs.edit { putBoolean("reset_notices", v) }

    var intervalMinutes: Int
        get() = prefs.getInt("interval", 15)
        set(v) = prefs.edit { putInt("interval", v) }

    /** Debug builds only: a percent that replaces the network reading. */
    var simulated: Int?
        get() = prefs.getInt("simulated", -1).takeIf { it >= 0 }
        set(v) = prefs.edit { putInt("simulated", v ?: -1) }

    fun clearAccount() = prefs.edit {
        listOf("org_id", "last_json", "fetched_at", "last_error", "auth_expired", "marks", "samples",
            "bo_failures", "bo_retry_at", "bo_rate").forEach(::remove)
    }

    private inline fun <reified T> decode(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { Json.decodeFromString<T>(it) }.getOrNull() }

    companion object {
        val THRESHOLDS = listOf(75, 90, 100)
        val INTERVALS = listOf(15, 30, 60)
    }
}
