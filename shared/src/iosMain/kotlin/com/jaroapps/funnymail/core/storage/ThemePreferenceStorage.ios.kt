package com.jaroapps.funnymail.core.storage

import platform.Foundation.NSUserDefaults

actual object ThemePreferenceStorage {
    actual fun attachPlatformContext(context: Any?) = Unit

    actual fun loadDarkTheme(): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey(DARK_THEME_KEY)

    actual fun saveDarkTheme(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = DARK_THEME_KEY)
    }

    actual fun updateSystemBars(darkThemeEnabled: Boolean, transparentStatusBar: Boolean) = Unit

    private const val DARK_THEME_KEY = "funnymail.darkThemeEnabled"
}
