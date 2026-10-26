package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * On-device semantic analysis for a single photo. The mask stores person confidence as unsigned
 * 8-bit values (0 = background, 255 = person). Face contours use normalized top-left coordinates.
 * All values are temporary and are not persisted.
 */
data class PortraitFeatures(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val maskWidth: Int,
    val maskHeight: Int,
    val personMask: ByteArray,
    val faceContours: List<PortraitFeatureContour>,
) {
    init {
        require(sourceWidth >= 3 && sourceHeight >= 3)
        require(maskWidth >= 3 && maskHeight >= 3)
        require(personMask.size == maskWidth * maskHeight)
    }
}

data class NormalizedPhotoPoint(
    val x: Double,
    val y: Double,
)

data class PortraitFeatureContour(
    val points: List<NormalizedPhotoPoint>,
    val strokeWidth: Float,
    val closed: Boolean = false,
)
