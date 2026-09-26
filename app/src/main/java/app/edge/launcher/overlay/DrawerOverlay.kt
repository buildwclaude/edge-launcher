package app.edge.launcher.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/** The app drawer, pulled up from the bottom edge over any app. */
class DrawerOverlay(private val service: EdgeAccessibilityService) {
    // Focusable once open so the search field can take the keyboard.
    val reveal = RevealState(service.scope) { open ->
        if (open) window.setFocusable(true) else window.hide()
    }
    private var last = 0f

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { DrawerContent(this) }
    }.apply { onBack = { close() } }

    fun begin() {
        reveal.extentPx = service.screenHeight().toFloat()
        window.show(focusable = false)
        reveal.beginDrag()
        last = 0f
    }

    fun drag(distance: Float) {
        reveal.dragBy(distance - last)
        last = distance
    }

    fun release(velocity: Float) = reveal.settle(velocity)
    fun cancel() = reveal.animateTo(false)
    fun close() = reveal.animateTo(false)

    fun open() {
        reveal.extentPx = service.screenHeight().toFloat()
        window.show(focusable = false)
        reveal.animateTo(true)
    }

    fun hideNow() {
        reveal.snapClosed()
        window.hide()
    }

    @Composable
    private fun DrawerContent(overlay: DrawerOverlay) {
        val edge = LocalContext.current.edge
        val apps by edge.apps.apps.collectAsState()
        val settings by edge.settings.settings.collectAsState()
        val p = overlay.reveal.progress
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = constraints.maxHeight.toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = p.value * 0.5f }.background(Color.Black))
            AppDrawer(
                apps = apps.orEmpty(),
                pinned = settings?.pinned.orEmpty(),
                visible = overlay.reveal.isShown,
                reveal = overlay.reveal,
                onLaunch = { app ->
                    overlay.hideNow()
                    edge.apps.launch(app)
                },
                modifier = Modifier
                    .graphicsLayer { translationY = (1f - p.value) * h }
                    .background(Lomiri.Panel),
            )
        }
    }
}
