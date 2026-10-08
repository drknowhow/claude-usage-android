package com.yepgent.usage.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yepgent.usage.data.Group
import com.yepgent.usage.data.Limit
import com.yepgent.usage.data.Snapshot
import com.yepgent.usage.data.Store
import com.yepgent.usage.data.headline
import com.yepgent.usage.surfaces.Format
import com.yepgent.usage.ui.theme.Mono
import java.time.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    snap: Snapshot,
    store: Store,
    now: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = Mono.colors
    Column(Modifier.fillMaxSize()) {
        Header("usage") {
            SquareButton(MonoIcons.Refresh, "Refresh", onRefresh)
            SquareButton(MonoIcons.Settings, "Settings", onSettings)
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (snap.authExpired) {
                    Notice("session expired · tap to sign in again", onSignIn)
                }
                val h = snap.limits.headline()
                val main = h.session ?: h.weekly
                if (main == null) {
                    MonoText(if (refreshing) "fetching…" else "no reading yet. pull to refresh.", color = c.ink2)
                } else {
                    Hero(main, snap, now)
                    val weekly = snap.limits.filter { it.group == Group.WEEKLY && it !== main }
                    if (weekly.isNotEmpty()) Weekly(weekly, now)
                }
                Column {
                    Rule()
                    LeaderRow("status icon", if (store.statusIcon) store.iconDriver.label else "off", onSettings)
                    LeaderRow(
                        "alerts",
                        if (store.alerts) store.thresholds.sorted().joinToString("/") else "off",
                        onSettings,
                    )
                }
            }
        }
        Footer(snap, store)
    }
}

@Composable
private fun Hero(limit: Limit, snap: Snapshot, now: Long) {
    val c = Mono.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MonoText(if (limit.group == Group.SESSION) "session / 5h" else "${limit.title} / 7d", size = 12.sp, color = c.ink2)
        Row(verticalAlignment = Alignment.Bottom) {
            MonoText(
                "${limit.percent}",
                size = 112.sp,
                weight = FontWeight.Medium,
                color = if (limit.percent >= 90) c.signal else c.ink,
                letterSpacing = (-0.06).em,
                lineHeight = 100.sp,
            )
            MonoText("%", size = 32.sp, weight = FontWeight.Medium, color = c.ink2, modifier = Modifier.padding(bottom = 14.dp))
        }
        SegmentBar(limit.percent, 28.dp, c.level(limit.percent))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            limit.resetsAt?.let { at ->
                KeyValue("resets", "${Format.resetAt(at)} (in ${Format.span(at.toEpochMilli() - now)})")
            }
            if (limit.group == Group.SESSION) {
                val (text, hot) = paceText(limit, snap)
                KeyValue("pace", text, if (hot) c.signal else c.ink)
            }
        }
    }
}

private fun paceText(limit: Limit, snap: Snapshot): Pair<String, Boolean> {
    val f = snap.forecast
    return when {
        limit.percent >= 100 -> "limit reached" to true
        f == null -> "measuring" to false
        f.beforeReset == false -> "under limit at reset" to false
        else -> "100% in ${Format.span(f.etaMs)} !" to (f.beforeReset == true)
    }
}

@Composable
private fun Weekly(limits: List<Limit>, now: Long) {
    val c = Mono.colors
    val resets = limits.mapNotNull { it.resetsAt }
    val shared = resets.size == limits.size && resets.all { abs(it.toEpochMilli() - resets.first().toEpochMilli()) < 10 * 60_000L }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Rule()
        Spacer(Modifier.height(6.dp))
        MonoText(
            if (shared) "weekly / resets ${Format.resetAt(resets.first(), Instant.ofEpochMilli(now))}" else "weekly",
            size = 12.sp,
            color = c.ink2,
        )
        for (limit in limits) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    MonoText(limit.label.lowercase(), size = 14.sp, modifier = Modifier.weight(1f))
                    MonoText("${limit.percent}%", size = 14.sp, weight = FontWeight.Bold, color = c.level(limit.percent))
                }
                SegmentBar(limit.percent, 10.dp, c.level(limit.percent))
                if (!shared) limit.resetsAt?.let {
                    MonoText("resets ${Format.resetAt(it, Instant.ofEpochMilli(now))}", size = 11.sp, color = c.ink2)
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, color: androidx.compose.ui.graphics.Color = Mono.colors.ink) {
    Row {
        MonoText(key, color = Mono.colors.ink2, modifier = Modifier.width(72.dp))
        MonoText(value, color = color)
    }
}

@Composable
private fun Notice(text: String, onClick: () -> Unit) {
    val c = Mono.colors
    MonoText(
        text,
        color = c.signal,
        weight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, c.signal)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    )
}

@Composable
private fun Footer(snap: Snapshot, store: Store) {
    val c = Mono.colors
    val text = when {
        snap.error != null -> listOfNotNull("stale", snap.error.lowercase(), snap.fetchedAt?.let { "last ${Format.clock(it)}" })
            .joinToString(" · ")
        snap.fetchedAt != null -> "updated ${Format.clock(snap.fetchedAt)} · next ${Format.clock(snap.fetchedAt + store.intervalMinutes * 60_000L)}"
        else -> "not updated yet"
    }
    Column {
        Rule()
        MonoText(
            text,
            size = 12.sp,
            color = if (snap.error != null) c.signal else c.ink2,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
fun Header(title: String, leading: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = if (leading == null) 24.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            MonoText(title, size = 22.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            actions()
        }
        Rule()
    }
}
