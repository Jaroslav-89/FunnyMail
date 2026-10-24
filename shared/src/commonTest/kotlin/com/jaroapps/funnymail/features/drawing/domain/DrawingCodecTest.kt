package com.jaroapps.funnymail.features.drawing.domain

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawingCodecTest {
    private val codec = DrawingCodec()

    @Test
    fun validDrawingRoundTripsAsJsonPointPairs() {
        val drawing = DrawingDocument(
            canvas = DrawingCanvas(backgroundColor = "#FFFFFF"),
            strokes = listOf(
                DrawingStroke(
                    color = "#000000",
                    width = 5f,
                    points = listOf(listOf(0.1, 0.2), listOf(0.35, 0.45)),
                ),
            ),
        )

        val encoded = codec.encode(drawing)

        assertTrue(encoded.isSuccess)
        val json = encoded.getOrThrow()
        assertTrue(json.contains("\"backgroundColor\":\"#FFFFFF\""))
        assertTrue(json.contains("[0.1,0.2]"))
        assertEquals(drawing, codec.decode(json).getOrThrow())
    }

    @Test
    fun rejectsCoordinatesOutsideNormalizedRange() {
        val drawing = DrawingDocument(
            strokes = listOf(
                DrawingStroke(
                    points = listOf(listOf(1.2, 0.5)),
                ),
            ),
        )

        assertFalse(codec.encode(drawing).isSuccess)
    }

    @Test
    fun rejectsUnsupportedFormatVersion() {
        assertFalse(codec.encode(DrawingDocument(version = 2)).isSuccess)
    }

    @Test
    fun rejectsJsonLargerThanConfiguredLimit() {
        val smallCodec = DrawingCodec(maxJsonBytes = 32)
        val drawing = DrawingDocument(
            strokes = listOf(DrawingStroke(points = listOf(listOf(0.1, 0.2)))),
        )

        assertFalse(smallCodec.encode(drawing).isSuccess)
    }
}
