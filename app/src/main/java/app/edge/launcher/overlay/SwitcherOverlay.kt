package app.edge.launcher.overlay

import android.accessibilityservice.AccessibilityService
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.edge.launcher.data.AppInfo
import app.edge.launcher.data.RecentsRepository
import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService
import app.edge.launcher.service.OverlayWindow
import app.edge.launcher.ui.AppIcon
import app.edge.launcher.ui.EdgeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Right edge, Lomiri style. Dragging in shrinks the current app into a card
 * while the previous app's card slides in; release early to switch, or drag
 * past the threshold to land in the spread of all running apps.
 */
class SwitcherOverlay(private val service: EdgeAccessibilityService) {
    val cards = mutableStateListOf<AppInfo>()
    /** cards[0] is the app that was in front when the gesture began. */
    var hasCurrent by mutableStateOf(false)
        private set
    /** Full-size screenshot of the current app, taken as the finger landed. */
    var currentShot by mutableStateOf<ImageBitmap?>(null)
        private set
    private var currentShotPkg: String? = null

    /** 0 = app full screen, 1 = spread. */
    val t = mutableFloatStateOf(0f)
    val scroll = mutableFloatStateOf(0f)
    /** Progress of a card zooming to full screen when switching. */
    val zoom = mutableFloatStateOf(0f)
    var switching by mutableIntStateOf(-1)
        private set
    /** True once released in the spread; cards are then interactive. */
    var interactive by mutableStateOf(false)
        private set
    private var closeGoesHome = false

    var geometry: SpreadGeometry? = null
    private var job: Job? = null
    private var scrollJob: Job? = null
    private var rawScroll = 0f

    private val scope get() = service.scope
    private val edge get() = service.edge
    val thumbnails get() = service.thumbnails

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { SwitcherContent(this) }
    }.apply { onBack = { closeSpread() } }

    val isShown get() = window.isShown

    private val enterIndex get() = if (hasCurrent) 1 else 0
    private fun longPx() = service.screenWidth() * edge.settings.current.longSwipeFraction
    private fun triggerPx() = edge.settings.current.triggerDistanceDp * service.resources.displayMetrics.density
    private fun maxScroll() = geometry?.maxScroll(cards.size) ?: 0f

    fun onCurrentShot(pkg: String, image: ImageBitmap) {
        currentShotPkg = pkg
        if (!isShown || (hasCurrent && cards.firstOrNull()?.packageName == pkg)) currentShot = image
    }

    fun begin() {
        job?.cancel(); scrollJob?.cancel()
        val fg = edge.recents.foreground.value
        val list = edge.recents.mru.value.mapNotNull { edge.apps.forPackage(it) }
        cards.clear()
        cards.addAll(list)
        hasCurrent = fg != null && fg != RecentsRepository.HOME && list.firstOrNull()?.packageName == fg
        if (!hasCurrent || currentShotPkg != fg) currentShot = null
        t.floatValue = 0f
        scroll.floatValue = 0f
        rawScroll = 0f
        zoom.floatValue = 0f
        switching = -1
        interactive = false
        closeGoesHome = false
        window.show(focusable = false)
    }

    fun drag(d: Float) {
        val long = longPx()
        t.floatValue = (d / long).coerceIn(0f, 1f)
        // Past the threshold, keep dragging to leaf through the hand.
        rawScroll = ((d - long) * 1.6f).coerceIn(0f, maxScroll())
        scroll.floatValue = rawScroll
    }

    fun release(d: Float, velocity: Float) {
        when {
            t.floatValue >= 1f -> enterSpread()
            (d > triggerPx() || velocity > 800f) && cards.size > enterIndex -> switchTo(enterIndex)
            else -> collapse()
        }
    }

    fun cancel() {
        if (t.floatValue >= 1f) enterSpread() else collapse()
    }

    private fun enterSpread() {
        interactive = true
        window.setFocusable(true)
    }

    /** Back to where we started: current app full screen, or nothing. */
    private fun collapse() {
        job?.cancel()
        interactive = false
        job = scope.launch {
            animate(t.floatValue, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 900f)) { v, _ ->
                t.floatValue = v
            }
            hideNow()
        }
    }

    /** Zoom card [i] to full screen and bring its app to the front. */
    fun switchTo(i: Int) {
        if (i !in cards.indices) return
        job?.cancel(); scrollJob?.cancel()
        val app = cards[i]
        switching = i
        interactive = false
        if (!(hasCurrent && i == 0)) edge.apps.launch(app, animate = false)
        job = scope.launch {
            animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { v, _ -> zoom.floatValue = v }
            delay(30)
            hideNow()
        }
    }

    fun closeSpread() {
        when {
            hasCurrent && cards.isNotEmpty() -> switchTo(0)
            closeGoesHome -> {
                hideNow()
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            else -> collapse()
        }
    }

    fun dismiss(app: AppInfo) {
        val index = cards.indexOf(app)
        if (index < 0) return
        if (index == 0 && hasCurrent) {
            // The app behind us can't be stopped while in front; go home on close.
            hasCurrent = false
            closeGoesHome = true
        }
        cards.removeAt(index)
        edge.recents.dismiss(app.packageName)
        thumbnails.remove(app.packageName)
        if (rawScroll > maxScroll()) settleScroll(0f)
        if (cards.isEmpty()) closeSpread()
    }

    fun hideNow() {
        job?.cancel(); scrollJob?.cancel()
        window.hide()
        interactive = false
        switching = -1
    }

    fun detach() = window.detach()

    // --- Scrolling ----------------------------------------------------------

    fun scrollBy(dx: Float) {
        scrollJob?.cancel()
        val slack = 48f * service.resources.displayMetrics.density
        rawScroll = (rawScroll + dx).coerceIn(-slack, maxScroll() + slack)
        scroll.floatValue = rawScroll
    }

    /** Flick: coast by [velocity] px/s, then spring inside the bounds. */
    fun settleScroll(velocity: Float) {
        scrollJob?.cancel()
        val target = (rawScroll + velocity * 0.3f).coerceIn(0f, maxScroll())
        val from = scroll.floatValue
        scrollJob = scope.launch {
            animate(from, target, initialVelocity = velocity, animationSpec = spring(dampingRatio = 1f, stiffness = 120f)) { v, _ ->
                scroll.floatValue = v
            }
        }
        rawScroll = target
    }

    /** Current pose of card [i], combining drag, scroll and switch progress. */
    fun pose(i: Int): CardPose {
        val g = geometry ?: return CardPose(0f, 0f, 1f, 0f, 0f, 0f)
        val n = cards.size
        val target = g.spread(i, n, scroll.floatValue)
        val start = when {
            i < enterIndex -> g.fullscreen().copy(alpha = if (currentShot != null) 1f else 0f)
            i == enterIndex -> target.copy(x = g.w, tile = 0f)
            else -> target.copy(x = g.w + (i - enterIndex) * g.cardW * 0.35f, tile = 0f)
        }
        val tt = t.floatValue
        var p = start.lerp(target, 1f - (1f - tt) * (1f - tt))
        val s = switching
        if (s >= 0) {
            val z = zoom.floatValue
            p = if (i == s) p.lerp(g.fullscreen(), z) else p.copy(alpha = p.alpha * (1f - z), tile = p.tile * (1f - z))
        }
        return p
    }

    fun backgroundAlpha(): Float =
        if (hasCurrent && currentShot != null) 1f else (t.floatValue * 1.5f).coerceIn(0f, 1f)
}

@Composable
private fun SwitcherContent(sw: SwitcherOverlay) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val g = remember(w, h, density) { SpreadGeometry(w, h, density) }
        sw.geometry = g

        // Backdrop, plus spread input: drag to scroll, tap empty space to go back.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = sw.backgroundAlpha() }
                .background(Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF050505))))
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, dx ->
                            if (sw.interactive) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                sw.scrollBy(-dx)
                            }
                        },
                        onDragEnd = { if (sw.interactive) sw.settleScroll(-tracker.calculateVelocity().x) },
                        onDragCancel = { if (sw.interactive) sw.settleScroll(0f) },
                    )
                }
                .pointerInput(Unit) { detectTapGestures { if (sw.interactive) sw.closeSpread() } },
        )

        val thumbsVersion = sw.thumbnails.version.intValue
        sw.cards.forEachIndexed { i, app ->
            key(app.key) {
                val image = if (i == 0 && sw.hasCurrent && sw.currentShot != null) {
                    sw.currentShot
                } else {
                    thumbsVersion.let { sw.thumbnails[app.packageName] }
                }
                SpreadCard(sw, g, i, app, image)
            }
        }
    }
}

@Composable
private fun SpreadCard(sw: SwitcherOverlay, g: SpreadGeometry, i: Int, app: AppInfo, image: ImageBitmap?) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { mutableFloatStateOf(0f) }
    val z = if (sw.switching == i) 1000f else i.toFloat()
    val cardWdp = with(density) { g.cardW.toDp() }
    val cardHdp = with(density) { g.cardH.toDp() }

    // Icon and title above the card.
    Row(
        Modifier
            .zIndex(z)
            .graphicsLayer {
                val p = sw.pose(i)
                translationX = p.x
                translationY = p.cy - g.cardH * p.scale / 2f - g.appInfoH * 0.55f + dragY.floatValue
                alpha = p.tile * p.alpha
            }
            .width(with(density) { (g.cardW * 0.6f).toDp() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Box(
        Modifier
            .zIndex(z)
            .size(cardWdp, cardHdp)
            .graphicsLayer {
                val p = sw.pose(i)
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationX = p.x
                translationY = p.cy - g.cardH / 2f + dragY.floatValue
                scaleX = p.scale
                scaleY = p.scale
                rotationY = p.angle
                cameraDistance = 7f * density.density
                alpha = p.alpha * (1f + dragY.floatValue / g.h * 1.4f).coerceIn(0f, 1f)
                shadowElevation = if (p.scale < 1.1f) 12.dp.toPx() * p.alpha else 0f
            }
            .pointerInput(app.key) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dy ->
                        if (sw.interactive) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragY.floatValue = (dragY.floatValue + dy).coerceAtMost(0f)
                        }
                    },
                    onDragEnd = {
                        val vy = tracker.calculateVelocity().y
                        val from = dragY.floatValue
                        scope.launch {
                            if (from < -g.cardH * 0.25f || vy < -1500f) {
                                animate(from, -g.h, initialVelocity = vy, animationSpec = tween(180)) { v, _ -> dragY.floatValue = v }
                                sw.dismiss(app)
                                dragY.floatValue = 0f
                            } else {
                                animate(from, 0f, initialVelocity = vy, animationSpec = spring(dampingRatio = 0.75f)) { v, _ ->
                                    dragY.floatValue = v
                                }
                            }
                        }
                    },
                    onDragCancel = { dragY.floatValue = 0f },
                )
            }
            .pointerInput(app.key) {
                detectTapGestures { if (sw.interactive) sw.switchTo(sw.cards.indexOf(app)) }
            },
    ) {
        if (image != null) {
            Image(image, contentDescription = app.label, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            IconFace(app)
        }
    }
}

/** Card for an app without a screenshot yet: its icon on a dark wash of its colour. */
@Composable
private fun IconFace(app: AppInfo) {
    val top = lerp(app.color, Color.Black, 0.55f)
    val bottom = lerp(app.color, Color.Black, 0.85f)
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(app.icon, 72.dp)
    }
}

