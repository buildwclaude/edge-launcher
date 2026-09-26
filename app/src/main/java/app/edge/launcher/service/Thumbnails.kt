package app.edge.launcher.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App previews for the switcher, taken with the accessibility screenshot API
 * (Android 11+). Kept in memory only; secure windows come out black.
 */
class Thumbnails(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 14
    }
    /** Bumped whenever a thumbnail changes, so Compose can re-read. */
    val version = mutableIntStateOf(0)
    private var lastShotAt = 0L

    operator fun get(pkg: String): ImageBitmap? = cache[pkg]

    fun remove(pkg: String) {
        if (cache.remove(pkg) != null) version.intValue++
    }

    /** Screenshots the display for [pkg]; [onFull] gets the full-size image straight away. */
    fun capture(pkg: String, onFull: ((ImageBitmap) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < 30) return
        val now = SystemClock.uptimeMillis()
        if (now - lastShotAt < 340) return
        lastShotAt = now
        take(pkg, onFull)
    }

    @RequiresApi(30)
    private fun take(pkg: String, onFull: ((ImageBitmap) -> Unit)?) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        buffer.close()
                        if (hw == null) return
                        onFull?.invoke(hw.asImageBitmap())
                        scope.launch(Dispatchers.Default) {
                            val soft = hw.copy(Bitmap.Config.ARGB_8888, false) ?: return@launch
                            val scaled = Bitmap.createScaledBitmap(
                                soft, (soft.width * 0.4f).toInt(), (soft.height * 0.4f).toInt(), true,
                            )
                            if (scaled !== soft) soft.recycle()
                            val out = scaled.copy(Bitmap.Config.HARDWARE, false) ?: scaled
                            withContext(Dispatchers.Main) {
                                cache[pkg] = out.asImageBitmap()
                                version.intValue++
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        } catch (e: Exception) {
            android.util.Log.w("EdgeThumbs", "screenshot failed", e)
        }
    }
}
