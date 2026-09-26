package app.edge.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Suru switch: a rounded-rectangle track with a square thumb. */
@Composable
fun SuruSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 900f), label = "sw")
    val track by animateColorAsState(if (checked) Lomiri.Orange else Lomiri.Slate, label = "track")
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .background(if (enabled) track else track.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = 18.dp * pos)
                .size(22.dp)
                .background(Color.White, RoundedCornerShape(4.dp)),
        )
    }
}

/** Suru slider: thin track, orange fill, round white thumb. [value] is 0..1. */
@Composable
fun SuruSlider(value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging > 0f) local else value
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val v = (o.x / size.width).coerceIn(0f, 1f)
                    local = v
                    change(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = 1f; local = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { c, _ ->
                        local = (c.position.x / size.width).coerceIn(0f, 1f)
                        change(local)
                    },
                    onDragEnd = { dragging = 0f },
                    onDragCancel = { dragging = 0f },
                )
            },
    ) {
        val r = 10.dp.toPx()
        val cy = size.height / 2
        val track = 4.dp.toPx()
        val x = r + (size.width - 2 * r) * shown
        drawRoundRect(Lomiri.Slate, Offset(r, cy - track / 2), Size(size.width - 2 * r, track), CornerRadius(track / 2))
        drawRoundRect(Lomiri.Orange, Offset(r, cy - track / 2), Size(x - r, track), CornerRadius(track / 2))
        drawCircle(Color(0x33000000), r + 1.5f, Offset(x, cy + 1.5f))
        drawCircle(Color.White, r, Offset(x, cy))
    }
}

/** A Lomiri menu row: title, optional subtitle, trailing content, hairline divider. */
@Composable
fun MenuItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Lomiri.Silk, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Lomiri.Text, fontSize = 16.sp)
                if (subtitle != null) Text(subtitle, color = Lomiri.TextDim, fontSize = 13.sp)
            }
            trailing()
        }
        MenuDivider()
    }
}

@Composable
fun MenuDivider() = HorizontalDivider(color = Lomiri.Divider, thickness = 1.dp)

/** Slider row with icons at both ends, as in the sound and brightness indicators. */
@Composable
fun MenuSlider(value: Float, low: ImageVector, high: ImageVector, onChange: (Float) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(low, contentDescription = null, tint = Lomiri.Silk, modifier = Modifier.size(20.dp))
            SuruSlider(value, Modifier.weight(1f).padding(horizontal = 10.dp), onChange)
            Icon(high, contentDescription = null, tint = Lomiri.Silk, modifier = Modifier.size(22.dp))
        }
        MenuDivider()
    }
}
