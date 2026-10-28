package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.roundToInt

class PhotoToSketchConverterTest {
    @Test
    fun detectsEdgesAroundObjectsInAnOrdinaryGrayscaleImage() {
        val width = 96
        val height = 80
        val pixels = IntArray(width * height) { 235 }

        // A high-contrast object with a smaller inner object, like a window on a building facade.
        for (y in 14..65) for (x in 16..78) {
            pixels[y * width + x] = 150
        }
        for (y in 24..38) for (x in 28..49) {
            pixels[y * width + x] = 25
        }
        // A narrow foreground object to exercise lines separate from the main component.
        for (y in 22..70) for (x in 65..68) {
            pixels[y * width + x] = 55
        }

        val map = PhotoEdgeDetector().detect(GrayImage(width, height, pixels))
        val edgeCount = map.edgeCount()

        assertTrue(edgeCount > 80, "Object boundaries should create a useful edge map")
        assertTrue(edgeCount < width * height / 3, "The edge map should remain sparse rather than filling the photo")
    }

    @Test
    fun vectorizesAnEdgeMapIntoEditableCompactDrawingStrokes() {
        val width = 96
        val height = 80
        val edges = ByteArray(width * height)

        // Two long edges and a short internal contour simulate a facade and a window.
        for (x in 10..82) edges[20 * width + x] = 220.toByte()
        for (x in 10..82) edges[55 * width + x] = 170.toByte()
        for (y in 20..55) edges[y * width + 10] = 210.toByte()
        for (y in 20..55) edges[y * width + 82] = 210.toByte()
        for (x in 25..47) edges[32 * width + x] = 140.toByte()

        val document = EdgeMapVectorizer().convert(EdgeMap(width, height, edges))

        assertTrue(document.strokes.size >= 2, "The closed facade contour and inner edge should remain separate strokes")
        assertTrue(document.strokes.all { stroke ->
            stroke.points.size >= 2 &&
                stroke.width == 2.6f &&
                stroke.points.all { point ->
                    point.size == 2 && point.all { it in 0.0..1.0 }
                }
        })
        val encoded = DrawingCodec().encode(document).getOrThrow()
        assertTrue(encoded.encodeToByteArray().size <= 768 * 1024)
    }

    @Test
    fun vectorizedDrawingKeepsTheEdgeMapLightToDarkContrast() {
        val width = 9
        val height = 7
        val edges = ByteArray(width * height)

        // Two separated contours with different confidence values.
        for (x in 1..5) edges[1 * width + x] = 40
        for (x in 1..5) edges[5 * width + x] = 255.toByte()

        val document = EdgeMapVectorizer().convert(EdgeMap(width, height, edges))

        assertTrue(document.strokes.any { it.color == "#000000" },
            "Strong map contours should remain black")
        assertTrue(document.strokes.any { it.color == "#A1A1A1" },
            "Weak map contours should remain light gray like the map preview")
        assertTrue(document.strokes.all { it.width == 2.6f },
            "Map-derived contour strokes should use thicker than the previous display thickness")
    }

    @Test
    fun preservesShortContoursAndIsolatedCellsVisibleOnTheMap() {
        val width = 640
        val height = 480
        val edges = ByteArray(width * height)

        // The preview groups source pixels into cells 2 pixels wide and keeps the maximum
        // edge confidence in each cell. Build a three-cell short contour and a separate dot.
        edges[15 * width + 10] = 40
        edges[15 * width + 11] = 255.toByte()
        edges[15 * width + 12] = 55
        edges[15 * width + 14] = 70
        edges[15 * width + 15] = 220.toByte()
        edges[200 * width + 100] = 90

        val document = EdgeMapVectorizer().convert(EdgeMap(width, height, edges))
        val points = document.strokes.flatMap { it.points }.toSet()

        fun q(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0
        fun hasPoint(sourceCellX: Int, sourceCellY: Int, cellWidth: Int = 2): Boolean {
            val expectedX = q((sourceCellX + cellWidth / 2.0) / width)
            val expectedY = q((sourceCellY + 0.5) / height)
            return listOf(expectedX, expectedY) in points
        }

        // Three neighbouring display cells form a short contour. None may be length-filtered.
        assertTrue(hasPoint(10, 15), "The first visible map cell should be retained")
        assertTrue(hasPoint(12, 15), "The middle visible map cell should be retained")
        assertTrue(hasPoint(14, 15), "The last visible map cell should be retained")
        // A point with no neighbours is visible on the map too and must become a dot stroke.
        assertTrue(hasPoint(100, 200, cellWidth = 2),
            "An isolated detected cell should be retained")
        assertTrue(document.strokes.any { it.points.size == 1 },
            "Isolated map cells must be represented as one-point strokes")
        assertTrue(document.canvas.width == 1_000 && document.canvas.height == 750,
            "Different preview grid steps must not distort the source aspect ratio")
        DrawingCodec().encode(document).getOrThrow()
    }

    @Test
    fun simplifiesLongStraightContoursIntoCompactThickStrokes() {
        val width = 160
        val height = 16
        val edges = ByteArray(width * height)
        for (x in 10..130) edges[8 * width + x] = 255.toByte()

        val document = EdgeMapVectorizer().convert(EdgeMap(width, height, edges))
        val stroke = document.strokes.maxByOrNull { it.points.size }
            ?: error("Expected the long edge to become a stroke")

        assertTrue(
            stroke.points.size <= 3,
            "A straight edge should keep only its endpoints after path simplification",
        )
        assertTrue(stroke.width == 2.6f, "The simplified edge should use a thicker stroke")
        assertTrue(document.strokes.sumOf { it.points.size } < 121 / 4,
            "Long straight paths should use substantially fewer points than raw edge cells")
    }

    @Test
    fun removesOnlyTheWeakestFifthOfIsolatedEdgeCells() {
        val width = 100
        val height = 20
        val edges = ByteArray(width * height)
        for (i in 0 until 10) {
            edges[10 * width + (5 + i * 10)] = ((i + 1) * 10).toByte()
        }

        val document = EdgeMapVectorizer().convert(EdgeMap(width, height, edges))

        assertTrue(document.strokes.size == 8,
            "Only the two weakest of ten isolated edge cells should be removed")
        assertTrue(document.strokes.all { it.points.size == 1 },
            "Remaining isolated detections should still be represented as individual points")
    }

    @Test
    fun flatPhotoFailsCleanlyInsteadOfCreatingNoise() {
        val width = 32
        val height = 32
        val flat = GrayImage(width, height, IntArray(width * height) { 180 })
        val result = runCatching { PhotoEdgeDetector().detect(flat) }

        assertTrue(result.exceptionOrNull() is NoContoursFoundException)
    }
}
