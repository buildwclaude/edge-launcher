package app.edge.launcher.overlay

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import app.edge.launcher.ui.IconShape
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.RevealState
import app.edge.launcher.ui.SheetAction

/** Unity/Lomiri-style launcher that slides in from the left edge. */
class DockOverlay(private val service: EdgeAccessibilityService) {
    val reveal = RevealState(service.scope) { open ->
        if (open) window.setFocusable(true) else window.hide()
    }
    /** 0..1: how close a long left swipe is to "go home". */
    var homeHint by mutableFloatStateOf(0f)
        private set
    private var last = 0f

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { DockContent(this) }
    }.apply { onBack = { close() } }

    private val settings: EdgeSettings get() = service.edge.settings.current
    private val density get() = service.resources.displayMetrics.density
    private fun dockWidthPx() = (settings.dockIconDp + DOCK_EXTRA_DP) * density
    private fun homeDistance() = service.screenWidth() * settings.longSwipeFraction.coerceAtLeast(0.25f) +
        dockWidthPx()

    fun begin() {
        reveal.extentPx = dockWidthPx()
        window.show(focusable = false)
        reveal.beginDrag()
        last = 0f
        homeHint = 0f
    }

    fun drag(distance: Float) {
        reveal.dragBy(distance - last)
        last = distance
        val w = dockWidthPx()
        homeHint = ((distance - w * 1.5f) / (homeDistance() - w * 1.5f)).coerceIn(0f, 1f)
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
        reveal.snapClosed()
        window.hide()
    }

    fun goHome() {
        hideNow()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun openDrawer() {
        hideNow()
        service.drawer.open()
    }

    fun launch(app: AppInfo) {
        service.edge.apps.launch(app)
        close()
    }

    companion object {
        const val DOCK_EXTRA_DP = 28
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockContent(dock: DockOverlay) {
    val edge = androidx.compose.ui.platform.LocalContext.current.edge
    val settings by edge.settings.settings.collectAsState()
    val apps by edge.apps.apps.collectAsState()
    val mru by edge.recents.mru.collectAsState()
    val foreground by edge.recents.foreground.collectAsState()
    val s = settings ?: EdgeSettings()
    val iconSize = s.dockIconDp.dp
    val dockWidth = iconSize + DockOverlay.DOCK_EXTRA_DP.dp
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }

    val pinnedApps = remember(s.pinned, apps) { s.pinned.mapNotNull { edge.apps.byKey(it) } }
    val runningApps = remember(mru, apps, pinnedApps) {
        val pinnedPkgs = pinnedApps.map { it.packageName }.toSet()
        mru.filter { it !in pinnedPkgs }.mapNotNull { edge.apps.forPackage(it) }
    }
    val p = dock.reveal.progress

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); dock.reveal.beginDrag() },
                    onHorizontalDrag = { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dock.reveal.dragBy(dx)
                    },
                    onDragEnd = { dock.reveal.settle(tracker.calculateVelocity().x) },
                    onDragCancel = { dock.reveal.settle(0f) },
                )
            },
    ) {
        // Scrim; darkens further as a long swipe approaches "go home".
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (p.value * 0.45f + dock.homeHint * 0.4f).coerceIn(0f, 1f) }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { dock.close() } },
        )
        if (dock.homeHint > 0f) {
            Text(
                "Release for Home",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = dock.homeHint },
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .width(dockWidth)
                .graphicsLayer { translationX = (p.value - 1f) * size.width }
                .background(Lomiri.Panel)
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // "BFB": tap for the app drawer, long-press for home.
            Box(
                Modifier
                    .size(iconSize)
                    .clip(IconShape)
                    .background(Lomiri.Orange)
                    .combinedClickable(onClick = { dock.openDrawer() }, onLongClick = { dock.goHome() }),
                contentAlignment = Alignment.Center,
            ) {
                DotsGlyph(iconSize * 0.42f)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(pinnedApps, key = { "p" + it.key }) { app ->
                    DockItem(app, iconSize, running = app.packageName in mru, focused = app.packageName == foreground,
                        onClick = { dock.launch(app) }, onLongClick = { menuFor = app })
                }
                if (runningApps.isNotEmpty()) {
                    item(key = "divider") {
                        HorizontalDivider(Modifier.width(iconSize * 0.6f).padding(vertical = 2.dp), color = Lomiri.PanelLight)
                    }
                    items(runningApps, key = { "r" + it.key }) { app ->
                        DockItem(app, iconSize, running = true, focused = app.packageName == foreground,
                            onClick = { dock.launch(app) }, onLongClick = { menuFor = app })
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
                    add(SheetAction("Unpin from dock") { edge.settings.unpin(target.key) })
                } else {
                    add(SheetAction("Pin to dock") { edge.settings.pin(target.key) })
                }
                if (target.packageName in mru) add(SheetAction("Remove from running") { edge.recents.dismiss(target.packageName) })
                add(SheetAction("App info") { dock.hideNow(); edge.apps.openAppInfo(target) })
            },
            onDismiss = { menuFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    app: AppInfo,
    iconSize: Dp,
    running: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Running pip, Unity style, on the left of the icon.
        Box(
            Modifier
                .width(4.dp)
                .height(if (focused) 16.dp else 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when {
                        focused -> Lomiri.Orange
                        running -> Color.White
                        else -> Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        AppIcon(
            app.icon,
            iconSize,
            contentDescription = app.label,
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
        Spacer(Modifier.width(10.dp))
    }
}

/** 3x3 grid of dots: the app-drawer button glyph. */
@Composable
private fun DotsGlyph(size: Dp) {
    Canvas(Modifier.size(size)) {
        val step = this.size.width / 3f
        val r = step * 0.22f
        for (i in 0..2) for (j in 0..2) {
            drawCircle(Color.White, r, Offset(step * (i + 0.5f), step * (j + 0.5f)))
        }
    }
}
