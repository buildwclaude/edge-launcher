package app.edge.launcher.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Most-recently-used list of apps. The accessibility service feeds it live
 * window changes; UsageStatsManager seeds it when the service starts.
 */
class RecentsRepository(private val context: Context, private val apps: AppRepository) {
    private val selfPkg = context.packageName
    private val _mru = MutableStateFlow<List<String>>(emptyList())
    /** Package names, most recent first. Only packages that have a launcher entry are shown in the UI. */
    val mru: StateFlow<List<String>> = _mru

    private val _foreground = MutableStateFlow<String?>(null)
    /** Package in front right now, or [HOME] when a launcher is showing. */
    val foreground: StateFlow<String?> = _foreground

    val recentApps: Flow<List<AppInfo>> = combine(_mru, apps.apps) { mru, list ->
        if (list == null) emptyList() else mru.mapNotNull { apps.forPackage(it) }
    }

    private val homePackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() + selfPkg
    }

    fun isHomePackage(pkg: String) = pkg in homePackages

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Rebuilds the list from the last 12 hours of usage events. */
    fun seedFromUsageStats() {
        if (!hasUsageAccess()) return
        try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - 12 * 60 * 60 * 1000L, end)
            val last = HashMap<String, Long>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                @Suppress("DEPRECATION")
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last[e.packageName] = e.timeStamp
            }
            val seeded = last.entries.sortedByDescending { it.value }.map { it.key }
                .filter { !isHomePackage(it) && it != "com.android.systemui" }
            // Keep live entries first; fill in anything we haven't seen yet.
            val live = _mru.value
            _mru.value = (live + seeded.filter { it !in live }).take(MAX)
            if (_foreground.value == null) {
                val latest = last.maxByOrNull { it.value }?.key
                _foreground.value = latest?.let { if (isHomePackage(it)) HOME else it }
            }
        } catch (e: Exception) {
            Log.w("EdgeRecents", "usage stats failed", e)
        }
    }

    /** Called when an activity window comes to the front. */
    fun onForeground(pkg: String) {
        if (isHomePackage(pkg)) {
            _foreground.value = HOME
            return
        }
        if (!apps.isLaunchable(pkg)) return // IME, system dialogs, etc.
        _foreground.value = pkg
        _mru.value = (listOf(pkg) + _mru.value.filter { it != pkg }).take(MAX)
    }

    /** The app a short right-edge swipe should switch to. */
    fun previousApp(): AppInfo? {
        val fg = _foreground.value
        return _mru.value.asSequence()
            .filter { it != fg }
            .mapNotNull { apps.forPackage(it) }
            .firstOrNull()
    }

    fun isRunning(pkg: String) = pkg in _mru.value

    /** Removes an app from the spread and asks the system to drop it if it's in the background. */
    fun dismiss(pkg: String) {
        _mru.value = _mru.value.filter { it != pkg }
        if (_foreground.value == pkg) _foreground.value = null
        try {
            context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
        } catch (e: Exception) {
            Log.w("EdgeRecents", "kill failed", e)
        }
    }

    companion object {
        const val HOME = "<home>"
        private const val MAX = 24
    }
}
