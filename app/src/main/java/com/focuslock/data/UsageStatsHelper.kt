package com.focuslock.data

import android.app.usage.UsageStatsManager
import android.content.Context
import com.focuslock.model.AppInfo
import com.focuslock.util.PackageManagerHelper

/**
 * 封装 [UsageStatsManager]，提供今日各应用前台使用时长。
 * 需要用户在系统设置中授予 "使用情况访问" 权限。
 */
class UsageStatsHelper(private val context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** 是否已获得使用情况访问权限 */
    fun hasPermission(): Boolean {
        if (usm == null) return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 24 * 60 * 60 * 1000, now)
        return stats != null && stats.isNotEmpty()
    }

    /**
     * 今日（自然日 0:00 至今）各应用前台时长映射：packageName -> 毫秒。
     */
    fun todayUsageMillis(): Map<String, Long> {
        val usm = this.usm ?: return emptyMap()
        val startOfDay = startOfTodayMillis()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now) ?: return emptyMap()

        val result = HashMap<String, Long>()
        for (s in stats) {
            if (s.totalTimeInForeground <= 0) continue
            val pkg = s.packageName
            // 取每条记录的最大累计值，避免分时段记录被重复计数
            val existing = result[pkg] ?: 0L
            if (s.totalTimeInForeground > existing) {
                result[pkg] = s.totalTimeInForeground
            }
        }
        return result
    }

    /**
     * 带应用元信息 + 今日使用时长的列表（按使用时长降序）。
     */
    fun todayAppList(): List<AppInfo> {
        val usage = todayUsageMillis()
        return PackageManagerHelper.installedApps(context).map {
            it.copy(usageTodayMs = usage[it.packageName] ?: 0L)
        }.filter { it.usageTodayMs > 0 }
            .sortedByDescending { it.usageTodayMs }
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
