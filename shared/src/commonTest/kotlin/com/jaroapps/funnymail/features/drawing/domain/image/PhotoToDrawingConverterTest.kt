package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoToDrawingConverterTest {
    @Test
    fun suppressesFineTextureAndKeepsMainSilhouetteWithBolderLines() {
        val width = 160
        val height = 120
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            // Fine background texture should be smoothed away.
            val texture = if (((x / 2) + (y / 2)) % 2 == 0) 224 else 244
            // A large, high-contrast object should remain recognizable.
            if (x in 34..126 && y in 22..98) 24 else texture
        }

        val drawing = PhotoToDrawingConverter().convert(GrayImage(width, height, pixels))
        val pointCount = drawing.strokes.sumOf { it.points.size }
        val json = DrawingCodec().encode(drawing).getOrThrow()

        assertTrue(drawing.strokes.isNotEmpty(), "The main object contour should survive")
        assertTrue(drawing.strokes.size < 8, "Version 8 should discard substantially more small contours")
        assertTrue(pointCount > 4, "The main silhouette should retain enough points")
        assertTrue(drawing.strokes.all { it.width >= 14f }, "Version 8 should keep bold lines")
        assertTrue(pointCount <= 750, "Version 8 should keep the point count compact")
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
        assertTrue(drawing.strokes.all { stroke -> stroke.points.size >= 2 })
        assertTrue(drawing.strokes.flatMap { it.points }.all { point ->
            point.size == 2 && point.all { it in 0.0..1.0 }
        })
        val encoded = DrawingCodec().encode(drawing)
        assertTrue(encoded.isSuccess, encoded.exceptionOrNull()?.message)
        assertTrue(encoded.getOrThrow().encodeToByteArray().size <= 256 * 1024)
    }
}
