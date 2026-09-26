package app.edge.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.edge.launcher.service.EdgeAccessibilityService

/** Checks for, and settings screens that grant, everything Edge needs. */
object Permissions {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, EdgeAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun usageAccess(ctx: Context) = ctx.edge.recents.hasUsageAccess()

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun batteryUnrestricted(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun defaultHome(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName
    }

    private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    fun usageAccessSettings(ctx: Context) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        if (Build.VERSION.SDK_INT >= 29) data = pkgUri(ctx)
    }

    fun overlaySettings(ctx: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(ctx))

    @android.annotation.SuppressLint("BatteryLife")
    fun batterySettings(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx))

    /** Role request dialog on Android 10+, otherwise the default-apps screen. */
    fun homeRequest(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    /** Starts [intent], falling back to [fallback] if the first screen doesn't exist. */
    fun open(ctx: Context, intent: Intent, fallback: Intent? = null) {
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            fallback?.let { runCatching { ctx.startActivity(it) } }
        }
    }
}
