package app.edge.launcher.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val contentIntent: PendingIntent?,
)

/** Feeds the messages page of the indicator panel. */
class EdgeNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        _items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val all = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val groupsWithChildren = all.filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.groupKey }.toSet()
        _items.value = all
            .filter { sbn ->
                val summary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                !(summary && sbn.groupKey in groupsWithChildren)
            }
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                NotificationItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    clearable = sbn.isClearable,
                    autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                    contentIntent = sbn.notification.contentIntent,
                )
            }
            .sortedByDescending { it.postTime }
    }

    companion object {
        @Volatile
        private var instance: EdgeNotificationListener? = null
        private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
        val items: StateFlow<List<NotificationItem>> = _items

        fun isEnabled(context: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun clearAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        /** Opens a notification; our accessibility service lends its right to start activities. */
        fun open(context: Context, item: NotificationItem) {
            val pi = item.contentIntent ?: return
            try {
                val opts = if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else {
                    null
                }
                pi.send(context, 0, null, null, null, null, opts)
                if (item.autoCancel) dismiss(item.key)
            } catch (e: Exception) {
                android.util.Log.w("EdgeNotifications", "open failed", e)
            }
        }
    }
}
