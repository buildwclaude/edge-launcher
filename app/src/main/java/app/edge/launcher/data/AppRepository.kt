package app.edge.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

@Immutable
data class AppInfo(
    /** Stable id: "package/class#userSerial". */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Average icon colour, used to tint switcher cards. */
    val color: Color,
)

class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconSizePx = (72 * context.resources.displayMetrics.density).toInt()
    private val iconCache = HashMap<String, Pair<ImageBitmap, Color>>()

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    /** All launchable activities, sorted by label. Null until the first load. */
    val apps: StateFlow<List<AppInfo>?> = _apps

    private var loadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) =
            pkgs.forEach { changed(it) }
        override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) =
            pkgs.forEach { changed(it) }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
        scope.launch { initDefaultPins() }
    }

    private fun changed(pkg: String) {
        synchronized(iconCache) { iconCache.keys.removeAll { it.startsWith("$pkg/") } }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _apps.value = withContext(Dispatchers.Default) { load() }
        }
    }

    private fun load(): List<AppInfo> {
        val collator = Collator.getInstance()
        val result = ArrayList<AppInfo>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = try {
                launcherApps.getActivityList(null, user)
            } catch (e: Exception) {
                Log.w(TAG, "getActivityList failed for $user", e)
                emptyList()
            }
            for (info in list) {
                val cn = info.componentName
                val key = "${cn.packageName}/${cn.className}#$serial"
                val (icon, color) = synchronized(iconCache) { iconCache[key] }
                    ?: renderIcon(info, user).also { synchronized(iconCache) { iconCache[key] = it } }
                result += AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    packageName = cn.packageName,
                    component = cn,
                    user = user,
                    icon = icon,
                    color = color,
                )
            }
        }
        result.sortWith { a, b -> collator.compare(a.label, b.label) }
        return result
    }

    private fun renderIcon(info: LauncherActivityInfo, user: UserHandle): Pair<ImageBitmap, Color> {
        val drawable: Drawable = try {
            if (user == Process.myUserHandle()) info.getIcon(0) else info.getBadgedIcon(0)
        } catch (e: Exception) {
            context.packageManager.defaultActivityIcon
        }
        val bitmap = drawableToSquareBitmap(drawable, iconSizePx)
        val avg = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        return bitmap.asImageBitmap() to Color(avg).copy(alpha = 1f)
    }

    // --- Lookups ------------------------------------------------------------

    fun byKey(key: String): AppInfo? = _apps.value?.firstOrNull { it.key == key }

    /** The main launchable activity for a package (primary profile first). */
    fun forPackage(pkg: String): AppInfo? {
        val list = _apps.value ?: return null
        return list.firstOrNull { it.packageName == pkg && it.user == Process.myUserHandle() }
            ?: list.firstOrNull { it.packageName == pkg }
    }

    fun isLaunchable(pkg: String): Boolean = forPackage(pkg) != null

    // --- Actions ------------------------------------------------------------

    fun launch(app: AppInfo, sourceBounds: Rect? = null) {
        try {
            launcherApps.startMainActivity(app.component, app.user, sourceBounds, null)
        } catch (e: Exception) {
            Log.w(TAG, "launch failed", e)
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchPackage(pkg: String) {
        forPackage(pkg)?.let { launch(it); return }
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try { context.startActivity(it) } catch (e: Exception) { Log.w(TAG, "launch failed", e) }
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "app info failed", e)
        }
    }

    // --- First-run dock -----------------------------------------------------

    private suspend fun initDefaultPins() {
        val (s, apps) = combine(settings.settings.filterNotNull(), this.apps.filterNotNull()) { s, a -> s to a }.first()
        if (s.pinnedInitialized) return
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        val keys = intents.mapNotNull { intent ->
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) { null }
            pkg?.takeIf { it != "android" }?.let { p -> apps.firstOrNull { it.packageName == p }?.key }
        }.distinct()
        settings.setPinned(keys)
    }

    companion object {
        private const val TAG = "EdgeApps"

        /**
         * Draws an icon into a full square. Adaptive icons are drawn without the
         * system mask so the UI can clip them to Lomiri's rounded squares.
         */
        fun drawableToSquareBitmap(d: Drawable, size: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (d is AdaptiveIconDrawable) {
                // Layers are 108dp with the middle 72dp visible.
                val pad = size / 4
                val bounds = Rect(-pad, -pad, size + pad, size + pad)
                d.background?.let { it.bounds = bounds; it.draw(canvas) }
                d.foreground?.let { it.bounds = bounds; it.draw(canvas) }
            } else {
                // Legacy icons: centre on a neutral tile so the square clip looks intentional.
                canvas.drawColor(android.graphics.Color.rgb(0xEE, 0xEE, 0xEE))
                val inset = size / 8
                d.setBounds(inset, inset, size - inset, size - inset)
                d.draw(canvas)
            }
            return bmp
        }
    }
}
