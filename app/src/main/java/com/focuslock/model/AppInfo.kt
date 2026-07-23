package com.focuslock.model

import android.graphics.drawable.Drawable

/**
 * 一个已安装应用的展示信息。
 * @param usageTodayMs 今日已使用时长（毫秒），仅"使用统计"页用到
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean = false,
    val usageTodayMs: Long = 0L
)
