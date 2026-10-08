package com.yepgent.usage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yepgent.usage.ui.theme.JetBrainsMono
import com.yepgent.usage.ui.theme.Mono
import kotlin.math.roundToInt

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    paths.fold(ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)) { builder, d ->
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Square,
        )
    }.build()

object MonoIcons {
    val Refresh = strokeIcon("refresh", "M20 12a8 8 0 1 1-2.34-5.66", "M20 4v4h-4")
    val Settings = strokeIcon("settings", "M4 7h10M18 7h2M4 17h4M12 17h8", "M14 5h4v4h-4z", "M8 15h4v4h-4z")
    val Back = strokeIcon("back", "M19 12H5", "M11 6l-6 6 6 6")
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    color: Color = Mono.colors.ink,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) = Text(
    text = text,
    modifier = modifier,
    fontSize = size,
    color = color,
    fontWeight = weight,
    fontFamily = JetBrainsMono,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
)

@Composable
fun SquareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Mono.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(1.5.dp, c.ink)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

private fun Modifier.semantics(label: String) = clearAndSetSemantics { contentDescription = label }

@Composable
fun SegmentBar(percent: Int, height: Dp, fill: Color, modifier: Modifier = Modifier, segments: Int = 20, gap: Dp = 3.dp) {
    val filled = (percent.coerceIn(0, 100) / 100f * segments).roundToInt()
    val empty = Mono.colors.faint
    Row(
        modifier = modifier.fillMaxWidth().semantics("$percent percent"),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(segments) { i ->
            Box(Modifier.weight(1f).height(height).background(if (i < filled) fill else empty))
        }
    }
}

/** "label ........ value", the whole row one touch target. */
@Composable
fun LeaderRow(label: String, value: String, onClick: () -> Unit, valueColor: Color = Mono.colors.ink, role: Role = Role.Button) {
    val c = Mono.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = role, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(label)
        Spacer(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(12.dp)
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(
                        color = c.leader,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx())),
                    )
                },
        )
        MonoText(value, weight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.5.dp).background(Mono.colors.ink))
}

@Composable
fun MonoButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val c = Mono.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.5.dp, c.ink)
            .background(if (filled) c.ink else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoText(label, size = 14.sp, weight = FontWeight.Bold, color = if (filled) c.ground else c.ink)
    }
}
