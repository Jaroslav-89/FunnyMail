package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts the "Карта найденных границ" preview into editable strokes.
 *
 * Long contours are simplified with a pixel-space Ramer–Douglas–Peucker pass so nearly-collinear
 * map cells become a compact polyline. Weak isolated cells are removed conservatively: at most
 * 20% of isolated cells, starting with the weakest. Short contours and junctions are preserved.
 */
class EdgeMapVectorizer(
    private val maxStrokes: Int = 120_000,
    private val maxPoints: Int = 500_000,
) {
    fun convert(edgeMap: EdgeMap): DrawingDocument {
        // The diagnostic map shows the maximum edge strength in each display cell. Vectorize
        // that same map instead of using a higher-resolution source and then discarding detail.
        val preview = mapAtPreviewResolution(edgeMap)
        val displayMap = preview.edgeMap
        val width = displayMap.width
        val height = displayMap.height
        val edgePixels = BooleanArray(displayMap.strength.size) {
            (displayMap.strength[it].toInt() and 0xFF) > 0
        }
        discardWeakIsolatedCells(edgePixels, displayMap.strength, width, height)
        val rawPaths = tracePaths(edgePixels, width, height)
        val candidates = rawPaths.map { raw ->
            val averageStrength = raw
                .map { displayMap.strength[it].toInt() and 0xFF }
                .average()
            Candidate(simplifyPath(raw, width), averageStrength)
        }

        // Apply geometry reduction before checking limits. Remaining strokes/points are never
        // dropped merely to make the document fit its persistence limits.
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
                val cellX = index % width
                val cellY = index / width
                val sourceX = cellX * preview.stepX
                val sourceY = cellY * preview.stepY
                val cellWidth = min(preview.stepX, preview.sourceWidth - sourceX)
                val cellHeight = min(preview.stepY, preview.sourceHeight - sourceY)
                listOf(
                    quantize((sourceX + cellWidth / 2.0) / preview.sourceWidth),
                    quantize((sourceY + cellHeight / 2.0) / preview.sourceHeight),
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
                width = 2.6f,
                points = normalized,
            )
        }
        if (strokes.isEmpty()) throw NoContoursFoundException()

        val canvasWidth: Int
        val canvasHeight: Int
        if (preview.sourceWidth >= preview.sourceHeight) {
            canvasWidth = 1_000
            canvasHeight = (1_000.0 * preview.sourceHeight / preview.sourceWidth).roundToInt().coerceAtLeast(1)
        } else {
            canvasHeight = 1_000
            canvasWidth = (1_000.0 * preview.sourceWidth / preview.sourceHeight).roundToInt().coerceAtLeast(1)
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

    /**
     * Drops only the weakest fifth of truly isolated edge cells. Isolated cells are those with no
     * 8-connected edge neighbour; cells attached to contours are never removed by this pass.
     */
    private fun discardWeakIsolatedCells(
        edges: BooleanArray,
        strengths: ByteArray,
        width: Int,
        height: Int,
    ) {
        val isolated = edges.indices.filter { index ->
            edges[index] && neighbours(index, width, height, edges).isEmpty()
        }
        val discardCount = isolated.size / 5
        if (discardCount == 0) return
        isolated
            .sortedWith(compareBy<Int> { strengths[it].toInt() and 0xFF }.thenBy { it })
            .take(discardCount)
            .forEach { edges[it] = false }
    }

    /**
     * Simplifies long paths in display-cell coordinates, not normalized canvas coordinates.
     * This makes the tolerance meaningful (one preview-map cell) regardless of photo aspect ratio.
     */
    private fun simplifyPath(path: List<Int>, width: Int): List<Int> {
        if (path.size <= 4) return path

        // RDP's endpoints are identical on a closed loop, so split the loop at the vertex farthest
        // from its start and simplify both halves independently to preserve the loop's shape.
        if (path.first() == path.last()) {
            val loop = path.dropLast(1)
            if (loop.size <= 4) return path
            val startX = loop.first() % width
            val startY = loop.first() / width
            val pivotIndex = loop.indices.maxByOrNull { i ->
                val dx = (loop[i] % width - startX).toDouble()
                val dy = (loop[i] / width - startY).toDouble()
                dx * dx + dy * dy
            } ?: return path
            if (pivotIndex == 0) return path

            val firstHalf = simplifyOpenPath(loop.subList(0, pivotIndex + 1), width)
            val secondHalf = ArrayList<Int>(loop.size - pivotIndex + 1)
            for (i in pivotIndex until loop.size) secondHalf += loop[i]
            secondHalf += loop.first()
            val simplifiedSecond = simplifyOpenPath(secondHalf, width)
            return firstHalf + simplifiedSecond.drop(1)
        }
        return simplifyOpenPath(path, width)
    }

    /**
     * Iterative Ramer–Douglas–Peucker simplification. A 1-cell tolerance removes raster stair-steps
     * along nearly straight lines but retains meaningful corners and wider contour turns.
     */
    private fun simplifyOpenPath(path: List<Int>, width: Int): List<Int> {
        if (path.size <= 3) return path
        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.lastIndex] = true
        val pending = ArrayList<Pair<Int, Int>>()
        pending += 0 to path.lastIndex
        val toleranceSquared = SIMPLIFICATION_TOLERANCE * SIMPLIFICATION_TOLERANCE

        while (pending.isNotEmpty()) {
            val (firstIndex, lastIndex) = pending.removeAt(pending.lastIndex)
            if (lastIndex - firstIndex <= 1) continue

            var farthestIndex = -1
            var farthestDistanceSquared = toleranceSquared
            val first = path[firstIndex]
            val last = path[lastIndex]
            for (i in firstIndex + 1 until lastIndex) {
                val distanceSquared = distanceToSegmentSquared(path[i], first, last, width)
                if (distanceSquared > farthestDistanceSquared) {
                    farthestDistanceSquared = distanceSquared
                    farthestIndex = i
                }
            }
            if (farthestIndex >= 0) {
                keep[farthestIndex] = true
                pending += firstIndex to farthestIndex
                pending += farthestIndex to lastIndex
            }
        }
        return path.indices.filter { keep[it] }.map { path[it] }
    }

    private fun distanceToSegmentSquared(point: Int, start: Int, end: Int, width: Int): Double {
        val px = (point % width).toDouble()
        val py = (point / width).toDouble()
        val sx = (start % width).toDouble()
        val sy = (start / width).toDouble()
        val ex = (end % width).toDouble()
        val ey = (end / width).toDouble()
        val vx = ex - sx
        val vy = ey - sy
        val lengthSquared = vx * vx + vy * vy
        if (lengthSquared == 0.0) {
            val dx = px - sx
            val dy = py - sy
            return dx * dx + dy * dy
        }
        val projection = (((px - sx) * vx + (py - sy) * vy) / lengthSquared).coerceIn(0.0, 1.0)
        val nearestX = sx + projection * vx
        val nearestY = sy + projection * vy
        val dx = px - nearestX
        val dy = py - nearestY
        return dx * dx + dy * dy
    }

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

    private data class PreviewMap(
        val edgeMap: EdgeMap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val stepX: Int,
        val stepY: Int,
    )

    private fun mapAtPreviewResolution(source: EdgeMap): PreviewMap {
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
        return PreviewMap(
            edgeMap = EdgeMap(width, height, strength),
            sourceWidth = source.width,
            sourceHeight = source.height,
            stepX = stepX,
            stepY = stepY,
        )
    }

    private fun quantize(value: Double): Double =
        (value * 10_000.0).roundToInt().coerceIn(0, 10_000) / 10_000.0

    private companion object {
        const val SIMPLIFICATION_TOLERANCE = 1.0
    }
}
