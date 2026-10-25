package com.jaroapps.funnymail

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android launchers decide their own notification-dot rendering and color.
 * This alternate launcher icon gives FunnyMail a red marker while messages are unread.
 */
object LauncherBadge {
    fun setUnread(context: Context, unread: Boolean) {
        val packageName = context.packageName
        val packageManager = context.packageManager
        val regularLauncher = ComponentName(packageName, "$packageName.LauncherActivity")
        val badgeLauncher = ComponentName(packageName, "$packageName.LauncherActivityWithBadge")
        val flags = PackageManager.DONT_KILL_APP

        if (unread) {
            packageManager.setComponentEnabledSetting(
                regularLauncher,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flags,
            )
            packageManager.setComponentEnabledSetting(
                badgeLauncher,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                flags,
            )
        } else {
            packageManager.setComponentEnabledSetting(
                regularLauncher,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                flags,
            )
            packageManager.setComponentEnabledSetting(
                badgeLauncher,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flags,
            )
        }
    }
}
