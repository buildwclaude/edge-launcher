package app.edge.launcher.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import app.edge.launcher.data.EdgeSettings
import java.util.EnumMap

enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

interface EdgeGestureListener {
    fun onEdgeStart(edge: Edge, rawX: Float, rawY: Float)
    /** [distance] is how far the finger has moved away from the edge, in px. */
    fun onEdgeDrag(edge: Edge, distance: Float, rawX: Float, rawY: Float)
    /** [velocity] is px/s away from the edge. */
    fun onEdgeRelease(edge: Edge, distance: Float, velocity: Float)
    fun onEdgeCancel(edge: Edge)
}

/** An invisible strip along one screen edge that reports inward drags. */
@SuppressLint("ViewConstructor")
class EdgeStripView(context: Context, private val edge: Edge, private val listener: EdgeGestureListener) : View(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    private fun inward(x: Float, y: Float) = when (edge) {
        Edge.LEFT -> x - downX
        Edge.RIGHT -> downX - x
        Edge.TOP -> y - downY
        Edge.BOTTOM -> downY - y
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val d = inward(e.rawX, e.rawY)
                if (!dragging && d > slop) {
                    dragging = true
                    listener.onEdgeStart(edge, downX, downY)
                }
                if (dragging) listener.onEdgeDrag(edge, d, e.rawX, e.rawY)
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(e)
                if (dragging) {
                    val t = tracker!!
                    t.computeCurrentVelocity(1000)
                    val v = when (edge) {
                        Edge.LEFT -> t.xVelocity
                        Edge.RIGHT -> -t.xVelocity
                        Edge.TOP -> t.yVelocity
                        Edge.BOTTOM -> -t.yVelocity
                    }
                    listener.onEdgeRelease(edge, inward(e.rawX, e.rawY), v)
                }
                dragging = false
                tracker?.recycle(); tracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) listener.onEdgeCancel(edge)
                dragging = false
                tracker?.recycle(); tracker = null
            }
        }
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Ask the system not to start its Back gesture on our side strips
        // (Android honours up to 200dp of exclusion per edge).
        if (Build.VERSION.SDK_INT >= 29 && (edge == Edge.LEFT || edge == Edge.RIGHT)) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }
}

/**
 * Owns the four edge strips, lays them out from settings, and hides them
 * while the keyboard is up, in full-screen apps, or on the lock screen.
 */
class EdgeStrips(
    private val context: Context,
    private val wm: WindowManager,
    private val listener: EdgeGestureListener,
) {
    private val strips = EnumMap<Edge, EdgeStripView>(Edge::class.java)
    private var settings = EdgeSettings()
    private val density = context.resources.displayMetrics.density

    var keyboardVisible = false
        set(v) { if (field != v) { field = v; relayout() } }
    var fullscreen = false
        set(v) { if (field != v) { field = v; relayout() } }
    var locked = false
        set(v) { if (field != v) { field = v; relayout() } }

    /** 1px window whose only job is to observe system-bar visibility. */
    private val probe = View(context)
    private var probeAttached = false

    fun apply(s: EdgeSettings) {
        settings = s
        attachProbe()
        relayout()
    }

    private fun attachProbe() {
        if (probeAttached || Build.VERSION.SDK_INT < 30) return
        probe.setOnApplyWindowInsetsListener { v, insets ->
            fullscreen = !insets.isVisible(WindowInsets.Type.statusBars())
            v.onApplyWindowInsets(insets)
        }
        try {
            wm.addView(probe, overlayParams(1, 1, focusable = false, touchable = false))
            probeAttached = true
        } catch (_: Exception) {}
    }

    fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    fun relayout() {
        val (w, h) = screenSize()
        val s = settings
        val hidden = locked ||
            (s.hideWithKeyboard && keyboardVisible) ||
            (s.hideInFullscreen && fullscreen)
        fun px(dp: Int) = (dp * density).toInt().coerceAtLeast(1)

        for (edge in Edge.entries) {
            val enabled = !hidden && when (edge) {
                Edge.LEFT -> s.leftEnabled
                Edge.RIGHT -> s.rightEnabled
                Edge.TOP -> s.topEnabled
                Edge.BOTTOM -> s.bottomEnabled
            }
            if (!enabled) {
                strips.remove(edge)?.let { runCatching { wm.removeViewImmediate(it) } }
                continue
            }
            val p = when (edge) {
                Edge.LEFT, Edge.RIGHT -> {
                    val start = if (edge == Edge.LEFT) s.leftStart else s.rightStart
                    val end = if (edge == Edge.LEFT) s.leftEnd else s.rightEnd
                    val top = (minOf(start, end) * h).toInt()
                    val height = ((maxOf(start, end) - minOf(start, end)) * h).toInt().coerceAtLeast(px(48))
                    overlayParams(px(s.sideWidthDp), height, focusable = false).apply {
                        gravity = Gravity.TOP or if (edge == Edge.LEFT) Gravity.LEFT else Gravity.RIGHT
                        y = top
                    }
                }
                Edge.TOP -> overlayParams(w, px(s.topHeightDp), focusable = false).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                }
                Edge.BOTTOM -> overlayParams(w, px(s.bottomHeightDp), focusable = false).apply {
                    gravity = Gravity.BOTTOM or Gravity.LEFT
                    y = px(s.bottomOffsetDp).takeIf { s.bottomOffsetDp > 0 } ?: 0
                }
            }
            val existing = strips[edge]
            val view = existing ?: EdgeStripView(context, edge, listener)
            view.setBackgroundColor(if (s.showStrips) 0x66E95420 else 0)
            try {
                if (existing == null) {
                    wm.addView(view, p)
                    strips[edge] = view
                } else {
                    wm.updateViewLayout(view, p)
                }
            } catch (e: Exception) {
                android.util.Log.w("EdgeStrips", "strip $edge failed", e)
            }
        }
    }

    fun removeAll() {
        strips.values.forEach { runCatching { wm.removeViewImmediate(it) } }
        strips.clear()
        if (probeAttached) runCatching { wm.removeViewImmediate(probe) }
        probeAttached = false
    }
}
