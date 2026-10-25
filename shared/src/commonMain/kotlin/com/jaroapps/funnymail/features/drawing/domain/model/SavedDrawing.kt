package com.jaroapps.funnymail.features.drawing.domain.model

/**
 * A drawing saved in this device's local library.
 *
 * The JSON remains the canonical drawing format, so the library does not depend
 * on raster images or cloud storage.
 */
data class SavedDrawing(
    val id: Long,
    val drawingJson: String,
    val createdAtEpochMillis: Long,
)
