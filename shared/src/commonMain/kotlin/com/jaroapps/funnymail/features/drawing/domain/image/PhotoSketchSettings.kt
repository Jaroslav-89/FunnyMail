package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * User-adjustable controls for photo-to-vector conversion. These values affect only generated
 * photo sketches; they are not stored in the drawing document after conversion.
 */
data class PhotoSketchSettings(
    /** Lower values detect more subtle edges; higher values keep only stronger contours. */
    val edgeSensitivity: Float = 0.12f,
    /** Minimum normalized edge confidence, 0..254. Zero keeps every detected edge. */
    val minimumEdgeStrength: Int = 0,
    /** Soft point budget used to trade detail for a smaller editable document. */
    val maxPoints: Int = 120_000,
    /** Width applied to every photo-derived vector contour. */
    val strokeWidth: Float = 2.6f,
    /** If false, each retained edge cell is represented as a dot instead of a connected path. */
    val mergePointsIntoLines: Boolean = true,
    /** Ramer-Douglas-Peucker tolerance measured in preview-map cells; zero disables simplification. */
    val simplificationTolerance: Double = 1.0,
    /** Weakest isolated edge cells removed, as a percentage from 0 to 80. */
    val isolatedPointRemovalPercent: Int = 20,
) {
    init {
        require(edgeSensitivity in 0.01f..0.95f)
        require(minimumEdgeStrength in 0..254)
        require(maxPoints in 1..500_000)
        require(strokeWidth.isFinite() && strokeWidth in 0.1f..100f)
        require(simplificationTolerance.isFinite() && simplificationTolerance in 0.0..64.0)
        require(isolatedPointRemovalPercent in 0..80)
    }
}
