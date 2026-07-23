package com.focuslock.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.focuslock.model.AppInfo

/**
 * 读取已安装应用列表（仅含可在桌面启动的应用）。
 */
object PackageManagerHelper {

    fun installedApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        return resolveInfos.map { ri ->
            val info = ri.activityInfo
            val pkg = info.packageName
            val label: String = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                ri.loadLabel(pm).toString()
            }
            val icon: Drawable? = try {
                pm.getApplicationIcon(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            val isSystem = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(
                packageName = pkg,
                label = label,
                icon = icon,
                isSystem = isSystem
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, pkg: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }
}
