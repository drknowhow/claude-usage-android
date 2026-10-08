package com.yepgent.usage.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yepgent.usage.Palette
import com.yepgent.usage.data.Group
import com.yepgent.usage.data.Limit
import com.yepgent.usage.data.Snapshot
import com.yepgent.usage.data.UsageRepository
import com.yepgent.usage.data.headline
import com.yepgent.usage.surfaces.Format
import com.yepgent.usage.ui.MainActivity
import kotlin.math.roundToInt

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

class UsageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = UsageRepository.load(context)
        // A live Glance session is recomposed, not re-provided, so read the shared state here.
        provideContent {
            val snap by UsageRepository.state.collectAsState()
            Content(snap ?: initial)
        }
    }
}

private fun pair(pick: (Palette) -> Int): ColorProvider =
    ColorProvider(day = Color(pick(Palette.LIGHT)), night = Color(pick(Palette.DARK)))

private fun mono(color: ColorProvider, size: Int, bold: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = FontFamily.Monospace,
)

@Composable
private fun Content(snap: Snapshot) {
    val size = LocalSize.current
    val compact = size.width < 200.dp || size.height < 100.dp
    val h = snap.limits.headline()
    val main = h.session ?: h.weekly

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(pair { it.ground })
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (main == null) {
            Text("claude usage", style = mono(pair { it.ink2 }, 11))
            Text(if (snap.signedIn) "no reading yet" else "tap to sign in", style = mono(pair { it.ink }, 14, bold = true))
            return@Column
        }
        if (!compact) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text("claude usage", style = mono(pair { it.ink2 }, 11), modifier = GlanceModifier.defaultWeight())
                Text(
                    snap.fetchedAt?.let { Format.clock(it) + if (snap.stale) " stale" else "" }.orEmpty(),
                    style = mono(pair { if (snap.stale) it.signal else it.ink2 }, 11),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${main.percent}%", style = mono(level(main), if (compact) 26 else 36, bold = true))
            Spacer(GlanceModifier.width(8.dp))
            Text(caption(main), style = mono(pair { it.ink2 }, 11), modifier = GlanceModifier.padding(bottom = 4.dp))
        }
        Spacer(GlanceModifier.height(6.dp))
        Bar(main.percent, if (compact) 8.dp else 14.dp)
        if (!compact && h.weekly != null && h.session != null) {
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text("weekly max", style = mono(pair { it.ink2 }, 12), modifier = GlanceModifier.defaultWeight())
                Text(
                    listOfNotNull("${h.weekly.percent}%", h.weekly.label.lowercase().takeIf { it != "all models" },
                        h.weekly.resetsAt?.let { Format.resetAt(it).substringBefore(' ') }).joinToString(" "),
                    style = mono(level(h.weekly), 12, bold = true),
                )
            }
        }
    }
}

/**
 * Twenty cells built from Glance boxes so their colours follow day/night like the text; a
 * bitmap would be drawn once in whatever mode the app process happened to be in. Two rows
 * of ten because a Glance Row holds at most ten children.
 */
@Composable
private fun Bar(percent: Int, height: Dp) {
    val filled = (percent.coerceIn(0, 100) / 100f * 20).roundToInt()
    val fill = pair { it.levelColor(percent) }
    val empty = pair { it.faint }
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        for (half in 0..1) {
            Row(GlanceModifier.defaultWeight().fillMaxHeight()) {
                for (i in 0 until 10) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 3.dp)) {
                        Box(GlanceModifier.fillMaxSize().background(if (half * 10 + i < filled) fill else empty)) {}
                    }
                }
            }
        }
    }
}

private fun level(limit: Limit) = pair { it.levelColor(limit.percent) }

private fun caption(limit: Limit): String = listOfNotNull(
    if (limit.group == Group.SESSION) "session" else "weekly",
    limit.resetsAt?.let { Format.resetAt(it) },
).joinToString(" · ")
