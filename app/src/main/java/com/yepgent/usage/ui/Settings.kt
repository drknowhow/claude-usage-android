package com.yepgent.usage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yepgent.usage.BuildConfig
import com.yepgent.usage.data.IconDriver
import com.yepgent.usage.data.Store
import com.yepgent.usage.ui.theme.Mono

/**
 * @param onChanged called after a setting that affects the notification or widget changes.
 * @param onIntervalChanged called with the new poll interval in minutes.
 */
@Composable
fun Settings(
    store: Store,
    signedIn: Boolean,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onSimulate: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val c = Mono.colors
    // Store is SharedPreferences-backed, not observable; bumping this re-reads it.
    var version by remember { mutableIntStateOf(0) }
    fun change(notify: Boolean = true, write: () -> Unit) {
        write()
        version++
        if (notify) onChanged()
    }
    fun onOff(v: Boolean) = if (v) "on" else "off"

    Column(Modifier.fillMaxSize()) {
        Header("settings", leading = { SquareButton(MonoIcons.Back, "Back", onBack) })
        key(version) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Section("status bar") {
                    LeaderRow("status icon", onOff(store.statusIcon), { change { store.statusIcon = !store.statusIcon } }, role = Role.Switch)
                    LeaderRow("icon shows", store.iconDriver.label, {
                        change { store.iconDriver = IconDriver.entries[(store.iconDriver.ordinal + 1) % IconDriver.entries.size] }
                    })
                }
                Section("alerts") {
                    LeaderRow("alerts", onOff(store.alerts), { change { store.alerts = !store.alerts } }, role = Role.Switch)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        for (t in Store.THRESHOLDS) {
                            Chip("$t%", t in store.thresholds) {
                                change(notify = false) {
                                    store.thresholds = if (t in store.thresholds) store.thresholds - t else store.thresholds + t
                                }
                            }
                        }
                    }
                    LeaderRow("reset notices", onOff(store.resetNotices), { change(notify = false) { store.resetNotices = !store.resetNotices } }, role = Role.Switch)
                }
                Section("refresh") {
                    LeaderRow("every", "${store.intervalMinutes} min", {
                        val next = Store.INTERVALS[(Store.INTERVALS.indexOf(store.intervalMinutes) + 1) % Store.INTERVALS.size]
                        change(notify = false) { store.intervalMinutes = next }
                        onIntervalChanged(next)
                    })
                }
                Section("account") {
                    if (signedIn) LeaderRow("claude.ai", "sign out", onSignOut, valueColor = c.signal)
                    else LeaderRow("claude.ai", "sign in", onSignIn)
                }
                if (BuildConfig.DEBUG) {
                    Section("debug") {
                        val sim = store.simulated
                        LeaderRow("simulate", sim?.let { "$it%" } ?: "off", {
                            change(notify = false) { store.simulated = if (sim == null) 62 else null }
                            onSimulate()
                        }, role = Role.Switch)
                        if (sim != null) {
                            var value by remember { mutableIntStateOf(sim) }
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { value = it.toInt() },
                                onValueChangeFinished = {
                                    store.simulated = value
                                    onSimulate()
                                },
                                valueRange = 0f..100f,
                            )
                        }
                    }
                }
                MonoText("version ${BuildConfig.VERSION_NAME}", size = 11.sp, color = c.ink2)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        MonoText(title, size = 12.sp, color = Mono.colors.ink2, modifier = Modifier.padding(bottom = 4.dp))
        Rule()
        content()
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onToggle: () -> Unit) {
    val c = Mono.colors
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 64.dp)
            .border(1.5.dp, c.ink)
            .background(if (selected) c.ink else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoText(label, weight = FontWeight.Bold, color = if (selected) c.ground else c.ink)
    }
}
