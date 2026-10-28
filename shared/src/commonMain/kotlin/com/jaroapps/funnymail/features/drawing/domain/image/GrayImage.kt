package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * Temporary grayscale pixels decoded from a selected photo.
 * Pixels are unsigned 8-bit luminance stored as Int values in [0, 255].
 * This buffer is transient and is never serialized into a drawing document.
 */
data class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width >= 3 && height >= 3) { "Image is too small to detect edges." }
        require(pixels.size == width * height) { "Invalid grayscale pixel buffer." }
        require(pixels.all { it in 0..255 }) { "Grayscale pixels must be in [0, 255]." }
    }
}
