package app.edge.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/** Daily screen time for the Lomiri-style infographic on the home screen. */
data class MonthData(
    val daysInMonth: Int,
    /** 1-based day of month. */
    val today: Int,
    /** Minutes per day this month; future days are 0. */
    val thisMonth: List<Long>,
    val lastMonth: List<Long>,
    val todayMinutes: Long,
)

object ScreenTime {
    private val cache = HashMap<Long, Long>()

    fun load(ctx: Context, recents: RecentsRepository): MonthData? {
        if (!recents.hasUsageAccess()) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_MONTH)
        val days = now.getActualMaximum(Calendar.DAY_OF_MONTH)

        fun dayStart(monthOffset: Int, day: Int) = Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun minutes(start: Long, isToday: Boolean): Long {
            if (!isToday) cache[start]?.let { return it }
            val end = if (isToday) System.currentTimeMillis() else start + 24 * 3600_000L
            val total = try {
                usm.queryAndAggregateUsageStats(start, end).values
                    .filter { !recents.isHomePackage(it.packageName) && it.packageName != "com.android.systemui" }
                    .sumOf { it.totalTimeInForeground }
            } catch (e: Exception) { 0L }
            val m = (total / 60_000L).coerceAtMost(24 * 60L)
            if (!isToday) cache[start] = m
            return m
        }

        val thisMonth = (1..days).map { d -> if (d > today) 0L else minutes(dayStart(0, d), d == today) }
        val prevDays = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastMonth = (1..prevDays).map { d -> minutes(dayStart(-1, d), false) }
        return MonthData(days, today, thisMonth, lastMonth, thisMonth[today - 1])
    }
}
