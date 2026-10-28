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
    /** Optional short caption shown under this drawing in chat. */
    val title: String = "",
    val caption: String = "",
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
    /** SOLID or GRADIENT; defaults keep older saved JSON compatible. */
    val backgroundMode: String = "SOLID",
    /** LEFT_TO_RIGHT, TOP_TO_BOTTOM, TOP_LEFT_TO_BOTTOM_RIGHT, or TOP_RIGHT_TO_BOTTOM_LEFT. */
    val gradientDirection: String = "TOP_TO_BOTTOM",
    val gradientStartColor: String = "#00AA00",
    val gradientEndColor: String = "#F5A800",
)

@Serializable
data class DrawingStroke(
    val color: String = "#000000",
    val width: Float = 5f,
    /** Each item must contain exactly two normalized coordinates: [x, y]. */
    val points: List<List<Double>>,
)
