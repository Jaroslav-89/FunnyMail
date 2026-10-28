package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts the same visible cells used by the "Карта найденных границ" preview into editable
 * strokes. No contour-length filtering, simplification, or point downsampling is performed:
 * short contours and isolated detected cells must remain visible in the final drawing.
 */
class EdgeMapVectorizer(
    private val maxStrokes: Int = 2_000,
    private val maxPoints: Int = 20_000,
) {
    fun convert(edgeMap: EdgeMap): DrawingDocument {
        // The diagnostic map shows the maximum edge strength in each display cell. Vectorize
        // that same map instead of using a higher-resolution source and then discarding detail.
        val displayMap = mapAtPreviewResolution(edgeMap)
        val width = displayMap.width
        val height = displayMap.height
        val edgePixels = BooleanArray(displayMap.strength.size) {
            (displayMap.strength[it].toInt() and 0xFF) > 0
        }
        val rawPaths = tracePaths(edgePixels, width, height)
        val candidates = rawPaths.map { raw ->
            val averageStrength = raw
                .map { displayMap.strength[it].toInt() and 0xFF }
                .average()
            Candidate(raw, averageStrength)
        }

        // Never silently drop details to fit the document limits. The limits are checked before
        // making the drawing; if they are exceeded, import fails clearly rather than trimming
        // short contours first or keeping only the largest paths.
        val pointCount = candidates.sumOf { it.points.size }
        require(candidates.size <= maxStrokes) {
            "Карта содержит ${candidates.size} контуров — это превышает лимит $maxStrokes. Детали не были удалены."
        }
        require(pointCount <= maxPoints) {
            "Карта содержит $pointCount точек — это превышает лимит $maxPoints. Детали не были удалены."
        }

        val strokes = ArrayList<DrawingStroke>(candidates.size)
        for (candidate in candidates) {
            val normalized = candidate.points.map { index ->
                listOf(
                    quantize(((index % width) + 0.5) / width.toDouble()),
                    quantize(((index / width) + 0.5) / height.toDouble()),
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
        // A single isolated edge cell has no graph edge to trace, but it is still visible on the
        // diagnostic map. Keep it as a one-point stroke rather than discarding it.
        for (index in edges.indices) {
            if (edges[index] && degree[index].toInt() == 0) paths += listOf(index)
        }
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

    private fun mapAtPreviewResolution(source: EdgeMap): EdgeMap {
        val stepX = (source.width / 320).coerceAtLeast(1)
        val stepY = (source.height / 320).coerceAtLeast(1)
        val width = (source.width + stepX - 1) / stepX
        val height = (source.height + stepY - 1) / stepY
        val strength = ByteArray(width * height)

        var displayY = 0
        for (sourceY in 0 until source.height step stepY) {
            val cellHeight = min(stepY, source.height - sourceY)
            var displayX = 0
            for (sourceX in 0 until source.width step stepX) {
                val cellWidth = min(stepX, source.width - sourceX)
                var maximumStrength = 0
                for (sampleY in sourceY until sourceY + cellHeight) {
                    for (sampleX in sourceX until sourceX + cellWidth) {
                        maximumStrength = max(
                            maximumStrength,
                            source.strength[sampleY * source.width + sampleX].toInt() and 0xFF,
                        )
                    }
                }
                if (maximumStrength > 0) {
                    strength[displayY * width + displayX] = maximumStrength.toByte()
                }
                displayX++
            }
            displayY++
        }
        return EdgeMap(width, height, strength)
    }

    private fun quantize(value: Double): Double =
        (value * 10_000.0).roundToInt().coerceIn(0, 10_000) / 10_000.0
}
