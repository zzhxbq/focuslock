package com.focuslock.data

import android.content.Context
import android.content.SharedPreferences
import com.focuslock.util.SystemWhitelist

/**
 * 持久化存储：锁机状态、黑名单（锁定目标）、用户白名单。
 * 使用 SharedPreferences，AccessibilityService 与 UI 在同一进程内可共享同一实例，
 * 跨组件读取时每次重新读 int/string/set 保证实时性。
 */
class AppRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前是否处于锁机会话 */
    var isLocked: Boolean
        get() = prefs.getBoolean(KEY_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCKED, value).apply()

    /** 锁机会话开始时间戳（毫秒），用于显示已专注时长 */
    var lockStartedAt: Long
        get() = prefs.getLong(KEY_LOCK_START, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCK_START, value).apply()

    /** 被锁定的应用包名集合（黑名单 / 锁定目标） */
    fun getLockedApps(): Set<String> =
        prefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet()

    fun setLockedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_LOCKED_APPS, packages).apply()
    }

    fun addLockedApp(pkg: String) {
        val cur = getLockedApps().toMutableSet()
        if (cur.add(pkg)) prefs.edit().putStringSet(KEY_LOCKED_APPS, cur).apply()
    }

    fun removeLockedApp(pkg: String) {
        val cur = getLockedApps().toMutableSet()
        if (cur.remove(pkg)) prefs.edit().putStringSet(KEY_LOCKED_APPS, cur).apply()
    }

    fun isLockedApp(pkg: String): Boolean = getLockedApps().contains(pkg)

    /** 用户白名单（首次进入时用通讯类应用初始化） */
    fun getUserWhitelist(): Set<String> {
        val cur = prefs.getStringSet(KEY_WHITELIST, null)
        if (cur == null) {
            prefs.edit().putStringSet(KEY_WHITELIST, SystemWhitelist.PACKAGES).apply()
            return SystemWhitelist.PACKAGES
        }
        return cur
    }

    fun setUserWhitelist(packages: Set<String>) {
        // 系统级保留应用永远不放开，与其取并集
        val merged = packages + SystemWhitelist.PACKAGES
        prefs.edit().putStringSet(KEY_WHITELIST, merged).apply()
    }

    fun isWhitelisted(pkg: String): Boolean = getUserWhitelist().contains(pkg) || SystemWhitelist.isProtected(pkg)

    /**
     * 锁机期间判定：是否应当拦截当前前台应用。
     * 命中黑名单 且 未在白名单（含系统保留）。
     */
    fun shouldBlock(pkg: String): Boolean = isLocked && isLockedApp(pkg) && !isWhitelisted(pkg)

    companion object {
        private const val PREFS_NAME = "focus_lock_prefs"
        private const val KEY_LOCKED = "is_locked"
        private const val KEY_LOCK_START = "lock_started_at"
        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_WHITELIST = "user_whitelist"

        @Volatile private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context).also { instance = it }
            }
    }
}
