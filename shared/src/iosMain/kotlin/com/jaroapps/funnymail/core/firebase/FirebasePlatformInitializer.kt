package com.jaroapps.funnymail.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.initialize
import platform.Foundation.NSBundle

/**
 * Firebase's iOS SDK reads the default app configuration from GoogleService-Info.plist.
 * Throw a specific, user-visible diagnostic when the file is not bundled or initialization fails.
 */
internal actual fun initializeFirebasePlatform(): Boolean {
    val plistPath = NSBundle.mainBundle.pathForResource("GoogleService-Info", "plist")
    check(!plistPath.isNullOrBlank()) {
        "В собранном iOS-приложении отсутствует GoogleService-Info.plist. " +
            "Скачайте файл для bundle ID com.jaroapps.funnymail.FunnyMail из Firebase Console " +
            "и добавьте его в iosApp/iosApp/GoogleService-Info.plist с Target Membership = iosApp."
    }

    if (Firebase.apps().isEmpty()) {
        try {
            Firebase.initialize()
        } catch (error: Throwable) {
            throw IllegalStateException(
                "Firebase не смог прочитать GoogleService-Info.plist или создать приложение: " +
                    (error.message ?: error::class.simpleName.orEmpty()),
                error,
            )
        }
    }

    check(Firebase.app.name.isNotBlank()) {
        "Firebase SDK не создал приложение по умолчанию. Проверьте GoogleService-Info.plist."
    }
    return true
}
