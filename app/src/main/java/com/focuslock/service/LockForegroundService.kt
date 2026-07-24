package com.focuslock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.focuslock.LockScreenActivity
import com.focuslock.MainActivity
import com.focuslock.R
import com.focuslock.data.AppRepository

/**
 * 前台保活服务：锁机会话期间保持常驻通知，
 * 降低系统杀进程概率，并给用户一个"正在专注"的明确入口。
 *
 * 额外职责：兜底轮询当前前台应用。
 * AccessibilityService 事件可能因系统策略或延迟漏报，
 * 这里每 [POLL_INTERVAL_MS] 用 UsageStatsManager 查一次当前前台包名，
 * 若在锁机状态且当前前台不在白名单，立即拉起锁屏。
 */
class LockForegroundService : Service() {

    private val repo by lazy { AppRepository.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var usageStats: UsageStatsManager? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())
        // 启动兜底轮询
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * 兜底：查询当前前台应用，若锁机且不在白名单则拉起锁屏。
     */
    private fun checkForeground() {
        if (!repo.isLocked) return
        val pkg = currentForegroundPackage() ?: return
        if (pkg == packageName) return
        if (repo.shouldBlock(pkg)) {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
                putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
            }
            startActivity(intent)
        }
    }

    /**
     * 通过 UsageStatsManager 查询最近 5 秒内使用时间最新的应用包名，
     * 作为当前前台应用的近似值。
     */
    private fun currentForegroundPackage(): String? {
        val usm = usageStats ?: return null
        val now = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 5_000,
                now
            )
        } catch (e: SecurityException) {
            return null
        } ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 1500L

        fun start(context: Context) {
            val intent = Intent(context, LockForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockForegroundService::class.java))
        }
    }
}
