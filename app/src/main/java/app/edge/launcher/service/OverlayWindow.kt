package app.edge.launcher.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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

/** Id of the display mode with the highest refresh rate at the current resolution. */
fun highestRefreshModeId(context: Context): Int {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
    val current = display.mode
    return display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate }?.modeId ?: 0
}

/** A TYPE_ACCESSIBILITY_OVERLAY layout, GPU rendered, ignoring system bar insets. */
fun overlayParams(width: Int, height: Int, focusable: Boolean, touchable: Boolean = true): WindowManager.LayoutParams {
    // Windows added through WindowManager don't get hardware acceleration unless they ask.
    var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
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
 * A full-screen Compose window drawn over every app. It is attached once and
 * kept composed; while hidden it is invisible and lets touches through, so
 * showing it costs a single frame instead of a fresh window and composition.
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
        touchable = false,
    ).apply {
        preferredDisplayModeId = highestRefreshModeId(context)
        title = "Edge overlay"
    }

    private var attached = false
    var isShown = false
        private set

    init {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent(content)
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(composeView, FrameLayout.LayoutParams(-1, -1))
        root.visibility = View.INVISIBLE
        if (Build.VERSION.SDK_INT >= 35) {
            root.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        try {
            wm.addView(root, params)
            attached = true
        } catch (e: Exception) {
            android.util.Log.w("EdgeOverlay", "addView failed", e)
        }
    }

    fun show(focusable: Boolean = false) {
        if (!attached) return
        isShown = true
        root.visibility = View.VISIBLE
        applyFlags(touchable = true, focusable = focusable)
    }

    fun setFocusable(focusable: Boolean) {
        if (isShown) applyFlags(touchable = true, focusable = focusable)
    }

    fun hide() {
        if (!isShown) return
        isShown = false
        root.visibility = View.INVISIBLE
        applyFlags(touchable = false, focusable = false)
    }

    private fun applyFlags(touchable: Boolean, focusable: Boolean) {
        var f = params.flags
        f = if (touchable) f and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        f = if (focusable) f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (f != params.flags) {
            params.flags = f
            try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
        }
    }

    fun detach() {
        if (attached) runCatching { wm.removeViewImmediate(root) }
        attached = false
        isShown = false
    }
}
