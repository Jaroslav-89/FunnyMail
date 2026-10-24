package com.jaroapps.funnymail.core.storage

/**
 * Stores one local drawing draft on the device. This is local-only storage; it does not
 * send drawings to Firebase or make them available on other devices.
 */
expect object DrawingDraftStorage {
    fun attachPlatformContext(context: Any?)
    fun load(): String?
    fun save(serializedDrawing: String)
    fun clear()
}
