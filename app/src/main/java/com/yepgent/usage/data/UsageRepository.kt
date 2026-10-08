package com.yepgent.usage.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.yepgent.usage.BuildConfig
import com.yepgent.usage.surfaces.Surfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

data class Snapshot(
    val signedIn: Boolean,
    val limits: List<Limit>,
    val fetchedAt: Long?,
    val stale: Boolean,
    val error: String?,
    val authExpired: Boolean,
    val forecast: Forecast?,
    val nextRefreshAt: Long?,
)

/** Single path for every refresh, so the app, worker and notification action agree. */
object UsageRepository {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state

    fun signedIn(): Boolean = CookieManager.getInstance()
        .getCookie(ClaudeWebClient.ORIGIN)?.contains(ClaudeWebClient.SESSION_COOKIE) == true

    /** Builds a snapshot from what is stored, without touching the network. */
    fun load(context: Context): Snapshot {
        val store = Store(context)
        val limits = store.simulated?.takeIf { BuildConfig.DEBUG }?.let(::simulatedLimits)
            ?: store.lastJson?.let(UsageParser::parse).orEmpty()
        val now = System.currentTimeMillis()
        val fetchedAt = store.fetchedAt.takeIf { it > 0 }
        val stale = store.lastError != null ||
            (fetchedAt != null && now - fetchedAt > store.intervalMinutes * 2 * 60_000L)
        val session = limits.headline().session
        return Snapshot(
            signedIn = signedIn() || (BuildConfig.DEBUG && store.simulated != null),
            limits = limits,
            fetchedAt = fetchedAt,
            stale = stale,
            error = store.lastError,
            authExpired = store.authExpired,
            forecast = session?.let { Pace.forecast(store.samples, it.resetsAt, now) },
            nextRefreshAt = fetchedAt?.let { it + store.intervalMinutes * 60_000L },
        ).also { _state.value = it }
    }

    /**
     * Fetches, stores, and pushes the result to the status notification, alerts and widget.
     * A manual refresh skips the failure back-off unless claude.ai asked us to wait.
     */
    suspend fun refresh(context: Context, manual: Boolean): Snapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val store = Store(app)
            val now = System.currentTimeMillis()
            val wasExpired = store.authExpired
            var fresh: List<Limit>? = null

            val simulated = store.simulated?.takeIf { BuildConfig.DEBUG }
            if (simulated != null) {
                fresh = simulatedLimits(simulated)
                store.fetchedAt = now
                store.lastError = null
            } else if (signedIn()) {
                val backoff = store.backoff
                val gated = backoff.shouldWait(now) && (!manual || backoff.rateLimited)
                if (!gated) fresh = fetch(app, store, now)
            }

            if (fresh != null) {
                fresh.headline().session?.let { store.samples = Pace.append(store.samples, Sample(now, it.percent)) }
                val (events, marks) = Alerts.evaluate(
                    store.marks, fresh, if (store.alerts) store.thresholds else emptySet(), store.resetNotices,
                )
                store.marks = marks
                Surfaces.alert(app, events)
            }
            if (store.authExpired && !wasExpired) Surfaces.authExpired(app)

            load(app).also { Surfaces.publish(app, it) }
        }
    }

    private fun fetch(context: Context, store: Store, now: Long): List<Limit>? {
        val client = ClaudeWebClient(
            cookies = { CookieManager.getInstance().getCookie(ClaudeWebClient.ORIGIN) },
            userAgent = WebSettings.getDefaultUserAgent(context),
        )
        return try {
            val (json, org) = client.fetchUsage(store.orgId)
            store.orgId = org
            store.lastJson = json
            store.fetchedAt = now
            store.lastError = null
            store.authExpired = false
            store.backoff = Backoff()
            UsageParser.parse(json)
        } catch (e: UsageException) {
            // A cached org that now 4xxs may belong to an old account: resolve again next time.
            if (e.status in 400..499 && e.status != 429) store.orgId = null
            store.lastError = e.message
            store.authExpired = e.authFailed
            store.backoff = if (e.authFailed) store.backoff.onAuthFailure(now) else store.backoff.onFailure(now, e.retryAfterMs)
            null
        } catch (e: IOException) {
            store.lastError = "Network error"
            store.backoff = store.backoff.onFailure(now, null)
            null
        }
    }

    suspend fun signOut(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Store(context).clearAccount()
        Surfaces.clear(context)
        load(context)
    }

    private fun simulatedLimits(percent: Int): List<Limit> {
        val now = Instant.now()
        return listOf(
            Limit("session", "Session", Group.SESSION, percent, now.plusSeconds(2 * 3600 + 14 * 60)),
            Limit("weekly_all", Limit.ALL_MODELS, Group.WEEKLY, 41, now.plusSeconds(4 * 86400)),
            Limit("weekly_opus", "Opus", Group.WEEKLY, 78, now.plusSeconds(4 * 86400)),
        )
    }
}
