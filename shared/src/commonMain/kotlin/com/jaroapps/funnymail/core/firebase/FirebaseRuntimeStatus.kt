package com.jaroapps.funnymail.core.firebase

/**
 * Keeps Firebase initialization lazy-safe for projects that have not added platform
 * configuration yet. The app can still start, while authentication reports setup guidance.
 */
object FirebaseRuntimeStatus {
    var isConfigured: Boolean = false
        private set

    var initializationMessage: String? = null
        private set

    fun initialize(): Boolean {
        val result = runCatching { initializeFirebasePlatform() }
        isConfigured = result.getOrDefault(false)
        initializationMessage = when {
            isConfigured -> null
            result.exceptionOrNull()?.message?.isNotBlank() == true ->
                result.exceptionOrNull()?.message
            else -> "Firebase не прошёл проверку конфигурации. Проверьте настройки платформы."
        }
        return isConfigured
    }
}

internal expect fun initializeFirebasePlatform(): Boolean
