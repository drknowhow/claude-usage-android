package com.yepgent.usage.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class UsageException(
    message: String,
    val authFailed: Boolean = false,
    val status: Int? = null,
    val retryAfterMs: Long? = null,
) : Exception(message)

/**
 * Reads usage from claude.ai with the session the in-app login left in the WebView
 * cookie jar. The cookie is read per request and never stored by this app.
 */
class ClaudeWebClient(
    private val cookies: () -> String?,
    private val userAgent: String,
) {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    /**
     * @return the raw usage JSON and the organization id it was read for.
     * @throws UsageException for HTTP and auth failures, [IOException] for network ones.
     */
    fun fetchUsage(orgId: String?): Pair<String, String> {
        val cookie = cookies()?.takeIf { SESSION_COOKIE in it }
            ?: throw UsageException("Not signed in", authFailed = true)
        val org = orgId ?: resolveOrgId(cookie)
        return get("$ORIGIN/api/organizations/$org/usage", cookie) to org
    }

    /** Prefers the organization with the claude.ai chat capability over API-only ones. */
    private fun resolveOrgId(cookie: String): String {
        val orgs = (runCatching { Json.parseToJsonElement(get("$ORIGIN/api/organizations", cookie)) }
            .getOrNull() as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val chat = orgs.firstOrNull { org ->
            (org["capabilities"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.contentOrNull == "chat" }
        }
        return ((chat ?: orgs.firstOrNull())?.get("uuid") as? JsonPrimitive)?.contentOrNull
            ?: throw UsageException("Could not find your organization. Sign in again.", authFailed = true)
    }

    private fun get(url: String, cookie: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cookie", cookie)
            .header("User-Agent", userAgent)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response)
            return response.body?.string() ?: throw UsageException("Empty response", status = response.code)
        }
    }

    private fun httpError(response: Response): UsageException {
        val status = response.code
        val retryAfter = parseRetryAfter(response.header("Retry-After"), System.currentTimeMillis())
        return when {
            response.header("cf-mitigated") == "challenge" ->
                UsageException("claude.ai asked for a browser check. Open the app and sign in again.", status = status)
            status == 401 || status == 403 ->
                UsageException("claude.ai session expired. Sign in again.", authFailed = true, status = status)
            status == 429 -> UsageException("Rate limited by claude.ai", status = status, retryAfterMs = retryAfter)
            else -> UsageException("claude.ai error $status", status = status, retryAfterMs = retryAfter)
        }
    }

    companion object {
        const val ORIGIN = "https://claude.ai"
        const val SESSION_COOKIE = "sessionKey="
    }
}

/** Retry-After is either delta-seconds or an HTTP date. */
fun parseRetryAfter(value: String?, now: Long): Long? {
    if (value.isNullOrBlank()) return null
    value.trim().toLongOrNull()?.let { return maxOf(0, it * 1000) }
    return runCatching { ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }
        .getOrNull()?.let { maxOf(0, it.toInstant().toEpochMilli() - now) }
}
