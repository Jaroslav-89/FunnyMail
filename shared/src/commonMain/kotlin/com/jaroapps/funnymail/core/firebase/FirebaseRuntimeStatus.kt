package com.jaroapps.funnymail.core.firebase

/**
 * Keeps Firebase initialization lazy-safe for projects that have not added platform
 * configuration yet. The app can still start, while authentication reports setup guidance.
 */
object FirebaseRuntimeStatus {
    var isConfigured: Boolean = false
        private set

    fun initialize(): Boolean {
        isConfigured = runCatching { initializeFirebasePlatform() }.getOrDefault(false)
        return isConfigured
    }
}

internal expect fun initializeFirebasePlatform(): Boolean
