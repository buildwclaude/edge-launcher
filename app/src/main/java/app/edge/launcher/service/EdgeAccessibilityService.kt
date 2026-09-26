package app.edge.launcher.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import app.edge.launcher.edge
import app.edge.launcher.overlay.DockOverlay
import app.edge.launcher.overlay.DrawerOverlay
import app.edge.launcher.overlay.SwitcherOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Draws the edge strips over every app and turns swipes on them into the
 * dock (left), app switcher (right), shade (top) and drawer (bottom).
 */
class EdgeAccessibilityService : AccessibilityService(), EdgeGestureListener {
    val scope: CoroutineScope = MainScope()
    lateinit var wm: WindowManager
        private set
    lateinit var owner: OverlayLifecycleOwner
        private set
    lateinit var dock: DockOverlay
        private set
    lateinit var drawer: DrawerOverlay
        private set
    lateinit var switcher: SwitcherOverlay
        private set
    private lateinit var strips: EdgeStrips
    private var connected = false

    private val activityCache = HashMap<String, Boolean>()
    private var topFired = false
    private var topStartX = 0f

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    closeOverlays()
                    strips.locked = true
                }
                else -> updateLocked()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        owner = OverlayLifecycleOwner().also { it.start() }
        strips = EdgeStrips(this, wm, this)
        dock = DockOverlay(this)
        drawer = DrawerOverlay(this)
        switcher = SwitcherOverlay(this)
        connected = true
        instance = this

        scope.launch {
            edge.settings.settings.filterNotNull().collect { strips.apply(it) }
        }
        edge.recents.seedFromUsageStats()

        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        updateLocked()
    }

    private fun updateLocked() {
        if (!connected) return
        strips.locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                val cls = event.className?.toString()
                if (pkg != null && cls != null && isActivity(pkg, cls)) {
                    edge.recents.onForeground(pkg)
                }
                updateWindows()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> updateWindows()
        }
    }

    /** Filters out dialogs, keyboards and our own overlay windows. */
    private fun isActivity(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        return activityCache.getOrPut(key) {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun updateWindows() {
        val list = try { windows } catch (e: Exception) { return }
        strips.keyboardVisible = list.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        strips.systemUiOpen = list.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused }
        updateLocked()
    }

    fun screenWidth() = strips.screenSize().first
    fun screenHeight() = strips.screenSize().second

    // --- Gestures -----------------------------------------------------------

    private val triggerPx get() = edge.settings.current.triggerDistanceDp * resources.displayMetrics.density

    override fun onEdgeStart(edge: Edge, rawX: Float, rawY: Float) {
        when (edge) {
            Edge.LEFT -> { closeOverlays(except = dock); dock.begin() }
            Edge.RIGHT -> { closeOverlays(except = switcher); switcher.begin() }
            Edge.BOTTOM -> { closeOverlays(except = drawer); drawer.begin() }
            Edge.TOP -> { topFired = false; topStartX = rawX }
        }
    }

    override fun onEdgeDrag(edge: Edge, distance: Float, rawX: Float, rawY: Float) {
        when (edge) {
            Edge.LEFT -> dock.drag(distance)
            Edge.RIGHT -> switcher.drag(distance)
            Edge.BOTTOM -> drawer.drag(distance)
            Edge.TOP -> if (!topFired && distance > triggerPx) {
                topFired = true
                openShade()
            }
        }
    }

    override fun onEdgeRelease(edge: Edge, distance: Float, velocity: Float) {
        when (edge) {
            Edge.LEFT -> dock.release(distance, velocity)
            Edge.RIGHT -> switcher.release(distance, velocity)
            Edge.BOTTOM -> drawer.release(velocity)
            Edge.TOP -> if (!topFired && velocity > 800f) openShade()
        }
    }

    override fun onEdgeCancel(edge: Edge) {
        when (edge) {
            Edge.LEFT -> dock.cancel()
            Edge.RIGHT -> switcher.cancel()
            Edge.BOTTOM -> drawer.cancel()
            Edge.TOP -> Unit
        }
    }

    /** Lomiri indicators: left half pulls notifications, right half quick settings. */
    private fun openShade() {
        val action = if (topStartX < screenWidth() / 2f) GLOBAL_ACTION_NOTIFICATIONS else GLOBAL_ACTION_QUICK_SETTINGS
        performGlobalAction(action)
    }

    private fun closeOverlays(except: Any? = null) {
        if (except !== dock) dock.hideNow()
        if (except !== drawer) drawer.hideNow()
        if (except !== switcher) switcher.hideNow()
    }

    fun lockScreen(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    // --- Lifecycle ----------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!connected) return
        closeOverlays()
        strips.relayout()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!connected) return
        connected = false
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        closeOverlays()
        strips.removeAll()
        owner.destroy()
        scope.cancel()
    }

    companion object {
        @Volatile
        var instance: EdgeAccessibilityService? = null
            private set
    }
}
