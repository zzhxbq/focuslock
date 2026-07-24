package com.focuslock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.focuslock.data.AppRepository
import com.focuslock.databinding.ActivityLockScreenBinding

/**
 * 强锁覆盖页：覆盖在任何非白名单应用之上，用户无法绕过。
 *
 * 强化点：
 * 1. 沉浸式全屏，隐藏状态栏/导航栏
 * 2. onPause 时若仍在锁机状态，立即重新拉起自己，防止被 Home/最近任务绕过
 * 3. 拦截返回键、最近任务键（长按）
 * 4. excludeFromRecents，不在最近任务列表里出现
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val repo by lazy { AppRepository.get(this) }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏之上显示 + 点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        applyImmersiveFullscreen()

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackHome.setOnClickListener { goHome() }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台重新应用沉浸式（部分机型会重置）
        applyImmersiveFullscreen()
    }

    override fun onPause() {
        super.onPause()
        // 兜底：若仍处于锁机会话，且不是用户主动结束，立刻重新拉起锁屏
        // 防止用户用 Home 键、最近任务键、分屏等方式绕过
        if (repo.isLocked) {
            handler.postDelayed({ relaunchIfStillLocked() }, 200)
        }
    }

    private fun relaunchIfStillLocked() {
        if (isFinishing || isDestroyed) return
        if (!repo.isLocked) return
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        startActivity(intent)
    }

    private fun applyImmersiveFullscreen() {
        val decor = window.decorView
        decor.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    /** 拦截返回键 */
    @Deprecated("锁屏期间禁止返回", ReplaceWith("/* 不响应 */"))
    override fun onBackPressed() {
        // 不调用 super，吞掉返回键
    }

    /** 拦截物理按键：返回 / 最近任务 / 音量等不响应，仅允许 Home 键回桌面 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,           // 最近任务键
            KeyEvent.KEYCODE_HOME -> true          // 吞掉（Home 一般由系统处理，这里再保险一次）
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        // 长按最近任务 / Home 也吞掉
        return when (keyCode) {
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK -> true
            else -> super.onKeyLongPress(keyCode, event)
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}
