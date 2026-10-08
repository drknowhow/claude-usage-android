package com.yepgent.usage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yepgent.usage.Palette
import com.yepgent.usage.R

data class MonoColors(
    val ground: Color,
    val ink: Color,
    val ink2: Color,
    val faint: Color,
    val signal: Color,
    val leader: Color,
) {
    fun level(percent: Int): Color = if (percent >= Palette.HOT) signal else ink

    companion object {
        fun of(p: Palette) = MonoColors(
            Color(p.ground), Color(p.ink), Color(p.ink2), Color(p.faint), Color(p.signal), Color(p.leader),
        )
    }
}

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val LocalMono = staticCompositionLocalOf { MonoColors.of(Palette.LIGHT) }

object Mono {
    val colors: MonoColors
        @Composable get() = LocalMono.current
}

private fun Typography.mono(): Typography {
    fun TextStyle.m() = copy(fontFamily = JetBrainsMono)
    return copy(
        displayLarge = displayLarge.m(), displayMedium = displayMedium.m(), displaySmall = displaySmall.m(),
        headlineLarge = headlineLarge.m(), headlineMedium = headlineMedium.m(), headlineSmall = headlineSmall.m(),
        titleLarge = titleLarge.m(), titleMedium = titleMedium.m(), titleSmall = titleSmall.m(),
        bodyLarge = bodyLarge.m(), bodyMedium = bodyMedium.m(), bodySmall = bodySmall.m(),
        labelLarge = labelLarge.m(), labelMedium = labelMedium.m(), labelSmall = labelSmall.m(),
    )
}

@Composable
fun MonoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = MonoColors.of(if (dark) Palette.DARK else Palette.LIGHT)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.ink, onPrimary = c.ground,
        secondary = c.ink2, onSecondary = c.ground,
        background = c.ground, onBackground = c.ink,
        surface = c.ground, onSurface = c.ink, surfaceVariant = c.faint, onSurfaceVariant = c.ink2,
        surfaceContainer = c.ground, surfaceContainerHigh = c.ground, surfaceContainerLow = c.ground,
        outline = c.ink, error = c.signal,
    )
    CompositionLocalProvider(LocalMono provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography().mono(), content = content)
    }
}
