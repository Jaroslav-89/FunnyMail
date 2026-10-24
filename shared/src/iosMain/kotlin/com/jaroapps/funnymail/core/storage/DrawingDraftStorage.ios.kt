package com.jaroapps.funnymail.core.storage

import platform.Foundation.NSUserDefaults

actual object DrawingDraftStorage {
    actual fun attachPlatformContext(context: Any?) = Unit

    actual fun load(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(DRAFT_KEY)

    actual fun save(serializedDrawing: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            serializedDrawing,
            forKey = DRAFT_KEY,
        )
    }

    actual fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(DRAFT_KEY)
    }

    private const val DRAFT_KEY = "funnymail.latestDrawingJson"
}
