package app.edge.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.edge.launcher.data.MonthData
import app.edge.launcher.data.ScreenTime
import app.edge.launcher.edge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val TextShadow = Shadow(Color(0x66000000), Offset(0f, 1.5f), blurRadius = 8f)

/** Lomiri greeter layout: clock and date on top, infographic circle below. */
@Composable
fun HomeContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<MonthData?>(null) }
    var resumes by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(resumes) {
        data = withContext(Dispatchers.IO) { ScreenTime.load(context, context.edge.recents) }
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.statusBarsPadding())
        Spacer(Modifier.height(32.dp))
        HomeClock()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) / 1.5f
            Infographic(data, resumes, Modifier.size(side))
        }
    }
}

/** Ubuntu Light clock (7.5gu) and date, updated on the minute. */
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
    val date = Date(now)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            DateFormat.getTimeFormat(context).format(date),
            style = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Light, fontSize = 64.sp, color = Color.White, shadow = TextShadow),
        )
        Text(
            DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMMyyyy"), date).toString(),
            style = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Light, fontSize = 17.sp, color = Color.White, shadow = TextShadow),
        )
    }
}

private val BubbleStart = Color(0xFFF7A06B)
private val BubbleMain = Lomiri.Orange
private val BubbleEnd = Lomiri.Aubergine

/** Three-stop colour along the month, like the greeter's data circles. */
private fun bubbleColor(index: Int, total: Int): Color {
    val p = if (total > 0) index.toFloat() / total else 0f
    return if (p < 0.5f) lerp(BubbleStart, BubbleMain, p * 2f) else lerp(BubbleMain, BubbleEnd, (p - 0.5f) * 2f)
}

/**
 * Ring of one dot per day of the month (past filled, today enlarged, future
 * hollow) with a translucent bubble per day sized by screen time.
 */
@Composable
private fun Infographic(data: MonthData?, key: Int, modifier: Modifier) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(data, key) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(1400, easing = LinearEasing))
    }
    val dotR = with(LocalDensity.current) { 2.4.dp.toPx() }
    val cal = remember(key) { java.util.Calendar.getInstance() }
    val days = data?.daysInMonth ?: cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val today = data?.today ?: cal.get(java.util.Calendar.DAY_OF_MONTH)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f * 0.86f
            val c = center
            fun at(i: Int, n: Int, radius: Float): Offset {
                val a = Math.toRadians(-90.0 + 360.0 * i / n)
                return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
            }
            if (data != null) {
                val peak = max(60L, max(data.thisMonth.maxOrNull() ?: 0L, data.lastMonth.maxOrNull() ?: 0L)).toFloat()
                // Last month: faint white circles behind.
                data.lastMonth.forEachIndexed { i, m ->
                    if (m > 0) drawCircle(Color.White.copy(alpha = 0.07f), r * 0.5f * (m / peak) * grow.value, at(i, data.lastMonth.size, r))
                }
                val n = data.thisMonth.size
                data.thisMonth.forEachIndexed { i, m ->
                    if (m <= 0) return@forEachIndexed
                    // Bubbles grow in one after another, day by day.
                    val local = (grow.value * (today + 3) - i).coerceIn(0f, 1f)
                    drawCircle(bubbleColor(i, today).copy(alpha = 0.32f), r * 0.5f * (m / peak) * local, at(i, n, r))
                }
            }
            for (i in 0 until days) {
                val p = at(i, days, r)
                val day = i + 1
                when {
                    day < today -> drawCircle(Color.White.copy(alpha = 0.9f), dotR, p)
                    day == today -> drawCircle(Color.White, dotR * 2f, p)
                    else -> drawCircle(Color.White.copy(alpha = 0.6f), dotR, p, style = Stroke(width = dotR * 0.5f))
                }
            }
        }
        if (data != null) {
            val h = data.todayMinutes / 60
            val m = data.todayMinutes % 60
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(200.dp)) {
                Text(
                    if (h > 0) "${h}h ${m}m" else "${m}m",
                    style = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Color.White, shadow = TextShadow),
                )
                Text(
                    "screen time today",
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Light, fontSize = 14.sp, color = Color.White, shadow = TextShadow),
                )
            }
        }
    }
}
