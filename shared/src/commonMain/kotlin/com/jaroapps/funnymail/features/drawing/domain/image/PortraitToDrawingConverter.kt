package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.roundToInt

/**
 * Converts raw on-device model outputs to vector strokes.
 *
 * Deliberately does not blur the confidence mask, keep only the largest component, simplify
 * contours, quantize coordinates, or filter face contours by length. The only segmentation
 * step is the confidence cutoff needed to turn the model mask into drawable boundaries.
 * The point limit protects DrawingCodec's serialized-document size.
 */
class PortraitToDrawingConverter(
    private val confidenceThreshold: Int = 128,
    private val maxPoints: Int = 5_000,
) {
    fun convert(features: PortraitFeatures): DrawingDocument {
        val width = features.maskWidth
        val height = features.maskHeight
        val modelMask = BooleanArray(features.personMask.size) { index ->
            (features.personMask[index].toInt() and 0xFF) >= confidenceThreshold
        }
        val silhouetteContours = traceAllModelContours(modelMask, width, height)
        val strokes = ArrayList<DrawingStroke>()
        var pointCount = 0

        // Keep every model-produced mask component, not just the dominant silhouette.
        // The larger contours are emitted first so the person outline survives the safety cap.
        for (contour in silhouetteContours) {
            if (pointCount >= maxPoints) break
            val points = contour.map { index ->
                listOf(
                    (index % width).toDouble() / (width - 1).toDouble(),
                    (index / width).toDouble() / (height - 1).toDouble(),
                )
            }
            if (points.size < 2 || points.size > maxPoints - pointCount) continue
            if (points.distinct().size < 2) continue
            strokes += DrawingStroke(
                color = "#202124",
                width = 2f,
                points = points,
            )
            pointCount += points.size
        }

        // Preserve all points emitted by the platform face-landmark models, without simplification.
        for (contour in features.faceContours) {
            if (pointCount >= maxPoints) break
            if (contour.points.size < 2) continue
            var points = contour.points.map { point -> listOf(point.x, point.y) }
            if (contour.closed && points.first() != points.last()) {
                points = points + listOf(points.first())
            }
            if (points.size > maxPoints - pointCount) continue
            strokes += DrawingStroke(
                color = "#202124",
                width = 2f,
                points = points,
            )
            pointCount += points.size
        }

        if (strokes.isEmpty()) throw NoContoursFoundException()
        val canvasWidth: Int
        val canvasHeight: Int
        if (features.sourceWidth >= features.sourceHeight) {
            canvasWidth = 1_000
            canvasHeight = (1_000.0 * features.sourceHeight / features.sourceWidth)
                .roundToInt().coerceAtLeast(1)
        } else {
            canvasHeight = 1_000
            canvasWidth = (1_000.0 * features.sourceWidth / features.sourceHeight)
                .roundToInt().coerceAtLeast(1)
        }
        return DrawingDocument(
            canvas = DrawingCanvas(
                width = canvasWidth,
                height = canvasHeight,
                backgroundColor = "#FFFFFF",
                backgroundMode = "SOLID",
            ),
            strokes = strokes,
        )
    }

    /**
     * Splits the thresholded model mask into its connected components and traces each component's
     * full pixel boundary. No component is discarded by size or dominance.
     */
    private fun traceAllModelContours(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): List<List<Int>> {
        val remaining = mask.copyOf()
        val componentMask = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val contours = ArrayList<List<Int>>()

        for (start in remaining.indices) {
            if (!remaining[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            remaining[start] = false
            componentMask[start] = true

            while (head < tail) {
                val current = queue[head++]
                val x = current % width
                val y = current / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val neighbour = ny * width + nx
                    if (remaining[neighbour]) {
                        remaining[neighbour] = false
                        componentMask[neighbour] = true
                        queue[tail++] = neighbour
                    }
                }
            }

            val boundary = traceOuterContour(componentMask, width, height)
            if (boundary.size >= 2) contours += boundary
            for (index in 0 until tail) componentMask[queue[index]] = false
        }
        return contours.sortedByDescending { it.size }
    }

    private fun traceOuterContour(mask: BooleanArray, width: Int, height: Int): List<Int> {
        val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
        var start = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!mask[index]) continue
                if (x == 0 || !mask[index - 1] ||
                    y == 0 || !mask[index - width]
                ) {
                    start = index
                    break
                }
            }
            if (start >= 0) break
        }
        if (start < 0) return emptyList()

        fun directionIndex(deltaX: Int, deltaY: Int): Int {
            for (index in 0 until 8) {
                if (dx[index] == deltaX && dy[index] == deltaY) return index
            }
            return 6 // West is the initial backtrack neighbour.
        }

        val path = ArrayList<Int>()
        var current = start
        var backX = start % width - 1
        var backY = start / width
        var steps = 0
        while (steps++ < mask.size * 2) {
            path += current
            val x = current % width
            val y = current / width
            val backDirection = directionIndex(backX - x, backY - y)
            var next = -1
            var previousDirection = -1
            for (offset in 1..8) {
                val direction = (backDirection + offset) % 8
                val nx = x + dx[direction]
                val ny = y + dy[direction]
                if (nx !in 0 until width || ny !in 0 until height) continue
                if (mask[ny * width + nx]) {
                    next = ny * width + nx
                    previousDirection = (direction + 7) % 8
                    break
                }
            }
            if (next < 0) break
            backX = x + dx[previousDirection]
            backY = y + dy[previousDirection]
            current = next
            if (current == start) {
                path += start
                break
            }
        }
        return if (path.lastOrNull() == path.firstOrNull()) path else path + path.first()
    }

}
