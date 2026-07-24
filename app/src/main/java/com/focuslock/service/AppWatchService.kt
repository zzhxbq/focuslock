package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.focuslock.data.AppRepository

/**
 * 无障碍服务：强锁模式核心拦截器。
 *
 * 锁机会话期间，当前台应用不在白名单（含系统保留应用）时，
 * 立即通过 [LockOverlayManager] 显示全屏悬浮窗覆盖，阻挡用户操作。
 *
 * 为什么用悬浮窗而不是 Activity：
 * 1. 悬浮窗优先级更高，Home/最近任务/返回键都弄不掉
 * 2. 从后台可直接弹出，不受"后台不能启动 Activity"限制
 * 3. 真正全屏覆盖状态栏和导航栏
 *
 * 注意：不做 lastBlockedPkg 去重。之前的去重逻辑有 bug：
 * 如果第一次显示失败（比如权限没给全），后续同一包名就不会再尝试，
 * 导致锁不住。改为每次命中都调 show，由 overlay 自己判断是否已显示。
 */
class AppWatchService : AccessibilityService() {

    private val repo by lazy { AppRepository.get(this) }
    private val overlay by lazy { LockOverlayManager.get(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg.isEmpty() || pkg == "android") return
        // 自身界面不拦截（用户在设置锁机/白名单）
        if (pkg == packageName) {
            if (repo.isLocked && overlay.isVisible()) {
                overlay.hide()
            }
            return
        }

        if (!repo.isLocked) {
            if (overlay.isVisible()) overlay.hide()
            return
        }

        if (repo.shouldBlock(pkg)) {
            overlay.show(pkg)
        } else {
            // 用户切到允许的应用（白名单内），隐藏悬浮窗
            if (overlay.isVisible()) overlay.hide()
        }
    }

    override fun onInterrupt() {
        // 无操作
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
