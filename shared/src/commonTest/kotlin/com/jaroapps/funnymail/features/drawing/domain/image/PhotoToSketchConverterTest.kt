package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.test.Test
import kotlin.test.assertTrue

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
                stroke.width == 2.0f &&
                stroke.points.all { point ->
                    point.size == 2 && point.all { it in 0.0..1.0 }
                }
        })
        val encoded = DrawingCodec().encode(document).getOrThrow()
        assertTrue(encoded.encodeToByteArray().size <= 256 * 1024)
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
        assertTrue(document.strokes.all { it.width == 1.0f },
            "Map-derived contour strokes should use twice the previous display thickness")
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
