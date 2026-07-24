package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focuslock.LockScreenActivity
import com.focuslock.data.AppRepository

/**
 * 无障碍服务：强锁模式核心拦截器。
 *
 * 锁机会话期间，前台应用只要不在白名单（含系统保留应用），
 * 立即拉起 [LockScreenActivity] 覆盖页阻挡使用。
 *
 * 关键点：
 * - 不再调用 performGlobalAction(GLOBAL_ACTION_BACK)，避免把锁屏自己也弹走
 * - 锁屏页自带 onPause 重新拉起机制，能挡住 Home/最近任务键
 * - 监听 typeWindowStateChanged + typeWindowsChanged，保证事件不漏
 * - 不拦截自身包名，避免锁死自己的设置 UI
 */
class AppWatchService : AccessibilityService() {

    private val repo by lazy { AppRepository.get(this) }

    /** 最近一次被拦截的包名，避免对同一应用反复拉起锁屏页 */
    private var lastBlockedPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // 忽略空包名与系统 UI 的事件噪声
        if (pkg.isEmpty() || pkg == "android") return
        // 自身界面不拦截（用户在设置锁机/白名单）
        if (pkg == packageName) {
            lastBlockedPkg = null
            return
        }

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

    /**
     * 拉起锁屏覆盖页。
     *
     * 故意不调用 performGlobalAction(GLOBAL_ACTION_BACK)：
     * 因为锁屏 Activity 是 NEW_TASK + 独立 taskAffinity，
     * BACK 全局动作会作用在当前栈顶（即刚拉起的锁屏），把它自己也关掉。
     * 改由锁屏页自身用 onPause 机制阻挡用户绕过。
     */
    private fun showLockScreen(pkg: String) {
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
