package com.jaroapps.funnymail.features.drawing.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-independent representation of a drawing.
 *
 * Point coordinates are normalized to [0, 1]. Each point is encoded as a JSON
 * pair, e.g. [0.1, 0.2], to keep the wire format compact and Firestore-friendly.
 */
@Serializable
data class DrawingDocument(
    val version: Int = CURRENT_VERSION,
    val canvas: DrawingCanvas = DrawingCanvas(),
    val strokes: List<DrawingStroke> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

@Serializable
data class DrawingCanvas(
    val width: Int = 1000,
    val height: Int = 1000,
    @SerialName("backgroundColor")
    val backgroundColor: String = "#FFFFFF",
)

@Serializable
data class DrawingStroke(
    val color: String = "#000000",
    val width: Float = 5f,
    /** Each item must contain exactly two normalized coordinates: [x, y]. */
    val points: List<List<Double>>,
)
