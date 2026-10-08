package com.yepgent.usage.surfaces

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
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

    private val ROWS = listOf(
        Triple(R.id.row0, R.id.label0, R.id.pct0) to R.id.bar0,
        Triple(R.id.row1, R.id.label1, R.id.pct1) to R.id.bar1,
        Triple(R.id.row2, R.id.label2, R.id.pct2) to R.id.bar2,
        Triple(R.id.row3, R.id.label3, R.id.pct3) to R.id.bar3,
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

        return Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(Icon.createWithBitmap(Bitmaps.glyph(Format.glyph(shown.percent))))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (snap.stale) "stale" else null)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomBigContentView(expanded(context, snap))
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

    private fun expanded(context: Context, snap: Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_expanded)
        val palette = Palette.of(context)
        ROWS.forEachIndexed { i, (ids, barId) ->
            val (rowId, labelId, pctId) = ids
            val limit = snap.limits.getOrNull(i)
            if (limit == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(labelId, limit.title.removeSuffix(" weekly"))
            views.setTextViewText(pctId, "${limit.percent}%")
            views.setTextColor(pctId, palette.levelColor(limit.percent))
            views.setImageViewBitmap(barId, Bitmaps.segmentBar(limit.percent, 400, 20, palette.levelColor(limit.percent), palette.faint))
        }
        val footer = listOfNotNull(
            snap.fetchedAt?.let { "updated ${Format.clock(it)}" },
            snap.limits.headline().weekly?.resetsAt?.let { "weekly resets ${Format.resetAt(it)}" },
        ).joinToString(" · ")
        views.setTextViewText(R.id.footer, footer)
        return views
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
