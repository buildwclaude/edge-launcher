package app.edge.launcher.overlay

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.edge.launcher.data.AppInfo
import app.edge.launcher.data.EdgeSettings
import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService
import app.edge.launcher.service.OverlayWindow
import app.edge.launcher.ui.ActionSheet
import app.edge.launcher.ui.AppIcon
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.RevealState
import app.edge.launcher.ui.SheetAction

/** Lomiri launcher: a full-height panel that slides in from the left edge. */
class DockOverlay(private val service: EdgeAccessibilityService) {
    val reveal = RevealState(service.scope) { open ->
        if (open) window.setFocusable(true) else window.hide()
    }
    /** 0..1: how close a long left swipe is to "go home" (when that mode is on). */
    var homeHint by mutableFloatStateOf(0f)
        private set
    private var last = 0f

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { LauncherContent(this) }
    }.apply { onBack = { close() } }

    val isShown get() = window.isShown

    private val settings: EdgeSettings get() = service.edge.settings.current
    private val density get() = service.resources.displayMetrics.density

    /** Lomiri: icons take 75% of the panel width. */
    fun widthPx() = settings.dockIconDp / 0.75f * density
    private fun homeDistance() = service.screenWidth() * settings.longSwipeFraction.coerceAtLeast(0.25f) + widthPx()

    fun begin() {
        reveal.extentPx = widthPx()
        window.show(focusable = false)
        reveal.beginDrag()
        last = 0f
        homeHint = 0f
    }

    fun drag(distance: Float) {
        reveal.dragBy(distance - last)
        last = distance
        if (!settings.longLeftDrawer) {
            val w = widthPx()
            homeHint = ((distance - w * 1.5f) / (homeDistance() - w * 1.5f)).coerceIn(0f, 1f)
        }
    }

    fun release(distance: Float, velocity: Float) {
        if (homeHint >= 1f) goHome() else reveal.settle(velocity)
        homeHint = 0f
    }

    fun cancel() {
        homeHint = 0f
        reveal.animateTo(false)
    }

    fun close() { reveal.animateTo(false) }

    fun hideNow() {
        homeHint = 0f
        reveal.snapClosed()
        window.hide()
    }

    fun detach() = window.detach()

    fun goHome() {
        hideNow()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun openDrawer() {
        service.drawer.open(fromLeft = true)
        // The drawer window sits above the launcher; drop the launcher once it's covered.
        reveal.animateTo(false)
    }

    fun launch(app: AppInfo) {
        service.edge.apps.launch(app)
        close()
    }

    /** Drag-to-reorder: dropping at [index] pins the app there. */
    fun drop(app: AppInfo, index: Int, pinned: List<String>) {
        val list = pinned.toMutableList()
        list.remove(app.key)
        list.add(index.coerceIn(0, list.size), app.key)
        service.edge.settings.setPinned(list)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherContent(dock: DockOverlay) {
    val edge = LocalContext.current.edge
    val settings by edge.settings.settings.collectAsState()
    val apps by edge.apps.apps.collectAsState()
    val mru by edge.recents.mru.collectAsState()
    val foreground by edge.recents.foreground.collectAsState()
    val s = settings ?: EdgeSettings()
    // Lomiri: icons are 75% of the panel width (8gu panel, 6gu icons by default).
    val iconSize = s.dockIconDp.dp
    val panelWidth = iconSize / 0.75f
    val listState = remember { LauncherListState() }
    var listTop by remember { mutableFloatStateOf(0f) }

    val pinnedApps = remember(s.pinned, apps) { s.pinned.mapNotNull { edge.apps.byKey(it) } }
    val runningApps = remember(mru, apps, pinnedApps) {
        val pinnedPkgs = pinnedApps.map { it.packageName }.toSet()
        mru.filter { it !in pinnedPkgs }.mapNotNull { edge.apps.forPackage(it) }
    }
    val items = remember(pinnedApps, runningApps) { pinnedApps + runningApps }
    val running = remember(mru) { mru.toSet() }
    val reveal = dock.reveal

    // Start at the bottom, closed quicklist, each time the launcher appears.
    LaunchedEffect(reveal.isShown) {
        if (!reveal.isShown) {
            listState.quickList = null
            listState.dragging = -1
            listState.stop()
            listState.scroll.floatValue = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Outside the panel: tap to hide, or push the launcher back to the edge.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dock.homeHint * 0.5f }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { if (listState.quickList != null) listState.quickList = null else dock.close() } }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking(); reveal.beginDrag() },
                        onHorizontalDrag = { change, dx ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            reveal.dragBy(dx)
                        },
                        onDragEnd = { reveal.settle(tracker.calculateVelocity().x) },
                        onDragCancel = { reveal.settle(0f) },
                    )
                },
        )
        if (dock.homeHint > 0f) {
            Text(
                "Release for Home",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = dock.homeHint },
            )
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(panelWidth + 12.dp)
                .graphicsLayer { translationX = (reveal.value - 1f) * size.width },
        ) {
            // Drop shadow along the panel's right edge.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .offset(x = panelWidth)
                    .background(Brush.horizontalGradient(listOf(Color(0x66000000), Color.Transparent))),
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Lomiri.LauncherBg)
                    .statusBarsPadding(),
            ) {
                LauncherList(
                    items = items,
                    iconSize = iconSize,
                    panelWidth = panelWidth,
                    running = running,
                    focused = foreground,
                    reveal = reveal,
                    state = listState,
                    onLaunch = { dock.launch(it) },
                    onDrop = { app, index -> dock.drop(app, index, pinnedApps.map { it.key }) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { listTop = it.positionInRoot().y },
                )
                Spacer(Modifier.height(4.dp))
                // Home button at the bottom: phones run the Lomiri launcher inverted.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(panelWidth * 0.9f)
                        .background(Lomiri.Orange)
                        .combinedClickable(onClick = { dock.openDrawer() }, onLongClick = { dock.goHome() }),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeGlyph(panelWidth * 0.5f)
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        val q = listState.quickList
        if (q != null && q in items.indices) {
            val app = items[q]
            QuickList(
                app = app,
                anchorY = {
                    val f = listState.fold(q.toFloat(), q == 0, q == items.lastIndex)
                    listTop + listState.listH - f.bottom - listState.itemH / 2f
                },
                panelWidth = panelWidth,
                entries = buildList {
                    add(QuickEntry(app.label, bold = true) { dock.launch(app) })
                    if (app.key in s.pinned) add(QuickEntry("Unpin shortcut") { edge.settings.unpin(app.key) })
                    else add(QuickEntry("Pin shortcut") { edge.settings.pin(app.key) })
                    add(QuickEntry("App info") { dock.hideNow(); edge.apps.openAppInfo(app) })
                    if (app.packageName in running) add(QuickEntry("Quit") { edge.recents.dismiss(app.packageName) })
                },
                onDismiss = { listState.quickList = null },
            )
        }
    }
}

private class QuickEntry(val label: String, val bold: Boolean = false, val onClick: () -> Unit)

/** Lomiri quicklist: a menu beside the icon with a small pointer towards it. */
@Composable
private fun QuickList(
    app: AppInfo,
    anchorY: () -> Float,
    panelWidth: Dp,
    entries: List<QuickEntry>,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val arrowW = 8.dp
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val margin = with(density) { 8.dp.roundToPx() }
                    val y = (anchorY() - p.height / 2f).roundToInt()
                        .coerceIn(margin, (constraints.maxHeight - p.height - margin).coerceAtLeast(margin))
                    p.place(with(density) { (panelWidth + 4.dp).roundToPx() }, y)
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(arrowW, 16.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height); close()
                }
                drawPath(path, Lomiri.LauncherBg)
            }
            Column(
                Modifier
                    .width(240.dp)
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .background(Lomiri.LauncherBg, RoundedCornerShape(4.dp)),
            ) {
                entries.forEachIndexed { i, e ->
                    Text(
                        e.label,
                        color = Lomiri.Text,
                        fontSize = 16.sp,
                        fontWeight = if (e.bold) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss(); e.onClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    if (i < entries.lastIndex) HorizontalDivider(color = Lomiri.Divider)
                }
            }
        }
    }
}

/** Home button glyph: a 2x2 grid of rounded tiles (apps), drawn in white. */
@Composable
private fun HomeGlyph(size: Dp) {
    Canvas(Modifier.size(size)) {
        val gap = this.size.minDimension * 0.12f
        val tile = (this.size.minDimension - gap) / 2f
        val corner = androidx.compose.ui.geometry.CornerRadius(tile * 0.3f)
        for (i in 0..1) for (j in 0..1) {
            drawRoundRect(
                Color.White,
                topLeft = Offset(i * (tile + gap), j * (tile + gap)),
                size = androidx.compose.ui.geometry.Size(tile, tile),
                cornerRadius = corner,
                style = if (i == 1 && j == 1) Stroke(width = tile * 0.16f) else androidx.compose.ui.graphics.drawscope.Fill,
            )
        }
    }
}
