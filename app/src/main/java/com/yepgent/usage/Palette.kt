package com.yepgent.usage

import android.content.Context
import android.content.res.Configuration

/** The Mono direction's colours, as ARGB ints so views, bitmaps and Compose share them. */
data class Palette(
    val ground: Int,
    val ink: Int,
    val ink2: Int,
    val faint: Int,
    val signal: Int,
    val leader: Int,
) {
    /** Usage at or above this percent is drawn in [signal]. */
    fun levelColor(percent: Int): Int = if (percent >= HOT) signal else ink

    companion object {
        const val HOT = 70

        val LIGHT = Palette(
            ground = 0xFFECEBE6.toInt(),
            ink = 0xFF111111.toInt(),
            ink2 = 0xFF4A4A46.toInt(),
            faint = 0xFFCFCDC5.toInt(),
            signal = 0xFFA8370A.toInt(),
            leader = 0xFF8A8880.toInt(),
        )

        val DARK = Palette(
            ground = 0xFF121211.toInt(),
            ink = 0xFFECEBE6.toInt(),
            ink2 = 0xFFA9A79F.toInt(),
            faint = 0xFF34332F.toInt(),
            signal = 0xFFF07A3C.toInt(),
            leader = 0xFF6F6D66.toInt(),
        )

        fun of(context: Context): Palette {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}
