package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoToDrawingConverterTest {
    @Test
    fun preservesMoreContoursForDetailedImagesWithVisibleStrokeWidth() {
        val width = 160
        val height = 120
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (((x / 10) + (y / 10)) % 2 == 0) 18 else 238
        }

        val drawing = PhotoToDrawingConverter().convert(GrayImage(width, height, pixels))
        val pointCount = drawing.strokes.sumOf { it.points.size }
        val json = DrawingCodec().encode(drawing).getOrThrow()

        assertTrue(drawing.strokes.size > 10, "Expected multiple connected contours")
        assertTrue(pointCount > 100, "Expected detail to survive vector simplification")
        assertTrue(drawing.strokes.all { it.width >= 4f }, "Imported lines should remain visible")
        assertTrue(json.encodeToByteArray().size <= 256 * 1024, "Keep the existing JSON size budget")
    }

    @Test
    fun convertsHighContrastShapeToNormalizedStrokesWithinJsonLimit() {
        val width = 128
        val height = 96
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x in 28..98 && y in 18..76) 20 else 245
        }

        val drawing = PhotoToDrawingConverter().convert(GrayImage(width, height, pixels))

        assertTrue(drawing.strokes.isNotEmpty())
        assertEquals(1_000, drawing.canvas.width)
        assertEquals(750, drawing.canvas.height)
        assertTrue(drawing.strokes.all { stroke -> stroke.points.size >= 3 })
        assertTrue(drawing.strokes.flatMap { it.points }.all { point ->
            point.size == 2 && point.all { it in 0.0..1.0 }
        })
        val encoded = DrawingCodec().encode(drawing)
        assertTrue(encoded.isSuccess, encoded.exceptionOrNull()?.message)
        assertTrue(encoded.getOrThrow().encodeToByteArray().size <= 256 * 1024)
    }
}
