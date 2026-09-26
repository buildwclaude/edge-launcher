package app.edge.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Progress (0 = hidden, 1 = shown) of something that follows a finger and then
 * springs open or closed: the dock, the drawer.
 */
@Stable
class RevealState(
    private val scope: CoroutineScope,
    private val onSettled: (open: Boolean) -> Unit = {},
) {
    val progress = Animatable(0f)
    /** Distance in px that maps to progress 1. */
    var extentPx = 1f
    private var raw = 0f

    val isShown: Boolean get() = progress.targetValue > 0f

    fun beginDrag() {
        raw = progress.value
    }

    /** [delta] in px, positive means "more open". */
    fun dragBy(delta: Float) {
        raw = (raw + delta / extentPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val v = raw
        scope.launch { progress.snapTo(v) }
    }

    /** [velocity] in px/s, positive means "opening". */
    fun settle(velocity: Float) {
        val open = when {
            velocity > FLING -> true
            velocity < -FLING -> false
            else -> raw > 0.3f
        }
        animateTo(open)
    }

    fun animateTo(open: Boolean) {
        raw = if (open) 1f else 0f
        scope.launch {
            progress.animateTo(raw, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            onSettled(open)
        }
    }

    fun snapClosed() {
        raw = 0f
        scope.launch { progress.snapTo(0f) }
    }

    companion object {
        const val FLING = 900f
    }
}
