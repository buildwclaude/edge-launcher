package app.edge.launcher.overlay

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.edge.launcher.data.AppInfo
import app.edge.launcher.ui.RevealState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** How one launcher item is drawn at the current scroll position. */
private class Fold(
    /** Distance of the item's bottom edge above the bottom of the list, in px. */
    val bottom: Float,
    val angle: Float,
    /** true: fold about the item's bottom edge (near the bottom of the list). */
    val pivotBottom: Boolean,
    val alpha: Float,
    val dark: Float,
)

/**
 * Scroll and drag state of the launcher list. The list is inverted, as on
 * Lomiri phones: item 0 sits just above the home button and the list grows
 * upwards.
 */
@Stable
class LauncherListState {
    val scroll = mutableFloatStateOf(0f)
    var itemH = 1f
    var listH = 1f
    var margin = 0f
    var count = 0
    /** Index of the item being dragged to a new place, or -1. */
    var dragging by mutableIntStateOf(-1)
    /** Finger position (from the list bottom) while dragging. */
    val dragBottom = mutableFloatStateOf(0f)
    var quickList by mutableStateOf<Int?>(null)
    private var job: Job? = null

    fun maxScroll() = max(0f, count * itemH - (listH - 2 * margin))

    fun stop() { job?.cancel() }

    fun scrollBy(dy: Float) {
        val s = scroll.floatValue
        // Rubber-band past either end.
        val over = s < 0f || s > maxScroll()
        scroll.floatValue = s + if (over) dy * 0.4f else dy
    }

    /** Lomiri snaps the list to whole items. */
    fun fling(scope: kotlinx.coroutines.CoroutineScope, velocity: Float) {
        job?.cancel()
        val raw = scroll.floatValue + velocity * 0.22f
        val target = ((raw / itemH).roundToInt() * itemH).coerceIn(0f, maxScroll())
        job = scope.launch {
            animate(scroll.floatValue, target, initialVelocity = velocity, animationSpec = spring(dampingRatio = 1f, stiffness = 260f)) { v, _ ->
                scroll.floatValue = v
            }
        }
    }

    /** Scrolls so item [i] is fully unfolded. */
    fun reveal(scope: kotlinx.coroutines.CoroutineScope, i: Int) {
        val visible = listH - 2 * margin
        val s = scroll.floatValue
        val target = when {
            i * itemH < s + itemH -> (i - 1) * itemH
            (i + 1) * itemH > s + visible - itemH -> (i + 2) * itemH - visible
            else -> s
        }.coerceIn(0f, maxScroll())
        job?.cancel()
        job = scope.launch {
            animate(s, target, animationSpec = spring(dampingRatio = 1f, stiffness = 400f)) { v, _ -> scroll.floatValue = v }
        }
    }

    /** Item under a point [fromBottom] px above the list bottom. */
    fun indexAt(fromBottom: Float): Int =
        floor((fromBottom - margin + scroll.floatValue) / itemH).toInt()

    /**
     * Lomiri-style folding: items that reach either end of the list tilt back
     * (up to 55 degrees), darken, fade, and pile up into the edge.
     */
    fun fold(position: Float, first: Boolean, last: Boolean): Fold {
        val h = itemH
        val b = margin + position * h - scroll.floatValue
        val dBottom = b - margin
        val dTop = (listH - margin) - (b + h)
        val nearBottom = dBottom <= dTop
        val d = if (nearBottom) dBottom else dTop
        val end = first || last
        val angle: Float
        val alpha: Float
        val dark: Float
        var shownBottom = b
        when {
            d >= h || (end && d >= 0f) -> { angle = 0f; alpha = 1f; dark = 0f }
            d >= 0f -> {
                val f = 1f - d / h
                angle = 50f * f
                alpha = 1f - 0.25f * f
                dark = 0.3f * f
            }
            else -> {
                val over = (-d / h).coerceIn(0f, 1f)
                angle = if (end) 55f * over else 50f + 5f * over
                alpha = ((if (end) 1f else 0.75f) * (1f + d / (2f * h))).coerceIn(0f, 1f)
                dark = if (end) 0.3f * over else 0.3f
                // Pile folded items up at the edge instead of sliding them away.
                val squeezed = d * 0.28f
                shownBottom = if (nearBottom) margin + squeezed else (listH - margin) - squeezed - h
            }
        }
        return Fold(shownBottom, angle, nearBottom, alpha, dark)
    }
}

/**
 * The scrolling, folding icon column of the Lomiri launcher. One gesture
 * handler covers the whole list so scrolling, tapping, long-press quicklists,
 * drag-to-reorder and swiping the launcher away never fight each other.
 */
@Composable
fun LauncherList(
    items: List<AppInfo>,
    iconSize: Dp,
    panelWidth: Dp,
    running: Set<String>,
    focused: String?,
    reveal: RevealState,
    state: LauncherListState,
    onLaunch: (AppInfo) -> Unit,
    onDrop: (app: AppInfo, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val itemHdp = iconSize * 15f / 16f + 8.dp

    BoxWithConstraints(modifier.clipToBounds()) {
        val listH = constraints.maxHeight.toFloat()
        val itemH = with(density) { itemHdp.toPx() }
        state.itemH = itemH
        state.listH = listH
        state.margin = with(density) { panelWidth.toPx() } * 0.15f
        state.count = items.size

        // While dragging, the other icons make room for the one being moved.
        val hover = if (state.dragging >= 0) {
            state.indexAt(state.dragBottom.floatValue).coerceIn(0, items.lastIndex)
        } else {
            -1
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(items) {
                    val slop = viewConfiguration.touchSlop
                    val longPress = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.stop()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val index = state.indexAt(listH - down.position.y)
                        var outcome = 0 // 0 = long press, 1 = tap, 2 = scroll, 3 = horizontal, 4 = cancel
                        withTimeoutOrNull(longPress) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null) { outcome = 4; break }
                                tracker.addPosition(c.uptimeMillis, c.position)
                                if (!c.pressed) { outcome = 1; break }
                                val d = c.position - down.position
                                if (abs(d.y) > slop && abs(d.y) >= abs(d.x)) { outcome = 2; break }
                                if (abs(d.x) > slop) { outcome = 3; break }
                            }
                        } ?: run { outcome = 0 }

                        when (outcome) {
                            1 -> if (index in items.indices) {
                                val f = state.fold(index.toFloat(), index == 0, index == items.lastIndex)
                                if (f.angle > 30f) state.reveal(scope, index) else onLaunch(items[index])
                            }
                            2 -> {
                                var last = down.position
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(c.uptimeMillis, c.position)
                                    // Inverted list: finger up moves the icons up.
                                    state.scrollBy(c.position.y - last.y)
                                    last = c.position
                                    c.consume()
                                    if (!c.pressed) break
                                }
                                state.fling(scope, tracker.calculateVelocity().y)
                            }
                            3 -> {
                                reveal.beginDrag()
                                var last = down.position
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(c.uptimeMillis, c.position)
                                    reveal.dragBy(c.position.x - last.x)
                                    last = c.position
                                    c.consume()
                                    if (!c.pressed) break
                                }
                                reveal.settle(tracker.calculateVelocity().x)
                            }
                            0 -> if (index in items.indices) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.quickList = index
                                var dragging = false
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!dragging && abs(c.position.y - down.position.y) > slop * 1.5f) {
                                        dragging = true
                                        state.quickList = null
                                        state.dragging = index
                                    }
                                    if (dragging) state.dragBottom.floatValue = listH - c.position.y
                                    c.consume()
                                    if (!c.pressed) break
                                }
                                if (dragging) {
                                    val to = state.indexAt(state.dragBottom.floatValue).coerceIn(0, items.lastIndex)
                                    onDrop(items[index], to)
                                    state.dragging = -1
                                }
                            }
                        }
                    }
                },
        ) {
            items.forEachIndexed { i, app ->
                key(app.key) {
                    val virtual = when {
                        hover < 0 || i == state.dragging -> i
                        i > state.dragging && i <= hover -> i - 1
                        i < state.dragging && i >= hover -> i + 1
                        else -> i
                    }
                    val animated by animateFloatAsState(virtual.toFloat(), spring(dampingRatio = 0.85f, stiffness = 500f), label = "slot")
                    LauncherItem(
                        app = app,
                        index = i,
                        position = { if (i == state.dragging) -100f else animated },
                        count = items.size,
                        state = state,
                        iconSize = iconSize,
                        panelWidth = panelWidth,
                        itemHdp = itemHdp,
                        running = app.packageName in running,
                        focused = app.packageName == focused,
                    )
                }
            }
            // The icon under the finger while reordering.
            val dragged = state.dragging
            if (dragged in items.indices) {
                val app = items[dragged]
                Box(
                    Modifier
                        .size(panelWidth, itemHdp)
                        .offset { IntOffset(0, (listH - state.dragBottom.floatValue - itemH / 2f).roundToInt()) }
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.95f },
                ) {
                    IconImage(app, iconSize, dark = { 0f })
                }
            }
        }
    }
}

@Composable
private fun LauncherItem(
    app: AppInfo,
    index: Int,
    position: () -> Float,
    count: Int,
    state: LauncherListState,
    iconSize: Dp,
    panelWidth: Dp,
    itemHdp: Dp,
    running: Boolean,
    focused: Boolean,
) {
    val first = index == 0
    val last = index == count - 1
    Box(
        Modifier
            .size(panelWidth, itemHdp)
            .offset {
                val f = state.fold(position(), first, last)
                IntOffset(0, (state.listH - f.bottom - state.itemH).roundToInt())
            }
            .graphicsLayer {
                val f = state.fold(position(), first, last)
                transformOrigin = TransformOrigin(0.5f, if (f.pivotBottom) 1f else 0f)
                rotationX = if (f.pivotBottom) f.angle else -f.angle
                cameraDistance = 6f * density
                alpha = f.alpha
            },
    ) {
        IconImage(app, iconSize, dark = { state.fold(position(), first, last).dark })
        // Running pip on the left, focused pip on the right (0.25 x 0.5 gu).
        if (running) Pip(Modifier.offset(x = 2.dp, y = itemHdp / 2 - 2.dp))
        if (focused) Pip(Modifier.offset(x = panelWidth - 4.dp, y = itemHdp / 2 - 2.dp))
    }
}

/** The icon, darkened in the draw phase so folding never recomposes. */
@Composable
private fun IconImage(app: AppInfo, iconSize: Dp, dark: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val s = iconSize.toPx()
                val topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f)
                val d = dark()
                // Soft drop shadow under the shape, as with Lomiri's DropShadow aspect.
                drawRoundRect(
                    Color(0x40000000),
                    topLeft = topLeft + Offset(0f, 1.5.dp.toPx()),
                    size = Size(s, s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.26f),
                )
                drawImage(
                    app.icon,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                    colorFilter = if (d > 0.005f) ColorFilter.tint(Color.Black.copy(alpha = d), BlendMode.SrcAtop) else null,
                )
            },
    )
}

@Composable
private fun Pip(modifier: Modifier) {
    Box(modifier.size(2.dp, 4.dp).drawBehind { drawRect(Color.White) })
}

