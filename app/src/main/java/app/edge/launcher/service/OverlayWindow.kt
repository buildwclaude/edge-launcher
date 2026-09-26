package app.edge.launcher.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Lifecycle for Compose content hosted by the accessibility service. */
class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Builds a TYPE_ACCESSIBILITY_OVERLAY layout that ignores system bar insets. */
fun overlayParams(width: Int, height: Int, focusable: Boolean, touchable: Boolean = true): WindowManager.LayoutParams {
    var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    return WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

/**
 * A full-screen Compose window drawn over every app. Added when shown and
 * removed when hidden, so it never blocks touches while idle.
 */
class OverlayWindow(
    context: Context,
    private val wm: WindowManager,
    owner: OverlayLifecycleOwner,
    content: @Composable () -> Unit,
) {
    var onBack: (() -> Unit)? = null

    private val root = object : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack?.invoke()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private val params = overlayParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        focusable = false,
    )

    var isAttached = false
        private set

    init {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent(content)
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(composeView, FrameLayout.LayoutParams(-1, -1))
    }

    fun show(focusable: Boolean = false) {
        if (!isAttached) {
            setFlags(focusable)
            try {
                wm.addView(root, params)
                isAttached = true
            } catch (e: Exception) {
                android.util.Log.w("EdgeOverlay", "addView failed", e)
            }
        } else {
            setFocusable(focusable)
        }
    }

    fun setFocusable(focusable: Boolean) {
        val before = params.flags
        setFlags(focusable)
        if (isAttached && before != params.flags) wm.updateViewLayout(root, params)
    }

    private fun setFlags(focusable: Boolean) {
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    fun hide() {
        if (isAttached) {
            try { wm.removeViewImmediate(root) } catch (_: Exception) {}
            isAttached = false
        }
    }
}
