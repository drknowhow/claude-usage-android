package com.yepgent.usage.surfaces

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.min
import kotlin.math.roundToInt

object Bitmaps {
    private const val ICON_PX = 96

    /**
     * White text on transparent, sized to fill a status-bar icon. The system uses only the
     * alpha channel and tints it, so colour here is irrelevant.
     */
    fun glyph(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 100f
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        paint.textSize *= min(ICON_PX * 0.98f / paint.measureText(text), ICON_PX * 0.80f / bounds.height())
        paint.getTextBounds(text, 0, text.length, bounds)
        Canvas(bitmap).drawText(text, ICON_PX / 2f, ICON_PX / 2f + bounds.height() / 2f, paint)
        return bitmap
    }

    /** A row of [segments] cells, the first `percent` of them in [fill]. */
    fun segmentBar(percent: Int, widthPx: Int, heightPx: Int, fill: Int, empty: Int, segments: Int = 20): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(segments * 2), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val gap = (bitmap.width / segments * 0.18f).coerceAtLeast(1f)
        val cell = (bitmap.width - gap * (segments - 1)) / segments
        val filled = (percent.coerceIn(0, 100) / 100f * segments).roundToInt()
        for (i in 0 until segments) {
            paint.color = if (i < filled) fill else empty
            val left = i * (cell + gap)
            canvas.drawRect(left, 0f, left + cell, bitmap.height.toFloat(), paint)
        }
        return bitmap
    }
}
