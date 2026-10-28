package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Converts a sparse edge map into editable vector strokes. The algorithm intentionally uses
 * gentle simplification and a moderate short-line filter to balance visual detail and cleanliness.
 */
class EdgeMapVectorizer(
    private val minPathLength: Double = 1.0,
    private val simplifyEpsilon: Double = 0.35,
    private val maxStrokes: Int = 1_500,
    private val maxPoints: Int = 6_500,
) {
    fun convert(edgeMap: EdgeMap): DrawingDocument {
        val width = edgeMap.width
        val height = edgeMap.height
        val edgePixels = BooleanArray(edgeMap.strength.size) {
            (edgeMap.strength[it].toInt() and 0xFF) > 0
        }
        val rawPaths = tracePaths(edgePixels, width, height)
        val candidates = rawPaths.mapNotNull { raw ->
            val length = pathLength(raw, width)
            if (length < minPathLength || raw.size < 2) return@mapNotNull null
            val simplified = simplify(raw, width, simplifyEpsilon)
            if (simplified.size < 2) return@mapNotNull null
            val averageStrength = raw.map { edgeMap.strength[it].toInt() and 0xFF }.average()
            Candidate(simplified, length, averageStrength)
        }.sortedWith(
            compareByDescending<Candidate> { it.length }
                .thenByDescending { it.averageStrength },
        )

        val strokes = ArrayList<DrawingStroke>()
        var pointCount = 0
        for (candidate in candidates) {
            if (strokes.size >= maxStrokes || pointCount >= maxPoints) break
            val available = maxPoints - pointCount
            val selected = when {
                candidate.points.size <= available -> candidate.points
                available >= 2 && candidate.points.size >= available -> downsample(candidate.points, available)
                else -> continue
            }
            if (selected.size < 2) continue

            val normalized = selected.map { index ->
                listOf(
                    quantize((index % width).toDouble() / (width - 1).toDouble()),
                    quantize((index / width).toDouble() / (height - 1).toDouble()),
                )
            }
            // The "Карта" preview draws each edge in black with alpha based on its
            // confidence. Convert that same alpha into a grayscale stroke so the editable
            // drawing keeps the map's light-to-dark appearance rather than turning every
            // detected contour into a uniformly black, thick line.
            val edgeStrength = candidate.averageStrength.coerceIn(0.0, 255.0)
            val previewAlpha = 0.25 + 0.75 * edgeStrength / 255.0
            val gray = ((1.0 - previewAlpha) * 255.0).roundToInt().coerceIn(0, 255)
            val grayHex = gray.toString(16).uppercase().padStart(2, '0')
            val strokeColor = "#$grayHex$grayHex$grayHex"
            strokes += DrawingStroke(
                color = strokeColor,
                // Use two display pixels for map-derived contours: one pixel looked too faint
                // compared with the "Карта найденных границ" preview.
                width = 2.0f,
                points = normalized,
            )
            pointCount += normalized.size
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

    private data class Candidate(
        val points: List<Int>,
        val length: Double,
        val averageStrength: Double,
    )

    private fun tracePaths(edges: BooleanArray, width: Int, height: Int): List<List<Int>> {
        val degree = ByteArray(edges.size)
        for (index in edges.indices) {
            if (edges[index]) degree[index] = neighbours(index, width, height, edges).size.toByte()
        }
        val visited = HashSet<Long>()
        fun key(a: Int, b: Int): Long = min(a, b).toLong() * edges.size.toLong() + max(a, b).toLong()

        fun trace(start: Int, first: Int): List<Int> {
            val path = ArrayList<Int>()
            path += start
            var previous = start
            var current = first
            visited += key(previous, current)
            var guard = 0
            while (guard++ <= edges.size) {
                if (current == start) {
                    path += current
                    break
                }
                path += current
                val nextCandidates = neighbours(current, width, height, edges)
                    .filter { it != previous && key(current, it) !in visited }
                if (nextCandidates.isEmpty()) break

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

        val paths = ArrayList<List<Int>>()
        for (index in edges.indices) {
            if (!edges[index] || degree[index].toInt() == 2) continue
            for (neighbour in neighbours(index, width, height, edges)) {
                if (key(index, neighbour) !in visited) {
                    val path = trace(index, neighbour)
                    if (path.size >= 2) paths += path
                }
            }
        }
        // Remaining unvisited edges are closed loops or chains composed only of degree-two nodes.
        for (index in edges.indices) {
            if (!edges[index]) continue
            for (neighbour in neighbours(index, width, height, edges)) {
                if (key(index, neighbour) !in visited) {
                    val path = trace(index, neighbour)
                    if (path.size >= 2) paths += path
                }
            }
        }
        return paths
    }

    private fun neighbours(index: Int, width: Int, height: Int, edges: BooleanArray): List<Int> {
        val x = index % width
        val y = index / width
        val result = ArrayList<Int>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            val neighbour = ny * width + nx
            if (!edges[neighbour]) continue
            // Avoid diagonal shortcuts when a horizontal or vertical route already exists.
            if (dx != 0 && dy != 0) {
                if (edges[y * width + nx] || edges[ny * width + x]) continue
            }
            result += neighbour
        }
        return result
    }

    private fun pathLength(path: List<Int>, width: Int): Double {
        var result = 0.0
        for (index in 1 until path.size) {
            val dx = (path[index] % width - path[index - 1] % width).toDouble()
            val dy = (path[index] / width - path[index - 1] / width).toDouble()
            result += sqrt(dx * dx + dy * dy)
        }
        return result
    }

    private fun simplify(path: List<Int>, width: Int, epsilon: Double): List<Int> {
        if (path.size <= 3 || epsilon <= 0.0) return path
        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.lastIndex] = true
        val stack = ArrayList<Pair<Int, Int>>()
        stack += 0 to path.lastIndex
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeAt(stack.lastIndex)
            val a = path[start]
            val b = path[end]
            val x1 = (a % width).toDouble()
            val y1 = (a / width).toDouble()
            val x2 = (b % width).toDouble()
            val y2 = (b / width).toDouble()
            val dx = x2 - x1
            val dy = y2 - y1
            val denominator = dx * dx + dy * dy
            var farthest = -1
            var maxDistance = 0.0
            for (index in start + 1 until end) {
                val point = path[index]
                val px = (point % width).toDouble()
                val py = (point / width).toDouble()
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
                stack += start to farthest
                stack += farthest to end
            }
        }
        return path.indices.filter { keep[it] }.map { path[it] }
    }

    private fun downsample(path: List<Int>, targetSize: Int): List<Int> {
        if (targetSize >= path.size) return path
        if (targetSize < 2) return emptyList()
        val result = ArrayList<Int>(targetSize)
        val step = (path.size - 1).toDouble() / (targetSize - 1).toDouble()
        for (index in 0 until targetSize) result += path[(index * step).roundToInt()]
        return result.distinct()
    }

    private fun quantize(value: Double): Double =
        (value * 10_000.0).roundToInt().coerceIn(0, 10_000) / 10_000.0
}
