package com.focuslock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focuslock.data.AppRepository
import com.focuslock.data.UsageStatsHelper
import com.focuslock.databinding.ActivityMainBinding
import com.focuslock.databinding.ItemPermissionBinding
import com.focuslock.model.AppInfo
import com.focuslock.service.LockForegroundService
import com.focuslock.ui.AppAdapter
import com.focuslock.util.PackageManagerHelper
import com.focuslock.util.PermissionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repo by lazy { AppRepository.get(this) }
    private val usageHelper by lazy { UsageStatsHelper(this) }

    private var currentTab = AppAdapter.Mode.WHITELIST
    private var adapter: AppAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(
            mode = currentTab,
            isChecked = ::isAppChecked,
            onToggle = ::onAppToggled
        )
        binding.rvApps.adapter = adapter

        binding.btnToggleLock.setOnClickListener { toggleLock() }
        binding.tabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentTab = when (tab.position) {
                    0 -> AppAdapter.Mode.WHITELIST
                    1 -> AppAdapter.Mode.LOCKED
                    else -> AppAdapter.Mode.USAGE
                }
                adapter = AppAdapter(currentTab, ::isAppChecked, ::onAppToggled)
                binding.rvApps.adapter = adapter
                loadApps()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        loadApps()

        // 默认选中白名单 Tab（强锁模式下白名单是核心配置）
        binding.tabs.getTabAt(0)?.select()
    }

    override fun onResume() {
        super.onResume()
        refreshLockUi()
        refreshPermissionCards()
        if (currentTab == AppAdapter.Mode.USAGE) loadApps()
    }

    // ---------------- 锁机开关 ----------------

    private fun toggleLock() {
        if (repo.isLocked) {
            repo.isLocked = false
            LockForegroundService.stop(this)
            refreshLockUi()
            android.widget.Toast.makeText(this, R.string.toast_lock_stopped, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (!PermissionUtil.isAccessibilityEnabled(this)) {
            android.widget.Toast.makeText(this, R.string.toast_accessibility_required, android.widget.Toast.LENGTH_LONG).show()
            startActivity(PermissionUtil.openAccessibilitySettings())
            return
        }

        // 悬浮窗权限是强锁的核心，必须开启
        if (!PermissionUtil.canDrawOverlays(this)) {
            android.widget.Toast.makeText(this, R.string.toast_overlay_required, android.widget.Toast.LENGTH_LONG).show()
            startActivity(PermissionUtil.openOverlaySettings(this))
            return
        }

        // 兜底轮询依赖 UsageStats，必须开启
        if (!PermissionUtil.isUsageStatsGranted(this)) {
            android.widget.Toast.makeText(this, R.string.toast_usage_required, android.widget.Toast.LENGTH_LONG).show()
            startActivity(PermissionUtil.openUsageAccessSettings())
            return
        }

        repo.isLocked = true
        repo.lockStartedAt = System.currentTimeMillis()
        LockForegroundService.start(this)
        refreshLockUi()
        android.widget.Toast.makeText(this, R.string.toast_lock_started, android.widget.Toast.LENGTH_SHORT).show()
        // 起锁机后立即回到桌面，由兜底轮询接管
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
    }

    private fun refreshLockUi() {
        if (repo.isLocked) {
            binding.tvStatus.setText(R.string.status_locked)
            binding.ivLockIcon.imageTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            binding.btnToggleLock.setText(R.string.action_unlock)
            binding.btnToggleLock.backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.danger))

            val started = repo.lockStartedAt
            if (started > 0) {
                val elapsed = (System.currentTimeMillis() - started) / 60000
                binding.tvSessionInfo.text = getString(R.string.lock_session_elapsed, elapsed)
            }
        } else {
            binding.tvStatus.setText(R.string.status_unlocked)
            binding.ivLockIcon.imageTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary))
            binding.btnToggleLock.setText(R.string.action_lock)
            binding.btnToggleLock.backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            binding.tvSessionInfo.setText(R.string.usage_today)
        }
    }

    // ---------------- 应用列表 ----------------

    private fun isAppChecked(info: AppInfo): Boolean = when (currentTab) {
        AppAdapter.Mode.LOCKED -> repo.isLockedApp(info.packageName)
        AppAdapter.Mode.WHITELIST -> repo.isWhitelisted(info.packageName)
        AppAdapter.Mode.USAGE -> false
    }

    private fun onAppToggled(info: AppInfo, checked: Boolean) {
        when (currentTab) {
            AppAdapter.Mode.LOCKED -> {
                if (checked) repo.addLockedApp(info.packageName)
                else repo.removeLockedApp(info.packageName)
                android.widget.Toast.makeText(
                    this,
                    if (checked) R.string.toast_added else R.string.toast_removed,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            AppAdapter.Mode.WHITELIST -> {
                val cur = repo.getUserWhitelist().toMutableSet()
                if (checked) cur.add(info.packageName) else cur.remove(info.packageName)
                repo.setUserWhitelist(cur)
            }
            else -> {}
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val list: List<AppInfo> = withContext(Dispatchers.IO) {
                when (currentTab) {
                    AppAdapter.Mode.USAGE -> {
                        if (PermissionUtil.isUsageStatsGranted(this@MainActivity)) {
                            usageHelper.todayAppList()
                        } else emptyList()
                    }
                    else -> PackageManagerHelper.installedApps(this@MainActivity)
                }
            }
            adapter?.submitList(list)
            binding.tvEmpty.visibility =
                if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmpty.setText(
                when {
                    currentTab == AppAdapter.Mode.USAGE && !PermissionUtil.isUsageStatsGranted(this@MainActivity) ->
                        R.string.usage_no_data
                    list.isEmpty() -> R.string.hint_empty_apps
                    else -> R.string.hint_empty_apps
                }
            )
        }
    }

    // ---------------- 权限引导卡 ----------------

    private fun refreshPermissionCards() {
        val container: LinearLayout = binding.permContainer
        container.removeAllViews()

        // 1. 悬浮窗权限（强锁核心）
        addPermissionCard(
            container,
            getString(R.string.perm_overlay_title),
            getString(R.string.perm_overlay_desc),
            PermissionUtil.canDrawOverlays(this)
        ) { startActivity(PermissionUtil.openOverlaySettings(this)) }

        // 2. 无障碍服务
        addPermissionCard(
            container,
            getString(R.string.perm_accessibility_title),
            getString(R.string.perm_accessibility_desc),
            PermissionUtil.isAccessibilityEnabled(this)
        ) { startActivity(PermissionUtil.openAccessibilitySettings()) }

        // 3. 使用情况访问
        addPermissionCard(
            container,
            getString(R.string.perm_usage_title),
            getString(R.string.perm_usage_desc),
            PermissionUtil.isUsageStatsGranted(this)
        ) { startActivity(PermissionUtil.openUsageAccessSettings()) }

        // 4. 通知权限
        addPermissionCard(
            container,
            getString(R.string.perm_notification_title),
            getString(R.string.perm_notification_desc),
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun addPermissionCard(
        container: LinearLayout,
        title: String,
        desc: String,
        granted: Boolean,
        onClick: () -> Unit
    ) {
        val item = ItemPermissionBinding.inflate(layoutInflater, container, false)
        item.tvPermTitle.text = title
        item.tvPermDesc.text = desc
        if (granted) {
            item.btnPermAction.text = getString(R.string.action_granted)
            item.btnPermAction.isEnabled = false
        } else {
            item.btnPermAction.text = getString(R.string.action_open_settings)
            item.btnPermAction.isEnabled = true
            item.btnPermAction.setOnClickListener { onClick() }
        }
        container.addView(item.root)
    }
}
