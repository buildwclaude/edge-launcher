package app.edge.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val IconShape = RoundedCornerShape(percent = 24)

@Composable
fun AppIcon(icon: ImageBitmap, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    // The squircle is baked into the bitmap, so no per-frame clipping.
    Image(
        bitmap = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

data class SheetAction(val label: String, val onClick: () -> Unit)

/**
 * A bottom action sheet drawn inside the current composition. Overlay windows
 * from the accessibility service can't rely on popups, so menus use this.
 */
@Composable
fun ActionSheet(
    visible: Boolean,
    title: String,
    icon: ImageBitmap?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Lomiri.Scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible,
                enter = slideInVertically(spring(dampingRatio = 0.8f)) { it },
                exit = slideOutVertically { it },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Lomiri.LauncherBg)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding(),
                ) {
                    HorizontalDivider(color = Lomiri.Divider)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            AppIcon(icon, 36.dp)
                            Spacer(Modifier.width(14.dp))
                        }
                        Text(title, color = Lomiri.Text, fontSize = 18.sp, fontWeight = FontWeight.Light)
                    }
                    HorizontalDivider(color = Lomiri.Divider)
                    for (a in actions) {
                        Text(
                            a.label,
                            color = Lomiri.Text,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); a.onClick() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                        HorizontalDivider(color = Lomiri.Divider)
                    }
                }
            }
        }
    }
}
