package com.jaroapps.funnymail.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app

/**
 * The Google Services plugin initializes the default Firebase app from
 * androidApp/google-services.json when that file is present.
 */
internal actual fun initializeFirebasePlatform(): Boolean =
    runCatching { Firebase.app.name.isNotBlank() }.getOrDefault(false)
