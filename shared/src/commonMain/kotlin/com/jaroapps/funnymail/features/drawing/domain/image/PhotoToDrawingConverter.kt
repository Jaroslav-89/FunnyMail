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
 * Version 7 adds an explicit geometric-length and span filter after contour joining. Short
 * marks and tiny loops are discarded before vector simplification; surviving contours are
 * simplified aggressively and drawn with thicker strokes.
 */
class PhotoToDrawingConverter(
    private val maxStrokes: Int = 45,
    private val maxPoints: Int = 1_000,
) {
    private data class Candidate(val points: List<Int>, val sourceLength: Int)

    private class MutableContour(var points: MutableList<Int>) {
        var active: Boolean = true
    }

    private data class EndpointRef(val contour: MutableContour, val atStart: Boolean)

    private data class Connection(
        val currentAtStart: Boolean,
        val target: MutableContour,
        val targetAtStart: Boolean,
        val score: Double,
    )

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

        // Version 6 keeps only dominant edges. Fine facial and clothing texture is more likely
        // to be discarded unless it creates a strong, sustained gradient after large-radius blur.
        val high = (maximumMagnitude * 0.31f).coerceIn(24f, 210f).coerceAtMost(maximumMagnitude)
        val rawEdges = hysteresis(thinned, width, height, high * 0.78f, high)
        val edges = closeSmallGaps(rawEdges, width, height)
        val maxGap = (min(width, height) * 0.045).roundToInt().coerceIn(14, 40)
        val minContourLength = (min(width, height) * 0.09).roundToInt().coerceIn(32, 72)
        // Explicit geometric filtering: long pixel paths are not necessarily long strokes
        // (a noisy loop can contain many pixels in a tiny area), so check both path length
        // and bounding-box span after nearby fragments have been joined.
        val minStrokeLength = (min(width, height) * 0.14).coerceIn(36.0, 68.0)
        val minStrokeSpan = (min(width, height) * 0.045).coerceIn(12.0, 24.0)
        val mergedContours = joinNearbyContours(
            paths = traceEdges(edges, width, height).filter { it.size >= 4 },
            width = width,
            maxGap = maxGap,
        )
        val candidates = mergedContours
            .asSequence()
            .filter { it.size >= minContourLength }
            .filter { polylineLength(it, width) >= minStrokeLength }
            .filter { contourSpan(it, width) >= minStrokeSpan }
            .map { Candidate(simplify(it, width, epsilon = 10.0), it.size) }
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
                // Version 7 emphasizes the remaining long contours with much heavier strokes.
                width = 14f,
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

    private fun polylineLength(path: List<Int>, width: Int): Double {
        var length = 0.0
        for (index in 1 until path.size) {
            val previous = path[index - 1]
            val current = path[index]
            val dx = (current % width - previous % width).toDouble()
            val dy = (current / width - previous / width).toDouble()
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }

    private fun contourSpan(path: List<Int>, width: Int): Double {
        if (path.isEmpty()) return 0.0
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (point in path) {
            val x = point % width
            val y = point / width
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        val dx = (maxX - minX).toDouble()
        val dy = (maxY - minY).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    private fun quantizeCoordinate(value: Double): Double =
        (value * 10_000.0).roundToInt().coerceIn(0, 10_000) / 10_000.0

    private fun boxBlur(source: IntArray, width: Int, height: Int): IntArray {
        // Separable 17x17 Gaussian-like blur. The 1D weights sum to 256; applying one pass
        // horizontally and one vertically is equivalent to the full 2D kernel at lower cost.
        val weights = intArrayOf(1, 2, 4, 8, 13, 20, 28, 34, 36, 34, 28, 20, 13, 8, 4, 2, 1)
        val horizontal = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            for (offset in -8..8) {
                val sampleX = (x + offset).coerceIn(0, width - 1)
                sum += source[y * width + sampleX] * weights[offset + 8]
            }
            horizontal[y * width + x] = sum
        }

        val result = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            for (offset in -8..8) {
                val sampleY = (y + offset).coerceIn(0, height - 1)
                sum += horizontal[sampleY * width + x] * weights[offset + 8]
            }
            result[y * width + x] = sum / 65_536
        }
        return result
    }

    /**
     * Greedily stitches nearby contour endpoints when their local directions are compatible.
     * The spatial hash avoids comparing every endpoint with every other endpoint. A joined
     * contour is indexed only after all possible attachments are made, and old entries are
     * lazily ignored through MutableContour.active.
     */
    private fun joinNearbyContours(
        paths: List<List<Int>>,
        width: Int,
        maxGap: Int,
    ): List<List<Int>> {
        if (paths.size < 2) return paths
        val bucketSize = maxGap.coerceAtLeast(1)
        val buckets = HashMap<Long, MutableList<EndpointRef>>()
        val orderedPaths = paths.sortedByDescending { it.size }
        val gapSquared = maxGap * maxGap

        fun bucketKey(x: Int, y: Int): Long {
            val cellX = x / bucketSize
            val cellY = y / bucketSize
            return (cellX.toLong() shl 32) xor (cellY.toLong() and 0xffffffffL)
        }

        fun endpoint(contour: MutableContour, atStart: Boolean): Int =
            if (atStart) contour.points.first() else contour.points.last()

        fun inwardVector(points: List<Int>, atStart: Boolean): Pair<Double, Double> {
            val edge = endpointIndex(points, atStart)
            val inner = if (atStart) points[1] else points[points.lastIndex - 1]
            val ex = (edge % width).toDouble()
            val ey = (edge / width).toDouble()
            return ((inner % width).toDouble() - ex) to ((inner / width).toDouble() - ey)
        }

        fun findConnection(points: List<Int>): Connection? {
            var best: Connection? = null
            for (currentAtStart in listOf(true, false)) {
                val point = if (currentAtStart) points.first() else points.last()
                val x = point % width
                val y = point / width
                val (vx, vy) = inwardVector(points, currentAtStart)
                val currentLength = sqrt(vx * vx + vy * vy).coerceAtLeast(1.0)

                for (cellY in y / bucketSize - 1..y / bucketSize + 1) {
                    for (cellX in x / bucketSize - 1..x / bucketSize + 1) {
                        val refs = buckets[(cellX.toLong() shl 32) xor (cellY.toLong() and 0xffffffffL)]
                            ?: continue
                        for (ref in refs) {
                            val target = ref.contour
                            if (!target.active || target.points.size < 2) continue
                            val other = endpoint(target, ref.atStart)
                            val dx = other % width - x
                            val dy = other / width - y
                            val distanceSquared = dx * dx + dy * dy
                            if (distanceSquared > gapSquared) continue

                            val (tx, ty) = inwardVector(target.points, ref.atStart)
                            val targetLength = sqrt(tx * tx + ty * ty).coerceAtLeast(1.0)
                            val alignment = (vx * tx + vy * ty) / (currentLength * targetLength)
                            // Inward tangent vectors should face roughly away from one another
                            // at the gap, which is what a continuous line's endpoints do.
                            if (alignment > 0.35) continue

                            val score = distanceSquared.toDouble() +
                                (alignment + 1.0) * gapSquared * 0.12
                            if (best == null || score < best.score) {
                                best = Connection(currentAtStart, target, ref.atStart, score)
                            }
                        }
                    }
                }
            }
            return best
        }

        val output = ArrayList<MutableContour>()
        for (path in orderedPaths) {
            if (path.size < 2) continue
            val current = MutableContour(path.toMutableList())
            var joins = 0
            while (joins++ < 24) {
                val connection = findConnection(current.points) ?: break
                val first = if (connection.currentAtStart) current.points.asReversed() else current.points
                val targetPoints = connection.target.points
                val second = if (connection.targetAtStart) targetPoints else targetPoints.asReversed()

                val combined = ArrayList<Int>(first.size + second.size)
                combined.addAll(first)
                if (first.last() == second.first()) {
                    combined.addAll(second.drop(1))
                } else {
                    combined.addAll(second)
                }
                current.points = combined
                connection.target.active = false
            }

            val finished = current
            output += finished
            for (atStart in listOf(true, false)) {
                val point = endpoint(finished, atStart)
                val x = point % width
                val y = point / width
                val key = bucketKey(x, y)
                buckets.getOrPut(key) { ArrayList() }.add(EndpointRef(finished, atStart))
            }
        }
        return output.filter { it.active }.map { it.points }
    }

    private fun endpointIndex(points: List<Int>, atStart: Boolean): Int =
        if (atStart) points.first() else points.last()

    /**
     * 5x5 morphological closing repairs slightly larger breaks in contour paths. The dilation
     * bridges narrow gaps; erosion then returns the mask to approximately its original thickness.
     */
    private fun closeSmallGaps(edges: BooleanArray, width: Int, height: Int): BooleanArray {
        val radius = 2
        val dilated = BooleanArray(edges.size)
        for (y in radius until (height - radius)) for (x in radius until (width - radius)) {
            var found = false
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (edges[(y + dy) * width + x + dx]) found = true
            }
            dilated[y * width + x] = found
        }

        val closed = BooleanArray(edges.size)
        for (y in radius until (height - radius)) for (x in radius until (width - radius)) {
            var filled = true
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (!dilated[(y + dy) * width + x + dx]) filled = false
            }
            closed[y * width + x] = filled
        }
        return closed
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
