package com.jaroapps.funnymail.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.initialize
import platform.Foundation.NSBundle

/**
 * Firebase's iOS SDK requires GoogleService-Info.plist to be bundled with the app.
 * Check for the file first so an unconfigured checkout still opens instead of
 * attempting to initialize Firebase without its required options.
 */
internal actual fun initializeFirebasePlatform(): Boolean {
    val plistPath = NSBundle.mainBundle.pathForResource("GoogleService-Info", "plist")
        ?: return false

    return runCatching {
        if (Firebase.apps().isEmpty()) {
            Firebase.initialize()
        }
        Firebase.app.name.isNotBlank() && plistPath.isNotBlank()
    }.getOrDefault(false)
}
