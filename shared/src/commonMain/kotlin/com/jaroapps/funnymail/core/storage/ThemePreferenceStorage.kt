package com.jaroapps.funnymail.core.storage

/** Persists the user's appearance choice on the current device. */
expect object ThemePreferenceStorage {
    fun attachPlatformContext(context: Any?)
    fun loadDarkTheme(): Boolean
    fun saveDarkTheme(enabled: Boolean)
    fun updateSystemBars(darkThemeEnabled: Boolean, transparentStatusBar: Boolean)
}
