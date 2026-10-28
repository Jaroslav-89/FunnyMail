package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * Edge map contract shared by classic and neural detectors. Zero is background; non-zero values
 * represent retained edge pixels or (before thresholding) neural edge confidence.
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

    /**
     * Converts a dense neural confidence map into sparse drawable edges using two thresholds.
     * Weak pixels are retained only if 8-connected to a strong edge. This avoids vectorizing the
     * entire grayscale confidence field as a single dense collection of paths.
     */
    fun thresholdAndLink(
        lowThreshold: Int = 38,
        highThreshold: Int = 82,
    ): EdgeMap {
        require(lowThreshold in 0..255)
        require(highThreshold in 1..255)
        require(lowThreshold <= highThreshold)

        val retained = BooleanArray(strength.size)
        val queue = IntArray(strength.size)
        var head = 0
        var tail = 0

        for (index in strength.indices) {
            val confidence = strength[index].toInt() and 0xFF
            if (confidence >= highThreshold) {
                retained[index] = true
                queue[tail++] = index
            }
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val neighbour = ny * width + nx
                val confidence = strength[neighbour].toInt() and 0xFF
                if (!retained[neighbour] && confidence >= lowThreshold) {
                    retained[neighbour] = true
                    queue[tail++] = neighbour
                }
            }
        }

        return EdgeMap(width, height, ByteArray(strength.size) { index ->
            if (retained[index]) strength[index] else 0
        })
    }
}

/** Interface for detectors that can produce an EdgeMap from the shared grayscale representation. */
fun interface EdgeMapDetector {
    fun detect(image: GrayImage): EdgeMap
}
