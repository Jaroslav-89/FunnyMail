package com.jaroapps.funnymail.features.drawing.domain.image

import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/**
 * First, deterministic edge-detection baseline for V10.
 *
 * It implements a small Gaussian blur, Sobel gradients, non-maximum suppression, and hysteresis.
 * The resulting EdgeMap contract intentionally does not depend on this implementation; a
 * neural edge detector such as DexiNed can replace it later without changing vectorization.
 */
class PhotoEdgeDetector(
    private val relativeHighThreshold: Float = 0.12f,
    private val lowThresholdRatio: Float = 0.38f,
) {
    init {
        require(relativeHighThreshold in 0.01f..0.95f)
        require(lowThresholdRatio in 0.05f..0.95f)
    }

    fun detect(image: GrayImage): EdgeMap {
        val width = image.width
        val height = image.height
        val blurred = gaussianBlur3x3(image.pixels, width, height)
        val gx = FloatArray(blurred.size)
        val gy = FloatArray(blurred.size)
        val magnitude = FloatArray(blurred.size)
        var maximumMagnitude = 0f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val topLeft = blurred[index - width - 1]
                val top = blurred[index - width]
                val topRight = blurred[index - width + 1]
                val left = blurred[index - 1]
                val right = blurred[index + 1]
                val bottomLeft = blurred[index + width - 1]
                val bottom = blurred[index + width]
                val bottomRight = blurred[index + width + 1]

                gx[index] = (-topLeft - 2 * left - bottomLeft + topRight + 2 * right + bottomRight).toFloat()
                gy[index] = (-topLeft - 2 * top - topRight + bottomLeft + 2 * bottom + bottomRight).toFloat()
                magnitude[index] = sqrt(gx[index] * gx[index] + gy[index] * gy[index])
                maximumMagnitude = max(maximumMagnitude, magnitude[index])
            }
        }

        if (maximumMagnitude < 5f) throw NoContoursFoundException()

        // Non-maximum suppression keeps local gradient peaks and discards thicker edge responses.
        val thinned = FloatArray(magnitude.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val current = magnitude[index]
                if (current <= 0f) continue

                var angle = atan2(gy[index].toDouble(), gx[index].toDouble()) * 180.0 / PI
                if (angle < 0.0) angle += 180.0
                val first: Int
                val second: Int
                when {
                    angle < 22.5 || angle >= 157.5 -> {
                        first = index - 1
                        second = index + 1
                    }
                    angle < 67.5 -> {
                        first = index - width + 1
                        second = index + width - 1
                    }
                    angle < 112.5 -> {
                        first = index - width
                        second = index + width
                    }
                    else -> {
                        first = index - width - 1
                        second = index + width + 1
                    }
                }
                if (current >= magnitude[first] && current >= magnitude[second]) {
                    thinned[index] = current
                }
            }
        }

        val normalized = ByteArray(thinned.size)
        for (index in thinned.indices) {
            normalized[index] = (thinned[index] * 255f / maximumMagnitude)
                .toInt().coerceIn(0, 255).toByte()
        }

        val high = (maximumMagnitude * relativeHighThreshold).coerceAtLeast(5f)
        val low = high * lowThresholdRatio
        val retained = hysteresis(thinned, width, height, low, high)
        val output = ByteArray(thinned.size)
        for (index in output.indices) {
            if (retained[index]) output[index] = normalized[index]
        }
        val map = EdgeMap(width, height, output)
        if (map.edgeCount() < 2) throw NoContoursFoundException()
        return map
    }

    private fun gaussianBlur3x3(source: IntArray, width: Int, height: Int): IntArray {
        val result = source.copyOf()
        val weights = intArrayOf(1, 2, 1)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) {
                    sum += source[(y + dy) * width + x + dx] * weights[dy + 1] * weights[dx + 1]
                }
                result[y * width + x] = sum / 16
            }
        }
        return result
    }

    private fun hysteresis(
        magnitude: FloatArray,
        width: Int,
        height: Int,
        low: Float,
        high: Float,
    ): BooleanArray {
        val retained = BooleanArray(magnitude.size)
        val queue = IntArray(magnitude.size)
        var head = 0
        var tail = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (magnitude[index] >= high) {
                    retained[index] = true
                    queue[tail++] = index
                }
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
                if (nx !in 1 until width - 1 || ny !in 1 until height - 1) continue
                val neighbour = ny * width + nx
                if (!retained[neighbour] && magnitude[neighbour] >= low) {
                    retained[neighbour] = true
                    queue[tail++] = neighbour
                }
            }
        }
        return retained
    }
}
