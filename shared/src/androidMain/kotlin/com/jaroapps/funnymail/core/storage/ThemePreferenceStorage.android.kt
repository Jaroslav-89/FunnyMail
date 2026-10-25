package com.jaroapps.funnymail.core.storage

import android.content.Context

actual object ThemePreferenceStorage {
    private var applicationContext: Context? = null

    actual fun attachPlatformContext(context: Any?) {
        applicationContext = (context as? Context)?.applicationContext
    }

    actual fun loadDarkTheme(): Boolean =
        applicationContext
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(DARK_THEME_KEY, false)
            ?: false

    actual fun saveDarkTheme(enabled: Boolean) {
        applicationContext
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(DARK_THEME_KEY, enabled)
            ?.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "funnymail_preferences"
        const val DARK_THEME_KEY = "dark_theme_enabled"
    }
}
