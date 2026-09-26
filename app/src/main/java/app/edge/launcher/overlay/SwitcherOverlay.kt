package app.edge.launcher.overlay

import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService

/** Right edge. Stage 2: a short swipe switches to the previous app. */
class SwitcherOverlay(private val service: EdgeAccessibilityService) {
    fun begin() = Unit
    fun drag(distance: Float) = Unit
    fun release(distance: Float, velocity: Float) {
        val trigger = service.edge.settings.current.triggerDistanceDp * service.resources.displayMetrics.density
        if (distance > trigger || velocity > 800f) {
            service.edge.recents.previousApp()?.let { service.edge.apps.launch(it) }
        }
    }
    fun cancel() = Unit
    fun hideNow() = Unit
}
