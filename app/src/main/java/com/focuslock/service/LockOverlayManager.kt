package com.focuslock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.focuslock.R
import com.focuslock.data.AppRepository

/**
 * 强锁悬浮窗管理器：用 WindowManager 全局悬浮窗实现锁屏覆盖。
 *
 * 为什么用悬浮窗而不是 Activity：
 * 1. 悬浮窗在所有应用之上，按 Home / 最近任务 / 返回键都弄不掉
 * 2. 从后台直接弹出，不受 "后台不能启动 Activity" 限制
 * 3. 真正的全屏覆盖，状态栏/导航栏都盖住
 *
 * 注意：需要 SYSTEM_ALERT_WINDOW 权限（用户需手动开启"悬浮窗/显示在其他应用上层"）
 */
class LockOverlayManager private constructor(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val repo = AppRepository.get(context)
    private var overlayView: View? = null
    private var isShowing = false

    /**
     * 显示锁屏悬浮窗。如果已经显示则不重复添加。
     */
    fun show(blockedPkg: String? = null) {
        if (isShowing) return
        val view = try {
            LayoutInflater.from(context).inflate(R.layout.overlay_lock_screen, null)
        } catch (e: Exception) {
            // 如果 overlay 布局加载失败，回退到 activity_lock_screen（同一份布局，只是名字不同）
            LayoutInflater.from(context).inflate(R.layout.activity_lock_screen, null)
        }

        // 初始化按钮
        val btnBack = view.findViewById<Button>(R.id.btnBackHome)
        btnBack?.setOnClickListener { goHome() }

        // 如果有被拦截的包名，显示提示
        val subtitle = view.findViewById<TextView>(R.id.lockSubtitle)
        if (blockedPkg != null && subtitle != null) {
            val appName = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(blockedPkg, 0)).toString()
            } catch (_: Exception) {
                blockedPkg
            }
            subtitle.text = context.getString(R.string.lock_screen_blocked_app, appName)
        }

        val params = buildLayoutParams()
        try {
            wm.addView(view, params)
            overlayView = view
            isShowing = true
        } catch (e: Exception) {
            // 权限不足或其他异常，降级为 Activity 方式
            fallbackToActivity(blockedPkg)
        }
    }

    /**
     * 隐藏锁屏悬浮窗。
     */
    fun hide() {
        if (!isShowing) return
        try {
            overlayView?.let { wm.removeView(it) }
        } catch (_: Exception) {
            // 忽略已被移除的异常
        }
        overlayView = null
        isShowing = false
    }

    /** 是否正在显示 */
    fun isVisible(): Boolean = isShowing

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        // 注意：不隐藏悬浮窗！回到桌面后悬浮窗仍然覆盖，
        // 因为桌面上用户也可能点其他 App，由拦截逻辑决定何时隐藏。
    }

    private fun fallbackToActivity(blockedPkg: String?) {
        try {
            val intent = Intent(context, com.focuslock.LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                blockedPkg?.let { putExtra(com.focuslock.LockScreenActivity.EXTRA_PACKAGE, it) }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 彻底失败，忽略
        }
    }

    companion object {
        @Volatile private var instance: LockOverlayManager? = null

        fun get(context: Context): LockOverlayManager =
            instance ?: synchronized(this) {
                instance ?: LockOverlayManager(context.applicationContext).also { instance = it }
            }
    }
}
