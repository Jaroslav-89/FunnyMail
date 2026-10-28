package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * Sparse edge map. Zero means no retained edge; non-zero values encode edge strength as unsigned
 * 8-bit confidence. The map is the interface between a detector (classical now, neural later)
 * and the platform-independent vectorizer.
 */
data class EdgeMap(
    val width: Int,
    val height: Int,
    val strength: ByteArray,
) {
    init {
        require(width >= 3 && height >= 3)
        require(strength.size == width * height)
    }

    fun edgeCount(): Int = strength.count { (it.toInt() and 0xFF) > 0 }
}

/** Pluggable map producer. Neural and classical detectors can share the same vectorizer. */
fun interface EdgeMapDetector {
    fun detect(image: GrayImage): EdgeMap
}
