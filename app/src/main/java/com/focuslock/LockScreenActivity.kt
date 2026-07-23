package com.focuslock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.focuslock.databinding.ActivityLockScreenBinding

/**
 * 锁机覆盖页：覆盖在被拦截的娱乐应用之上，
 * 提示用户"专注中"并提供返回桌面的入口。
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 显示在锁屏之上、点亮屏幕
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackHome.setOnClickListener { goHome() }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("锁屏期间禁止返回")
    override fun onBackPressed() {
        // 拦截返回键，强制用户走"返回桌面"按钮
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}
