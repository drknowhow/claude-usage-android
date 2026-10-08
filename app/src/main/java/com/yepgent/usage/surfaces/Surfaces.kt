package com.yepgent.usage.surfaces

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.glance.appwidget.updateAll
import com.yepgent.usage.Palette
import com.yepgent.usage.R
import com.yepgent.usage.data.AlertEvent
import com.yepgent.usage.data.Group
import com.yepgent.usage.data.IconDriver
import com.yepgent.usage.data.Limit
import com.yepgent.usage.data.Snapshot
import com.yepgent.usage.data.Store
import com.yepgent.usage.data.headline
import com.yepgent.usage.ui.MainActivity
import com.yepgent.usage.widget.UsageWidget
import com.yepgent.usage.work.RefreshReceiver

/** Status-bar notification, alerts and widget: everything outside the app's own window. */
object Surfaces {
    private const val CHANNEL_LIVE = "live"
    private const val CHANNEL_ALERTS = "alerts"
    private const val LIVE_ID = 1
    private const val AUTH_ID = 2
    private const val ALERT_SUMMARY_ID = 3

    /** Platform key behind NotificationCompat.setRequestPromotedOngoing; API 36 has no constant. */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    private class Row(val row: Int, val label: Int, val fill: Int, val empty: Int, val pct: Int)

    private val ROWS = listOf(
        Row(R.id.row0, R.id.label0, R.id.fill0, R.id.empty0, R.id.pct0),
        Row(R.id.row1, R.id.label1, R.id.fill1, R.id.empty1, R.id.pct1),
        Row(R.id.row2, R.id.label2, R.id.fill2, R.id.empty2, R.id.pct2),
        Row(R.id.row3, R.id.label3, R.id.fill3, R.id.empty3, R.id.pct3),
    )

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Default importance, made silent: LOW lands in the shade's "Silent" group, whose
        // icons the status bar hides, and the glyph there is the whole point.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, "Live usage", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The percent in the status bar and the usage card in the shade"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Limit alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a limit passes 75, 90 or 100% and when it resets"
            },
        )
    }

    suspend fun publish(context: Context, snap: Snapshot) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val store = Store(context)
        val shown = driver(snap.limits, store.iconDriver)
        if (store.statusIcon && snap.signedIn && shown != null) nm.notify(LIVE_ID, live(context, snap, shown))
        else nm.cancel(LIVE_ID)
        UsageWidget().updateAll(context)
    }

    fun alert(context: Context, events: List<AlertEvent>) {
        for (event in events) {
            val limit = event.limit
            val reset = limit.resetsAt?.let { Format.resetAt(it) }
            val (title, text) = when (event) {
                is AlertEvent.Crossed ->
                    (if (event.threshold >= 100) "${limit.title} limit reached" else "${limit.title} passed ${event.threshold}%") to
                        listOfNotNull("${limit.percent}% used", reset?.let { "resets $it" }).joinToString(" · ")
                is AlertEvent.Reset ->
                    "${limit.title} reset" to listOfNotNull("now ${limit.percent}%", reset?.let { "next reset $it" }).joinToString(" · ")
            }
            postAlert(context, 1000 + (limit.key.hashCode() and 0xffff), title, text)
        }
    }

    fun authExpired(context: Context) =
        postAlert(context, AUTH_ID, "sign in again", "the claude.ai session expired. tap to sign in.")

    /**
     * Alerts go in a group with a real summary. Android 16 bundles summary-less groups and
     * stray notifications together, which would fold the live notification in with them and
     * replace its glyph with the app icon.
     */
    private fun postAlert(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(id, alertBuilder(context).setContentTitle(title).setContentText(text).build())
        nm.notify(
            ALERT_SUMMARY_ID,
            alertBuilder(context)
                .setContentTitle("limit alerts")
                .setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .build(),
        )
    }

    suspend fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        UsageWidget().updateAll(context)
    }

    /** The limit the status-bar glyph follows. */
    fun driver(limits: List<Limit>, driver: IconDriver): Limit? {
        val h = limits.headline()
        return when (driver) {
            IconDriver.SESSION -> h.session ?: h.weekly
            IconDriver.WEEKLY -> h.weekly ?: h.session
            IconDriver.HIGHEST -> listOfNotNull(h.session, h.weekly).maxByOrNull { it.percent }
        }
    }

    private fun live(context: Context, snap: Snapshot, shown: Limit): Notification {
        val h = snap.limits.headline()
        val title = h.session?.let { s ->
            listOfNotNull("session ${s.percent}%", s.resetsAt?.let { "resets ${Format.resetAt(it)}" }).joinToString(" · ")
        } ?: "${shown.title} ${shown.percent}%"
        val text = snap.limits.filter { it.group == Group.WEEKLY }
            .joinToString(" · ") { "${it.title.removeSuffix(" weekly")} ${it.percent}%" }

        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (snap.stale) "stale" else null)
        if (canPromote(context)) {
            // Android 16 Live Update: the status bar shows a chip with the short text. Some
            // skins (OxygenOS) draw app icons there instead of small icons, so the glyph
            // alone never reaches the status bar. Promotion forbids custom views.
            builder.setSmallIcon(R.drawable.ic_stat_usage)
                .setShortCriticalText(if (shown.percent >= 100) Format.glyph(shown.percent) else "${shown.percent}%")
                .setStyle(progress(shown))
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        } else {
            builder.setSmallIcon(Icon.createWithBitmap(Bitmaps.glyph(Format.glyph(shown.percent))))
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded(context, snap))
        }
        return builder
            .setColor(Palette.LIGHT.ink)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .addAction(Notification.Action.Builder(null as Icon?, "refresh", refreshNow(context)).build())
            .addAction(Notification.Action.Builder(null as Icon?, "open", openApp(context)).build())
            .build()
    }

    private fun canPromote(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    /** Twenty five-point segments, faded past the current percent. */
    private fun progress(limit: Limit): Notification.ProgressStyle {
        val color = if (limit.percent >= Palette.HOT) Palette.DARK.signal else Palette.LIGHT.leader
        return Notification.ProgressStyle()
            .setProgressSegments(List(20) { Notification.ProgressStyle.Segment(5).setColor(color) })
            .setProgress(limit.percent.coerceIn(0, 100))
    }

    private fun expanded(context: Context, snap: Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_expanded)
        ROWS.forEachIndexed { i, ids ->
            val limit = snap.limits.getOrNull(i)
            if (limit == null) {
                views.setViewVisibility(ids.row, View.GONE)
                return@forEachIndexed
            }
            val p = limit.percent
            views.setViewVisibility(ids.row, View.VISIBLE)
            views.setTextViewText(ids.label, limit.title.removeSuffix(" weekly"))
            views.setTextViewText(ids.pct, "$p%")
            if (p >= Palette.HOT) tint(context, views, ids.pct, "setTextColor") { it.signal }
            // White masks tinted per theme: the shade can switch day/night long after posting.
            views.setImageViewBitmap(ids.fill, Bitmaps.segmentBar(p, 400, 20, Color.WHITE, Color.TRANSPARENT))
            views.setImageViewBitmap(ids.empty, Bitmaps.segmentBar(p, 400, 20, Color.TRANSPARENT, Color.WHITE))
            tint(context, views, ids.fill, "setColorFilter") { it.levelColor(p) }
            tint(context, views, ids.empty, "setColorFilter") { it.faint }
        }
        val footer = listOfNotNull(
            snap.fetchedAt?.let { "updated ${Format.clock(it)}" },
            snap.limits.headline().weekly?.resetsAt?.let { "weekly resets ${Format.resetAt(it)}" },
        ).joinToString(" · ")
        views.setTextViewText(R.id.footer, footer)
        return views
    }

    /** Light and dark values resolved by the system at draw time on API 31+, else the current theme's. */
    private fun tint(context: Context, views: RemoteViews, id: Int, method: String, pick: (Palette) -> Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorInt(id, method, pick(Palette.LIGHT), pick(Palette.DARK))
        else views.setInt(id, method, pick(Palette.of(context)))
    }

    private fun alertBuilder(context: Context): Notification.Builder =
        Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_usage)
            .setColor(Palette.LIGHT.signal)
            .setGroup(CHANNEL_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun refreshNow(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RefreshReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
