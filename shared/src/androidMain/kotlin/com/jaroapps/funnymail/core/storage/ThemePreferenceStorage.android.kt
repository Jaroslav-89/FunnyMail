package com.jaroapps.funnymail.core.storage

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View

actual object ThemePreferenceStorage {
    private var applicationContext: Context? = null
    private var hostActivity: Activity? = null

    actual fun attachPlatformContext(context: Any?) {
        applicationContext = (context as? Context)?.applicationContext
        hostActivity = context as? Activity
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

    actual fun updateSystemBars(darkThemeEnabled: Boolean) {
        val window = hostActivity?.window ?: return
        window.statusBarColor = if (darkThemeEnabled) {
            AndroidColor.rgb(16, 17, 20)
        } else {
            AndroidColor.rgb(245, 168, 0)
        }
        val view = window.decorView
        val currentFlags = view.systemUiVisibility
        view.systemUiVisibility = if (darkThemeEnabled) {
            currentFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            currentFlags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private const val PREFERENCES_NAME = "funnymail_preferences"
    private const val DARK_THEME_KEY = "dark_theme_enabled"
}
