package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focuslock.LockScreenActivity
import com.focuslock.data.AppRepository

/**
 * 无障碍服务：在锁机会话期间监测前台应用。
 * 当用户切换到黑名单内且未在白名单的应用时，拉起 [LockScreenActivity] 覆盖页，
 * 阻止用户继续使用娱乐类应用。
 *
 * 仅在 [AppRepository.isLocked] == true 时执行拦截逻辑。
 */
class AppWatchService : AccessibilityService() {

    private val repo by lazy { AppRepository.get(this) }

    /** 最近一次被拦截的包名，避免对同一应用反复拉起锁屏页 */
    private var lastBlockedPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return // 忽略自身界面切换

        if (!repo.isLocked) {
            lastBlockedPkg = null
            return
        }

        if (repo.shouldBlock(pkg)) {
            if (lastBlockedPkg != pkg) {
                lastBlockedPkg = pkg
                showLockScreen(pkg)
            }
        } else {
            // 用户切回允许的应用，重置拦截标记
            lastBlockedPkg = null
        }
    }

    override fun onInterrupt() {
        // 无操作
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastBlockedPkg = null
    }

    private fun showLockScreen(pkg: String) {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
        }
        // 启动锁屏覆盖页后执行全局 "返回" 操作，把被拦截应用压回后台
        startActivity(intent)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
