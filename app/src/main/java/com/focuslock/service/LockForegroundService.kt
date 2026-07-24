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
import com.focuslock.MainActivity
import com.focuslock.R
import com.focuslock.data.AppRepository

/**
 * 前台保活服务：锁机会话期间保持常驻通知，
 * 降低系统杀进程概率，并给用户一个"正在专注"的明确入口。
 *
 * 额外职责：兜底轮询当前前台应用。
 * AccessibilityService 事件可能因系统策略、机型差异漏报，
 * 这里每 [POLL_INTERVAL_MS] 用 UsageStatsManager 查一次当前前台包名，
 * 若在锁机状态且当前前台不在白名单，立即通过 [LockOverlayManager] 显示悬浮窗。
 *
 * 双重保险：无障碍事件（实时）+ 轮询（兜底），保证锁得住。
 */
class LockForegroundService : Service() {

    private val repo by lazy { AppRepository.get(this) }
    private val overlay by lazy { LockOverlayManager.get(this) }
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
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        // 服务销毁时同时隐藏悬浮窗
        try {
            overlay.hide()
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * 兜底：查询当前前台应用，若锁机且不在白名单则显示悬浮窗。
     * 若在白名单则隐藏悬浮窗。
     */
    private fun checkForeground() {
        if (!repo.isLocked) {
            if (overlay.isVisible()) overlay.hide()
            return
        }
        val pkg = currentForegroundPackage() ?: return
        if (pkg == packageName) {
            // 自身界面，隐藏悬浮窗
            if (overlay.isVisible()) overlay.hide()
            return
        }
        if (repo.shouldBlock(pkg)) {
            overlay.show(pkg)
        } else {
            if (overlay.isVisible()) overlay.hide()
        }
    }

    /**
     * 通过 UsageStatsManager 查询最近 2 秒内使用时间最新的应用包名，
     * 作为当前前台应用的近似值。
     *
     * 时间窗口从 5 秒缩到 2 秒，更准确；同时如果查不到结果会扩大到 10 秒再试一次。
     */
    private fun currentForegroundPackage(): String? {
        val usm = usageStats ?: return null
        val now = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 2_000,
                now
            )
        } catch (e: SecurityException) {
            return null
        } ?: return null

        val result = stats.maxByOrNull { it.lastTimeUsed }
        if (result != null) return result.packageName

        // 第一次没查到，扩大时间窗口到 10 秒再试一次
        return try {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 10_000,
                now
            )?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
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
        private const val POLL_INTERVAL_MS = 1000L // 每 1 秒轮询一次

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
