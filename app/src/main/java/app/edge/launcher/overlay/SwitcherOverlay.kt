package app.edge.launcher.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.edge.launcher.data.AppInfo
import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService
import app.edge.launcher.service.OverlayWindow
import app.edge.launcher.ui.AppIcon
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Right edge. A short swipe switches to the previous app; dragging past the
 * long-swipe threshold opens the Lomiri-style spread of running apps.
 */
class SwitcherOverlay(private val service: EdgeAccessibilityService) {
    enum class Mode { Idle, Peek, Spread }

    var mode by mutableStateOf(Mode.Idle)
        private set
    var distance by mutableFloatStateOf(0f)
        private set
    var peek by mutableStateOf<AppInfo?>(null)
        private set
    val cards = mutableStateListOf<AppInfo>()
    val spreadIn = Animatable(0f)
    val scroll = Animatable(0f)

    /** Set by the UI once it knows the card geometry. */
    var stepPx = 1f
    var maxScroll = 0f
    private var spreadBase = 0f
    private var rawScroll = 0f

    private val scope get() = service.scope
    private val edge get() = service.edge

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { SwitcherContent(this) }
    }.apply { onBack = { close() } }

    private fun longPx() = service.screenWidth() * edge.settings.current.longSwipeFraction
    private fun triggerPx() = edge.settings.current.triggerDistanceDp * service.resources.displayMetrics.density

    fun begin() {
        cards.clear()
        cards.addAll(edge.recents.mru.value.mapNotNull { edge.apps.forPackage(it) })
        peek = edge.recents.previousApp()
        mode = Mode.Peek
        distance = 0f
        rawScroll = 0f
        scope.launch { spreadIn.snapTo(0f) }
        scope.launch { scroll.snapTo(0f) }
        window.show(focusable = false)
    }

    fun drag(d: Float) {
        distance = d
        if (mode == Mode.Peek && d > longPx()) {
            mode = Mode.Spread
            spreadBase = d
            scope.launch { spreadIn.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow)) }
        }
        if (mode == Mode.Spread) {
            // Keep dragging to leaf through the hand.
            rawScroll = ((d - spreadBase) * 1.4f).coerceIn(0f, maxScroll)
            val v = rawScroll
            scope.launch { scroll.snapTo(v) }
        }
    }

    fun release(d: Float, velocity: Float) {
        when (mode) {
            Mode.Spread -> {
                window.setFocusable(true)
                snapToCard()
            }
            Mode.Peek -> {
                val target = peek
                hideNow()
                if (target != null && (d > triggerPx() || velocity > 800f)) edge.apps.launch(target)
            }
            Mode.Idle -> Unit
        }
    }

    fun cancel() {
        if (mode == Mode.Spread) window.setFocusable(true) else hideNow()
    }

    fun close() {
        scope.launch {
            spreadIn.animateTo(0f, tween(160))
            hideNow()
        }
    }

    fun hideNow() {
        window.hide()
        mode = Mode.Idle
    }

    fun launch(app: AppInfo) {
        hideNow()
        edge.apps.launch(app)
    }

    fun dismiss(app: AppInfo) {
        cards.remove(app)
        edge.recents.dismiss(app.packageName)
        maxScroll = max(0f, (cards.size - 1) * stepPx)
        if (rawScroll > maxScroll) {
            rawScroll = maxScroll
            scope.launch { scroll.animateTo(maxScroll, spring()) }
        }
        if (cards.isEmpty()) close()
    }

    // --- Scrolling ----------------------------------------------------------

    fun stopScroll() {
        rawScroll = scroll.value
        scope.launch { scroll.stop() }
    }

    fun scrollBy(dx: Float) {
        // Rubber-band slightly past the ends.
        rawScroll = (rawScroll + dx).coerceIn(-stepPx * 0.3f, maxScroll + stepPx * 0.3f)
        val v = rawScroll
        scope.launch { scroll.snapTo(v) }
    }

    /** Throws the hand by [velocity] px/s and settles on the nearest card. */
    fun fling(velocity: Float) {
        val target = ((scroll.value + velocity * 0.3f) / stepPx).roundToInt() * stepPx
        rawScroll = target.coerceIn(0f, maxScroll)
        val v = rawScroll
        scope.launch {
            scroll.animateTo(v, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), initialVelocity = velocity)
        }
    }

    private fun snapToCard() {
        rawScroll = ((scroll.value / stepPx).roundToInt() * stepPx).coerceIn(0f, maxScroll)
        val v = rawScroll
        scope.launch { scroll.animateTo(v, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
    }
}

@Composable
private fun SwitcherContent(sw: SwitcherOverlay) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val cardW = w * 0.62f
        val cardH = min(cardW * h / w * 0.92f, h * 0.62f)
        val step = cardW * 0.46f
        sw.stepPx = step
        sw.maxScroll = max(0f, (sw.cards.size - 1) * step)

        // Scrim: follows the finger in peek mode, deepens as the spread opens.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (sw.mode == SwitcherOverlay.Mode.Peek) {
                        (sw.distance / w).coerceIn(0f, 0.45f)
                    } else {
                        0.45f + 0.35f * sw.spreadIn.value
                    }
                }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { sw.close() } },
        )

        when (sw.mode) {
            SwitcherOverlay.Mode.Peek -> {
                val app = sw.peek
                val cardWdp = with(density) { cardW.toDp() }
                val cardHdp = with(density) { cardH.toDp() }
                Box(
                    Modifier
                        .offset { IntOffset((w - sw.distance * 1.15f).roundToInt(), ((h - cardH) / 2f).roundToInt()) }
                        .size(cardWdp, cardHdp),
                ) {
                    if (app != null) {
                        CardFace(app, Modifier.fillMaxSize())
                    } else {
                        Text("No recent apps", color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterStart))
                    }
                }
            }
            SwitcherOverlay.Mode.Spread -> Spread(sw, w, h, cardW, cardH, step)
            SwitcherOverlay.Mode.Idle -> Unit
        }
    }
}

@Composable
private fun Spread(sw: SwitcherOverlay, w: Float, h: Float, cardW: Float, cardH: Float, step: Float) {
    val density = LocalDensity.current
    val margin = w * 0.07f
    val rightLimit = w - cardW * 0.42f
    val headerPx = with(density) { 36.dp.toPx() }
    val top = (h - cardH) / 2f + headerPx * 0.5f
    val n = sw.cards.size

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); sw.stopScroll() },
                    onHorizontalDrag = { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        sw.scrollBy(-dx * 1.3f)
                    },
                    onDragEnd = { sw.fling(-tracker.calculateVelocity().x * 1.3f) },
                    onDragCancel = { sw.fling(0f) },
                )
            }
            .pointerInput(Unit) { detectTapGestures { sw.close() } },
    ) {
        if (n == 0) {
            Text(
                "No recent apps",
                color = Lomiri.TextDim,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        sw.cards.forEachIndexed { i, app ->
            key(app.key) {
                // Logical position along the hand; compressed at both ends.
                val s = i * step - sw.scroll.value
                var x = if (s < 0f) margin + s * 0.1f else margin + s
                if (x > rightLimit) x = rightLimit + (x - rightLimit) * 0.12f
                val enter = (1f - sw.spreadIn.value) * w * (0.6f + i * 0.12f)
                val fade = if (s < 0f) (1f + s / (step * 2.5f)).coerceIn(0f, 1f) else 1f
                // Cards leaving to the left sink under the rest; the rest stack left-on-top.
                val z = if (s < -step * 0.5f) (i - 2 * n).toFloat() else (n - i).toFloat()
                SpreadCard(
                    sw = sw,
                    app = app,
                    x = x + enter,
                    y = top - headerPx,
                    cardW = cardW,
                    cardH = cardH,
                    headerW = step,
                    screenH = h,
                    alpha = fade * sw.spreadIn.value.coerceIn(0f, 1f),
                    modifier = Modifier.zIndex(z),
                )
            }
        }
        Text(
            "Swipe a card up to close it",
            color = Lomiri.TextDim,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = sw.spreadIn.value },
        )
    }
}

@Composable
private fun SpreadCard(
    sw: SwitcherOverlay,
    app: AppInfo,
    x: Float,
    y: Float,
    cardW: Float,
    cardH: Float,
    headerW: Float,
    screenH: Float,
    alpha: Float,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    val headerDp = 36.dp
    val cardWdp = with(density) { cardW.toDp() }
    val cardHdp = with(density) { cardH.toDp() }
    val headerWdp = with(density) { (headerW - 8.dp.toPx()).coerceAtLeast(40f).toDp() }

    Column(
        modifier
            .offset { IntOffset(x.roundToInt(), (y + dragY.value).roundToInt()) }
            .graphicsLayer {
                this.alpha = alpha * (1f + dragY.value / screenH * 1.6f).coerceIn(0f, 1f)
                rotationY = -9f
                cameraDistance = 14f * density.density
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .pointerInput(app.key) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking(); rawY = dragY.value },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        rawY = min(0f, rawY + dy)
                        val v = rawY
                        scope.launch { dragY.snapTo(v) }
                    },
                    onDragEnd = {
                        val vy = tracker.calculateVelocity().y
                        scope.launch {
                            if (rawY < -cardH * 0.3f || vy < -1500f) {
                                dragY.animateTo(-screenH, tween(180))
                                sw.dismiss(app)
                            } else {
                                dragY.animateTo(0f, spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f, spring()) } },
                )
            }
            .pointerInput(app.key) { detectTapGestures { sw.launch(app) } },
    ) {
        Row(
            Modifier.width(headerWdp).height(headerDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CardFace(app, Modifier.size(cardWdp, cardHdp))
    }
}

/** App thumbnails aren't available without root, so cards show the app's icon on its own colour. */
@Composable
private fun CardFace(app: AppInfo, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val top = lerp(app.color, Color.Black, 0.35f)
    val bottom = lerp(app.color, Color.Black, 0.75f)
    Box(
        modifier
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(app.icon, 80.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
