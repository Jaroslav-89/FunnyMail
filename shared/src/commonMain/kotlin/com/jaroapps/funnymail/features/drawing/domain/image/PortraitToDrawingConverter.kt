package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Converts the V8 on-device person mask and named face landmarks into editable vector strokes.
 * Reuses V2 post-processing choices (5x5 Gaussian-style smoothing and closing, epsilon 1.35,
 * 8-14 px minimum semantic contour length, 5 px stroke width, and a 7,000-point budget).
 * Edge-gradient thresholds from V2 do not apply to the model's confidence mask.
 */
class PortraitToDrawingConverter(
    private val confidenceThreshold: Int = 145,
    private val maxStrokes: Int = 1_000,
    private val maxPoints: Int = 7_000,
) {
    fun convert(features: PortraitFeatures): DrawingDocument {
        val width = features.maskWidth
        val height = features.maskHeight
        // V2's 5x5 Gaussian-style smoothing is applied to model confidence before thresholding.
        // This keeps local mask noise from becoming jagged outlines.
        val smoothedConfidence = smoothConfidenceMask(features.personMask, width, height)
        val thresholded = BooleanArray(width * height) { index ->
            (smoothedConfidence[index].toInt() and 0xFF) >= confidenceThreshold
        }
        val closedMask = closeSmallMaskGaps(thresholded, width, height)
        val person = largestComponent(closedMask, width, height)
        val foregroundCount = person.count { it }
        if (foregroundCount < width * height * 0.015) throw NoContoursFoundException()

        val boundary = traceOuterContour(person, width, height)
        if (boundary.size < 8) throw NoContoursFoundException()
        val simplifiedBoundary = simplify(boundary, width, epsilon = 1.35)
        val strokes = ArrayList<DrawingStroke>()
        var pointCount = 0

        val rawSilhouettePoints = toNormalizedPoints(
            path = ensureClosed(simplifiedBoundary),
            width = width,
            height = height,
        )
        val silhouettePoints = if (rawSilhouettePoints.size > maxPoints) {
            downsampleNormalized(rawSilhouettePoints, maxPoints)
        } else {
            rawSilhouettePoints
        }
        if (silhouettePoints.size >= 4) {
            strokes += DrawingStroke(
                color = "#202124",
                width = 5f,
                points = silhouettePoints,
            )
            pointCount += silhouettePoints.size
        }

        // These contours are semantically meaningful (eyes, brows, nose, lips, face outline).
        // Keep a small number of them instead of detecting arbitrary internal photo edges.
        val minContourLength = (
            min(features.sourceWidth, features.sourceHeight) * 0.015
        ).roundToInt().coerceIn(8, 14).toDouble()
        val faceContours = features.faceContours
            .asSequence()
            .filter { it.points.size >= 4 }
            .filter {
                normalizedLength(it.points, features.sourceWidth, features.sourceHeight) >= minContourLength
            }
            .sortedByDescending {
                normalizedLength(it.points, features.sourceWidth, features.sourceHeight)
            }
            .toList()

        for (contour in faceContours) {
            if (strokes.size >= maxStrokes || pointCount >= maxPoints) break
            val simplifiedFaceContour = simplifyNormalized(
                points = contour.points,
                width = features.sourceWidth,
                height = features.sourceHeight,
                epsilon = 1.35,
            )
            var normalized = simplifiedFaceContour.map { point ->
                listOf(point.x.coerceIn(0.0, 1.0), point.y.coerceIn(0.0, 1.0))
            }
            if (contour.closed && normalized.isNotEmpty() && normalized.first() != normalized.last()) {
                normalized = normalized + listOf(normalized.first())
            }
            val remaining = maxPoints - pointCount
            if (normalized.size > remaining) {
                if (remaining < 2) break
                normalized = downsampleNormalized(normalized, remaining)
            }
            if (normalized.size < 2) continue
            strokes += DrawingStroke(
                color = "#202124",
                width = 5f,
                points = normalized,
            )
            pointCount += normalized.size
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

    private fun smoothConfidenceMask(mask: ByteArray, width: Int, height: Int): ByteArray {
        // Same separable 5x5 Gaussian-style kernel as V2: [1, 4, 6, 4, 1].
        val weights = intArrayOf(1, 4, 6, 4, 1)
        return ByteArray(mask.size) { index ->
            val x = index % width
            val y = index / width
            var weightedSum = 0
            for (dy in -2..2) for (dx in -2..2) {
                val sampleX = (x + dx).coerceIn(0, width - 1)
                val sampleY = (y + dy).coerceIn(0, height - 1)
                val value = mask[sampleY * width + sampleX].toInt() and 0xFF
                weightedSum += value * weights[dy + 2] * weights[dx + 2]
            }
            (weightedSum / 256).coerceIn(0, 255).toByte()
        }
    }

    private fun closeSmallMaskGaps(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        // V2's 5x5 morphology is adapted to the semantic person mask to bridge small edge breaks.
        val radius = 2
        val dilated = BooleanArray(mask.size)
        for (y in radius until height - radius) for (x in radius until width - radius) {
            var found = false
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (mask[(y + dy) * width + x + dx]) found = true
            }
            dilated[y * width + x] = found
        }
        val closed = BooleanArray(mask.size)
        for (y in radius until height - radius) for (x in radius until width - radius) {
            var filled = true
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (!dilated[(y + dy) * width + x + dx]) filled = false
            }
            closed[y * width + x] = filled
        }
        return closed
    }

    private fun largestComponent(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var largest = IntArray(0)
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val current = queue[head++]
                val x = current % width
                val y = current / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (mask[next] && !visited[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            if (tail > largest.size) largest = queue.copyOf(tail)
        }
        if (largest.isEmpty()) return BooleanArray(mask.size)
        return BooleanArray(mask.size).also { result ->
            largest.forEach { result[it] = true }
        }
    }

    /**
     * Moore-neighbour tracing returns the outside border of the dominant connected person mask.
     * The search follows foreground boundary pixels and closes when it returns to the start.
     */
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

    private fun simplify(path: List<Int>, width: Int, epsilon: Double): List<Int> {
        if (path.size <= 4) return path
        val stride = ceil(path.size / 4_096.0).toInt().coerceAtLeast(1)
        val sampled = if (stride == 1) path else buildList {
            var index = 0
            while (index < path.size) {
                add(path[index])
                index += stride
            }
            if (lastOrNull() != path.last()) add(path.last())
        }
        if (sampled.size <= 4) return sampled

        val keep = BooleanArray(sampled.size)
        keep[0] = true
        keep[sampled.lastIndex] = true
        val ranges = ArrayList<Pair<Int, Int>>()
        ranges += 0 to sampled.lastIndex
        while (ranges.isNotEmpty()) {
            val (start, end) = ranges.removeAt(ranges.lastIndex)
            val a = sampled[start]
            val b = sampled[end]
            val x1 = (a % width).toDouble()
            val y1 = (a / width).toDouble()
            val x2 = (b % width).toDouble()
            val y2 = (b / width).toDouble()
            val vx = x2 - x1
            val vy = y2 - y1
            val denominator = vx * vx + vy * vy
            var farthest = -1
            var maxDistance = 0.0
            for (index in start + 1 until end) {
                val point = sampled[index]
                val px = (point % width).toDouble()
                val py = (point / width).toDouble()
                val projection = if (denominator == 0.0) 0.0 else
                    (((px - x1) * vx + (py - y1) * vy) / denominator).coerceIn(0.0, 1.0)
                val nearestX = x1 + projection * vx
                val nearestY = y1 + projection * vy
                val distance = sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
                if (distance > maxDistance) {
                    maxDistance = distance
                    farthest = index
                }
            }
            if (farthest >= 0 && maxDistance > epsilon) {
                keep[farthest] = true
                ranges += start to farthest
                ranges += farthest to end
            }
        }
        return sampled.indices.filter { keep[it] }.map { sampled[it] }
    }

    private fun ensureClosed(path: List<Int>): List<Int> =
        if (path.isNotEmpty() && path.first() == path.last()) path else path + path.first()

    private fun toNormalizedPoints(path: List<Int>, width: Int, height: Int): List<List<Double>> =
        path.map { point ->
            listOf(
                (point % width).toDouble() / (width - 1).toDouble(),
                (point / width).toDouble() / (height - 1).toDouble(),
            )
        }

    private fun normalizedLength(points: List<NormalizedPhotoPoint>, width: Int, height: Int): Double {
        var length = 0.0
        for (index in 1 until points.size) {
            val first = points[index - 1]
            val second = points[index]
            val dx = (second.x - first.x) * width
            val dy = (second.y - first.y) * height
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }

    /**
     * V2 Douglas-Peucker simplification applied to the model's face landmarks in image-pixel space.
     * This reduces redundant landmark points without changing the normalized JSON coordinate format.
     */
    private fun simplifyNormalized(
        points: List<NormalizedPhotoPoint>,
        width: Int,
        height: Int,
        epsilon: Double,
    ): List<NormalizedPhotoPoint> {
        if (points.size <= 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val ranges = ArrayList<Pair<Int, Int>>()
        ranges += 0 to points.lastIndex
        while (ranges.isNotEmpty()) {
            val (start, end) = ranges.removeAt(ranges.lastIndex)
            val first = points[start]
            val last = points[end]
            val x1 = first.x * width
            val y1 = first.y * height
            val x2 = last.x * width
            val y2 = last.y * height
            val dx = x2 - x1
            val dy = y2 - y1
            val denominator = dx * dx + dy * dy
            var farthest = -1
            var maxDistance = 0.0
            for (index in start + 1 until end) {
                val point = points[index]
                val px = point.x * width
                val py = point.y * height
                val projection = if (denominator == 0.0) 0.0 else
                    (((px - x1) * dx + (py - y1) * dy) / denominator).coerceIn(0.0, 1.0)
                val nearestX = x1 + projection * dx
                val nearestY = y1 + projection * dy
                val distance = sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
                if (distance > maxDistance) {
                    maxDistance = distance
                    farthest = index
                }
            }
            if (farthest >= 0 && maxDistance > epsilon) {
                keep[farthest] = true
                ranges += start to farthest
                ranges += farthest to end
            }
        }
        return points.indices.filter { keep[it] }.map { points[it] }
    }

    private fun downsampleNormalized(
        points: List<List<Double>>,
        targetSize: Int,
    ): List<List<Double>> {
        if (targetSize >= points.size) return points
        if (targetSize < 2) return emptyList()
        val result = ArrayList<List<Double>>(targetSize)
        val step = (points.size - 1).toDouble() / (targetSize - 1).toDouble()
        for (index in 0 until targetSize) result += points[(index * step).roundToInt()]
        return result.distinct()
    }
}
