package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class NoContoursFoundException : IllegalStateException("No clear contours found")

/**
 * Local edge-to-vector conversion.
 *
 * A higher vector budget, sub-pixel coordinate quantization and more continuous contour tracing
 * preserve much more detail without increasing the 256 KiB serialized drawing limit.
 */
class PhotoToDrawingConverter(
    private val maxStrokes: Int = 1_000,
    private val maxPoints: Int = 7_000,
) {
    private data class Candidate(val points: List<Int>, val sourceLength: Int)

    fun convert(image: GrayImage): DrawingDocument {
        val width = image.width
        val height = image.height
        require(width >= 3 && height >= 3)

        val gray = boxBlur(image.pixels, width, height)
        val gx = FloatArray(gray.size)
        val gy = FloatArray(gray.size)
        val magnitude = FloatArray(gray.size)
        var maximumMagnitude = 0f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val tl = gray[index - width - 1]
                val t = gray[index - width]
                val tr = gray[index - width + 1]
                val l = gray[index - 1]
                val r = gray[index + 1]
                val bl = gray[index + width - 1]
                val b = gray[index + width]
                val br = gray[index + width + 1]

                gx[index] = (-tl - 2 * l - bl + tr + 2 * r + br).toFloat()
                gy[index] = (-tl - 2 * t - tr + bl + 2 * b + br).toFloat()
                magnitude[index] = sqrt(gx[index] * gx[index] + gy[index] * gy[index])
                if (magnitude[index] > maximumMagnitude) maximumMagnitude = magnitude[index]
            }
        }
        if (maximumMagnitude < 5f) throw NoContoursFoundException()

        // Non-maximum suppression thins edges while retaining their location.
        val thinned = FloatArray(gray.size)
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

        // The previous threshold saturated at 96 for most photos, discarding weaker but useful
        // contours. Lower adaptive thresholds recover detail; a small blur and NMS reduce noise.
        val high = (maximumMagnitude * 0.08f).coerceIn(4f, 64f).coerceAtMost(maximumMagnitude)
        val edges = hysteresis(thinned, width, height, high * 0.34f, high)
        val candidates = traceEdges(edges, width, height)
            .asSequence()
            .filter { it.size >= 2 }
            .map { Candidate(simplify(it, width, epsilon = 0.95), it.size) }
            .filter { it.points.size >= 2 }
            .sortedByDescending { it.sourceLength }
            .toList()
        if (candidates.isEmpty()) throw NoContoursFoundException()

        val strokes = ArrayList<DrawingStroke>(min(maxStrokes, candidates.size))
        var pointCount = 0
        for (candidate in candidates) {
            if (strokes.size >= maxStrokes || pointCount >= maxPoints) break
            val remaining = maxPoints - pointCount
            val points = if (candidate.points.size <= remaining) {
                candidate.points
            } else {
                downsample(candidate.points, remaining)
            }
            if (points.size < 2) continue

            strokes += DrawingStroke(
                color = "#202124",
                // A two-pixel stroke became sub-pixel on typical phone canvases.
                width = 4f,
                points = points.map { point ->
                    listOf(
                        quantizeCoordinate((point % width).toDouble() / (width - 1).toDouble()),
                        quantizeCoordinate((point / width).toDouble() / (height - 1).toDouble()),
                    )
                },
            )
            pointCount += points.size
        }
        if (strokes.isEmpty()) throw NoContoursFoundException()

        val canvasWidth: Int
        val canvasHeight: Int
        if (width >= height) {
            canvasWidth = 1_000
            canvasHeight = (1_000.0 * height / width).roundToInt().coerceAtLeast(1)
        } else {
            canvasHeight = 1_000
            canvasWidth = (1_000.0 * width / height).roundToInt().coerceAtLeast(1)
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

    private fun quantizeCoordinate(value: Double): Double =
        (value * 10_000.0).roundToInt().coerceIn(0, 10_000) / 10_000.0

    private fun boxBlur(source: IntArray, width: Int, height: Int): IntArray {
        val result = source.copyOf()
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            var sum = 0
            for (dy in -1..1) for (dx in -1..1) sum += source[(y + dy) * width + x + dx]
            result[y * width + x] = sum / 9
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
        val result = BooleanArray(magnitude.size)
        val queue = IntArray(magnitude.size)
        var head = 0
        var tail = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val index = y * width + x
            if (magnitude[index] >= high) {
                result[index] = true
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
                if (nx !in 1 until width - 1 || ny !in 1 until height - 1) continue
                val neighbour = ny * width + nx
                if (!result[neighbour] && magnitude[neighbour] >= low) {
                    result[neighbour] = true
                    queue[tail++] = neighbour
                }
            }
        }
        return result
    }

    /**
     * Uses 8-connectivity for diagonal contours but discards redundant diagonal links whenever
     * an orthogonal route already connects the same two pixels. This reduces false branch nodes.
     */
    private fun edgeNeighbours(index: Int, width: Int, height: Int, edges: BooleanArray): IntArray {
        val x = index % width
        val y = index / width
        val result = IntArray(8)
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx !in 1 until width - 1 || ny !in 1 until height - 1) continue
            val neighbour = ny * width + nx
            if (!edges[neighbour]) continue
            if (dx != 0 && dy != 0) {
                val horizontal = y * width + nx
                val vertical = ny * width + x
                if (edges[horizontal] || edges[vertical]) continue
            }
            result[count++] = neighbour
        }
        return result.copyOf(count)
    }

    private fun traceEdges(edges: BooleanArray, width: Int, height: Int): List<List<Int>> {
        val size = edges.size
        val degree = ByteArray(size)
        for (index in edges.indices) {
            if (edges[index]) degree[index] = edgeNeighbours(index, width, height, edges).size.toByte()
        }
        val visited = HashSet<Long>()
        fun key(a: Int, b: Int): Long = min(a, b).toLong() * size.toLong() + max(a, b).toLong()

        fun trace(start: Int, first: Int): List<Int> {
            val path = ArrayList<Int>()
            path += start
            var previous = start
            var current = first
            visited += key(previous, current)
            var guard = 0
            while (guard++ <= size) {
                if (current == start) {
                    path += current
                    break
                }
                path += current
                val nextCandidates = edgeNeighbours(current, width, height, edges)
                    .filter { it != previous && key(current, it) !in visited }
                if (nextCandidates.isEmpty()) break

                // At junctions, continue along the edge with the smallest turn. The previous
                // implementation stopped at every node whose degree was not exactly two,
                // splitting long contours into many tiny fragments which were then discarded.
                val prevX = (current % width - previous % width).toDouble()
                val prevY = (current / width - previous / width).toDouble()
                val next = nextCandidates.maxByOrNull { candidate ->
                    val nextX = (candidate % width - current % width).toDouble()
                    val nextY = (candidate / width - current / width).toDouble()
                    prevX * nextX + prevY * nextY
                } ?: break
                visited += key(current, next)
                previous = current
                current = next
            }
            return path
        }

        val result = ArrayList<List<Int>>()
        for (index in edges.indices) {
            if (!edges[index] || degree[index].toInt() == 2) continue
            for (neighbour in edgeNeighbours(index, width, height, edges)) {
                if (key(index, neighbour) !in visited) {
                    val path = trace(index, neighbour)
                    if (path.size >= 2) result += path
                }
            }
        }
        // Remaining edges belong to loops or isolated chains whose every vertex has degree two.
        for (index in edges.indices) {
            if (!edges[index]) continue
            for (neighbour in edgeNeighbours(index, width, height, edges)) {
                if (key(index, neighbour) !in visited) {
                    val path = trace(index, neighbour)
                    if (path.size >= 2) result += path
                }
            }
        }
        return result
    }

    private fun simplify(path: List<Int>, width: Int, epsilon: Double): List<Int> {
        if (path.size <= 3) return path
        val stride = ceil(path.size / 4_096.0).toInt().coerceAtLeast(1)
        val sampled = if (stride == 1) path else buildList {
            var index = 0
            while (index < path.size) {
                add(path[index])
                index += stride
            }
            if (lastOrNull() != path.last()) add(path.last())
        }
        if (sampled.size <= 3) return sampled

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
            val dx = x2 - x1
            val dy = y2 - y1
            val denominator = dx * dx + dy * dy
            var farthest = -1
            var maxDistance = 0.0
            for (i in start + 1 until end) {
                val point = sampled[i]
                val px = (point % width).toDouble()
                val py = (point / width).toDouble()
                val projection = if (denominator == 0.0) 0.0 else
                    (((px - x1) * dx + (py - y1) * dy) / denominator).coerceIn(0.0, 1.0)
                val nearestX = x1 + projection * dx
                val nearestY = y1 + projection * dy
                val distance = sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
                if (distance > maxDistance) {
                    maxDistance = distance
                    farthest = i
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

    private fun downsample(path: List<Int>, targetSize: Int): List<Int> {
        if (targetSize >= path.size) return path
        if (targetSize < 2) return emptyList()
        val result = ArrayList<Int>(targetSize)
        val step = (path.size - 1).toDouble() / (targetSize - 1).toDouble()
        for (index in 0 until targetSize) result += path[(index * step).roundToInt()]
        return result.distinct()
    }
}
