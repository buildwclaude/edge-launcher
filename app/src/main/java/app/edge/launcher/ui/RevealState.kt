package app.edge.launcher.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Progress (0 = hidden, 1 = shown) of something that follows a finger and then
 * springs open or closed. Drags write the state directly so the next frame
 * shows the finger's position with no extra latency.
 */
@Stable
class RevealState(
    private val scope: CoroutineScope,
    private val onSettled: (open: Boolean) -> Unit = {},
) {
    private val state = mutableFloatStateOf(0f)
    val value: Float get() = state.floatValue

    /** Where the panel is heading; > 0 means open or opening. */
    var target by mutableFloatStateOf(0f)
        private set
    val isShown: Boolean get() = target > 0f

    /** Distance in px that maps to progress 1. */
    var extentPx = 1f
    private var job: Job? = null

    fun beginDrag() {
        job?.cancel()
    }

    /** [delta] in px, positive means "more open". */
    fun dragBy(delta: Float) {
        job?.cancel()
        val v = (value + delta / extentPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        state.floatValue = v
        target = v
    }

    /** [velocity] in px/s, positive means "opening". */
    fun settle(velocity: Float) {
        val open = when {
            velocity > FLING -> true
            velocity < -FLING -> false
            else -> value > 0.3f
        }
        animateTo(open, velocity)
    }

    fun animateTo(open: Boolean, velocity: Float = 0f) {
        job?.cancel()
        val to = if (open) 1f else 0f
        target = to
        job = scope.launch {
            animate(
                initialValue = value,
                targetValue = to,
                initialVelocity = velocity / extentPx.coerceAtLeast(1f),
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 700f),
            ) { v, _ -> state.floatValue = v }
            onSettled(open)
        }
    }

    fun snapClosed() {
        job?.cancel()
        state.floatValue = 0f
        target = 0f
    }

    companion object {
        const val FLING = 900f
    }
}
