package app.edge.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

private val TextShadow = Shadow(Color(0x88000000), Offset(0f, 2f), blurRadius = 12f)

/** Large centred clock and date, updated on the minute. */
@Composable
fun HomeClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val locale = Locale.getDefault()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    val date = Date(now)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            DateFormat.format(timePattern, date).toString(),
            style = TextStyle(
                fontFamily = Ubuntu,
                fontWeight = FontWeight.Light,
                fontSize = 96.sp,
                color = Color.White,
                shadow = TextShadow,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            DateFormat.format(datePattern, date).toString(),
            style = TextStyle(
                fontFamily = Ubuntu,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                color = Color.White,
                shadow = TextShadow,
            ),
        )
    }
}
