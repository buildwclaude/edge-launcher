package app.edge.launcher.overlay

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.Canvas
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

    fun close() = reveal.animateTo(false)

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
    val iconSize = s.dockIconDp.dp
    val panelWidth = iconSize / 0.75f
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }

    val pinnedApps = remember(s.pinned, apps) { s.pinned.mapNotNull { edge.apps.byKey(it) } }
    val runningApps = remember(mru, apps, pinnedApps) {
        val pinnedPkgs = pinnedApps.map { it.packageName }.toSet()
        mru.filter { it !in pinnedPkgs }.mapNotNull { edge.apps.forPackage(it) }
    }
    val items = pinnedApps + runningApps
    val reveal = dock.reveal

    Box(
        Modifier
            .fillMaxSize()
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
    ) {
        // Light dim of the app behind; darker as a long swipe nears "home".
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (reveal.value * 0.25f + dock.homeHint * 0.5f).coerceIn(0f, 1f) }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { dock.close() } },
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
                .width(panelWidth + 10.dp)
                .graphicsLayer { translationX = (reveal.value - 1f) * size.width },
        ) {
            // Soft shadow along the panel's right edge.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .offset(x = panelWidth)
                    .background(Brush.horizontalGradient(listOf(Color(0x55000000), Color.Transparent))),
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Lomiri.LauncherBg)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Home button: full-width orange block, as in Lomiri.
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
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(items, key = { it.key }) { app ->
                        LauncherItem(
                            app = app,
                            iconSize = iconSize,
                            panelWidth = panelWidth,
                            running = app.packageName in mru,
                            focused = app.packageName == foreground,
                            onClick = { dock.launch(app) },
                            onLongClick = { menuFor = app },
                        )
                    }
                }
            }
        }

        val target = menuFor
        val pinnedIndex = target?.let { s.pinned.indexOf(it.key) } ?: -1
        ActionSheet(
            visible = target != null,
            title = target?.label.orEmpty(),
            icon = target?.icon,
            actions = buildList {
                if (target == null) return@buildList
                if (pinnedIndex >= 0) {
                    if (pinnedIndex > 0) add(SheetAction("Move up") { edge.settings.move(target.key, -1) })
                    if (pinnedIndex < s.pinned.lastIndex) add(SheetAction("Move down") { edge.settings.move(target.key, 1) })
                    add(SheetAction("Unpin from launcher") { edge.settings.unpin(target.key) })
                } else {
                    add(SheetAction("Pin to launcher") { edge.settings.pin(target.key) })
                }
                if (target.packageName in mru) add(SheetAction("Close") { edge.recents.dismiss(target.packageName) })
                add(SheetAction("App info") { dock.hideNow(); edge.apps.openAppInfo(target) })
            },
            onDismiss = { menuFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherItem(
    app: AppInfo,
    iconSize: Dp,
    panelWidth: Dp,
    running: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Lomiri item height: itemWidth * 15/16 + 1gu.
    Box(
        Modifier
            .width(panelWidth)
            .height(iconSize * 15f / 16f + 8.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            app.icon,
            iconSize,
            contentDescription = app.label,
            modifier = Modifier.graphicsLayer {
                val sc = if (pressed) 0.92f else 1f
                scaleX = sc; scaleY = sc
            },
        )
        // Running pip on the left, focused pip on the right: 0.25gu x 0.5gu.
        if (running) Pip(Modifier.align(Alignment.CenterStart).offset(x = 3.dp))
        if (focused) Pip(Modifier.align(Alignment.CenterEnd).offset(x = (-3).dp))
    }
}

@Composable
private fun Pip(modifier: Modifier) {
    Box(modifier.size(width = 2.dp, height = 4.dp).background(Color.White))
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
