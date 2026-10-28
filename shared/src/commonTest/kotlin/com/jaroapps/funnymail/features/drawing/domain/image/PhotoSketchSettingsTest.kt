package com.jaroapps.funnymail.features.drawing.domain.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoSketchSettingsTest {
    @Test
    fun configurableLineThicknessIsAppliedToGeneratedStrokes() {
        val width = 48
        val height = 20
        val edges = ByteArray(width * height)
        for (x in 5..35) edges[10 * width + x] = 220.toByte()

        val document = EdgeMapVectorizer(
            settings = PhotoSketchSettings(
                strokeWidth = 4.5f,
                isolatedPointRemovalPercent = 0,
            ),
        ).convert(EdgeMap(width, height, edges))

        assertTrue(document.strokes.isNotEmpty())
        assertTrue(document.strokes.all { it.width == 4.5f })
    }

    @Test
    fun pointBudgetKeepsStrongestDotsWhenSeparatePointsCannotBeSimplified() {
        val width = 180
        val height = 20
        val edges = ByteArray(width * height)
        // Forty isolated cells with different confidence; no line simplification can reduce them.
        for (i in 0 until 40) {
            edges[10 * width + (3 + i * 4)] = ((i + 1) * 255 / 40).toByte()
        }

        val document = EdgeMapVectorizer(
            settings = PhotoSketchSettings(
                maxPoints = 10,
                mergePointsIntoLines = false,
                simplificationTolerance = 0.0,
                isolatedPointRemovalPercent = 0,
            ),
        ).convert(EdgeMap(width, height, edges))

        assertEquals(10, document.strokes.sumOf { it.points.size })
        assertEquals(10, document.strokes.size)
        assertTrue(document.strokes.all { it.points.size == 1 })
        assertTrue(
            document.strokes.any { it.color == "#000000" },
            "The strongest dots should be kept when the point budget is tight",
        )
    }

    @Test
    fun minimumEdgeStrengthRemovesWeakDetections() {
        val width = 48
        val height = 20
        val edges = ByteArray(width * height)
        edges[5 * width + 5] = 40
        edges[10 * width + 20] = 120
        edges[15 * width + 40] = 240

        val document = EdgeMapVectorizer(
            settings = PhotoSketchSettings(
                minimumEdgeStrength = 100,
                maxPoints = 100,
                isolatedPointRemovalPercent = 0,
            ),
        ).convert(EdgeMap(width, height, edges))

        assertEquals(2, document.strokes.size)
    }

    @Test
    fun togglingPointMergingChangesPolylineOutput() {
        val width = 48
        val height = 20
        val edges = ByteArray(width * height)
        for (x in 5..25) edges[10 * width + x] = 255.toByte()
        val map = EdgeMap(width, height, edges)

        val connected = EdgeMapVectorizer(
            settings = PhotoSketchSettings(
                mergePointsIntoLines = true,
                isolatedPointRemovalPercent = 0,
            ),
        ).convert(map)
        val separate = EdgeMapVectorizer(
            settings = PhotoSketchSettings(
                mergePointsIntoLines = false,
                isolatedPointRemovalPercent = 0,
            ),
        ).convert(map)

        assertTrue(connected.strokes.any { it.points.size > 1 })
        assertTrue(separate.strokes.all { it.points.size == 1 })
        assertTrue(separate.strokes.size > connected.strokes.size)
    }
}
