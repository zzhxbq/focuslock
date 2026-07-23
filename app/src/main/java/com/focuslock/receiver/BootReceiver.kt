package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.data.AppRepository
import com.focuslock.service.LockForegroundService

/**
 * 开机自启：若锁机会话在重启前处于激活状态，则恢复前台保活服务。
 * 无障碍服务会被系统自动恢复，无需手动启动。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AppRepository.get(context).isLocked) {
            LockForegroundService.start(context)
        }
    }
}
