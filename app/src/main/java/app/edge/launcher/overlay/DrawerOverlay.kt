package app.edge.launcher.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService
import app.edge.launcher.service.OverlayWindow
import app.edge.launcher.ui.AppDrawer
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.RevealState

/** The app drawer over any app: from the bottom edge, or out of the launcher on the left. */
class DrawerOverlay(private val service: EdgeAccessibilityService) {
    // Focusable once open so the search field can take the keyboard.
    val reveal = RevealState(service.scope) { open ->
        if (open) {
            window.setFocusable(true)
            service.dock.hideNow()
        } else {
            window.hide()
        }
    }
    var fromLeft by mutableStateOf(false)
        private set
    private var last = 0f

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { DrawerContent(this) }
    }.apply { onBack = { close() } }

    val isShown get() = window.isShown

    private fun extent() = if (fromLeft) service.screenWidth().toFloat() else service.screenHeight().toFloat()

    fun begin(fromLeft: Boolean) {
        this.fromLeft = fromLeft
        reveal.snapClosed()
        reveal.extentPx = extent()
        window.show(focusable = false)
        reveal.beginDrag()
        last = 0f
    }

    fun drag(distance: Float) {
        reveal.dragBy(distance - last)
        last = distance
    }

    fun release(velocity: Float) { reveal.settle(velocity) }
    fun cancel() { reveal.animateTo(false) }
    fun close() { reveal.animateTo(false) }

    fun open(fromLeft: Boolean) {
        this.fromLeft = fromLeft
        reveal.extentPx = extent()
        window.show(focusable = false)
        reveal.animateTo(true)
    }

    fun hideNow() {
        reveal.snapClosed()
        window.hide()
    }

    fun detach() = window.detach()

    @Composable
    private fun DrawerContent(overlay: DrawerOverlay) {
        val edge = LocalContext.current.edge
        val apps by edge.apps.apps.collectAsState()
        val settings by edge.settings.settings.collectAsState()
        val reveal = overlay.reveal
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            AppDrawer(
                apps = apps.orEmpty(),
                pinned = settings?.pinned.orEmpty(),
                visible = reveal.isShown,
                reveal = reveal,
                fromLeft = overlay.fromLeft,
                onLaunch = { app ->
                    overlay.hideNow()
                    edge.apps.launch(app)
                },
                modifier = Modifier
                    .graphicsLayer {
                        if (overlay.fromLeft) translationX = (reveal.value - 1f) * w
                        else translationY = (1f - reveal.value) * h
                    }
                    .background(Lomiri.DrawerBg),
            )
        }
    }
}
