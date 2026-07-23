package com.focuslock.util

/**
 * 系统级默认白名单：通讯 / 输入法 / 启动器 / 系统界面等。
 * 这些应用即使被误加入黑名单，锁机期间也不会被拦截，保证手机基本可用。
 */
object SystemWhitelist {

    val PACKAGES: Set<String> = setOf(
        // 启动器（桌面）
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.sec.android.app.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.android.systemui",

        // 电话 / 紧急
        "com.android.dialer",
        "com.android.phone",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",

        // 短信
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",

        // 联系人
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",

        // 设置
        "com.android.settings",
        "com.miui.securitycenter",

        // 输入法
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.baidu.input",
        "com.sohu.inputmethod.sogou",
        "com.iflytek.inputmethod",

        // 本应用自身
        "com.focuslock",

        // 时钟 / 闹钟（保证用户能听到闹钟）
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage"
    )

    fun isProtected(pkg: String): Boolean = PACKAGES.contains(pkg)
}
