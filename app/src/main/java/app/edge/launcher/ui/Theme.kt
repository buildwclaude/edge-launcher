package app.edge.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.edge.launcher.R

object Lomiri {
    val Orange = Color(0xFFE95420)
    val Scrim = Color(0x99000000)
    val Panel = Color(0xE61A1A1A)
    val PanelLight = Color(0x33FFFFFF)
    val Card = Color(0xFF2C2C2C)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xB3F5F5F5)
}

val Ubuntu = FontFamily(
    Font(R.font.ubuntu_light, FontWeight.Light),
    Font(R.font.ubuntu_regular, FontWeight.Normal),
    Font(R.font.ubuntu_medium, FontWeight.Medium),
    Font(R.font.ubuntu_bold, FontWeight.Bold),
)

private fun TextStyle.ubuntu() = copy(fontFamily = Ubuntu)

private val EdgeTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.ubuntu(), displayMedium = displayMedium.ubuntu(),
        displaySmall = displaySmall.ubuntu(), headlineLarge = headlineLarge.ubuntu(),
        headlineMedium = headlineMedium.ubuntu(), headlineSmall = headlineSmall.ubuntu(),
        titleLarge = titleLarge.ubuntu(), titleMedium = titleMedium.ubuntu(),
        titleSmall = titleSmall.ubuntu(), bodyLarge = bodyLarge.ubuntu(),
        bodyMedium = bodyMedium.ubuntu(), bodySmall = bodySmall.ubuntu(),
        labelLarge = labelLarge.ubuntu(), labelMedium = labelMedium.ubuntu(),
        labelSmall = labelSmall.ubuntu(),
    )
}

private val EdgeColors = darkColorScheme(
    primary = Lomiri.Orange,
    onPrimary = Color.White,
    secondary = Lomiri.Orange,
    background = Color(0xFF111111),
    onBackground = Lomiri.Text,
    surface = Color(0xFF1E1E1E),
    onSurface = Lomiri.Text,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Lomiri.TextDim,
)

@Composable
fun EdgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EdgeColors, typography = EdgeTypography, content = content)
}
