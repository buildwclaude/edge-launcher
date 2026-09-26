package app.edge.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Lomiri.Orange,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF232323)),
    ) { content() }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Lomiri.Text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Lomiri.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Lomiri.Orange, checkedThumbColor = Color.White),
        )
    }
}

private val sliderColors @Composable get() = SliderDefaults.colors(
    thumbColor = Lomiri.Orange,
    activeTrackColor = Lomiri.Orange,
    inactiveTrackColor = Color(0xFF444444),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Lomiri.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(format(value), color = Lomiri.TextDim, fontSize = 14.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps, colors = sliderColors)
    }
}

@Composable
fun RangeRow(
    title: String,
    value: ClosedFloatingPointRange<Float>,
    range: ClosedFloatingPointRange<Float>,
    format: (ClosedFloatingPointRange<Float>) -> String,
    onChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Lomiri.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(format(value), color = Lomiri.TextDim, fontSize = 14.sp)
        }
        RangeSlider(value = value, onValueChange = onChange, valueRange = range, colors = sliderColors)
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = Lomiri.Text, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = Lomiri.TextDim, fontSize = 13.sp)
    }
}
