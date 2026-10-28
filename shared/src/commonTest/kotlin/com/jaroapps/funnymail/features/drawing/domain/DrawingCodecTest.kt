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
    fun reportsActualJsonSizeEvenWhenDrawingExceedsConfiguredLimit() {
        val smallCodec = DrawingCodec(maxJsonBytes = 32)
        val drawing = DrawingDocument(
            strokes = listOf(
                DrawingStroke(
                    points = listOf(listOf(0.1, 0.2), listOf(0.35, 0.45)),
                ),
            ),
        )

        val (encoded, sizeBytes) = smallCodec.encodeWithSize(drawing)

        assertFalse(encoded.isSuccess)
        assertTrue(sizeBytes > 32)
        assertEquals(sizeBytes, smallCodec.encodedSizeBytes(drawing))
    }

    @Test
    fun acceptsMorePointsThanThePreviousDetailLimit() {
        val codecWithRoomForPointBudget = DrawingCodec(maxJsonBytes = 768 * 1024)
        val drawing = DrawingDocument(
            strokes = listOf(
                DrawingStroke(
                    points = List(30_001) { listOf(0.1, 0.2) },
                ),
            ),
        )

        val encoded = codecWithRoomForPointBudget.encode(drawing)

        assertTrue(encoded.isSuccess)
        val (checkedResult, sizeBytes) = codecWithRoomForPointBudget.encodeWithSize(drawing)
        assertTrue(checkedResult.isSuccess)
        assertTrue(sizeBytes > 30_001)
        assertEquals(sizeBytes, encoded.getOrThrow().encodeToByteArray().size)
    }

    @Test
    fun rejectsJsonLargerThanConfiguredLimit() {
        val smallCodec = DrawingCodec(maxJsonBytes = 32)
        val drawing = DrawingDocument(
            strokes = listOf(DrawingStroke(points = listOf(listOf(0.1, 0.2)))),
        )

        assertFalse(smallCodec.encode(drawing).isSuccess)
    }
    @Test
    fun enforces250KiBDrawingJsonLimit() {
        val limitedCodec = DrawingCodec(maxJsonBytes = 250 * 1024)
        val drawing = DrawingDocument(
            strokes = listOf(
                DrawingStroke(points = List(30_000) { listOf(0.1, 0.2) }),
            ),
        )
        assertFalse(limitedCodec.encode(drawing).isSuccess)
    }

    @Test
    fun validatesCaptionLengthLimits() {
        assertFalse(codec.encode(DrawingDocument(title = "x".repeat(21))).isSuccess)
        assertFalse(codec.encode(DrawingDocument(caption = "x".repeat(81))).isSuccess)
        assertTrue(codec.encode(DrawingDocument(title = "A".repeat(20), caption = "B".repeat(80))).isSuccess)
    }

    @Test
    fun validatesStrokeAndBackgroundOpacity() {
        val invalidStroke = DrawingDocument(
            strokes = listOf(
                DrawingStroke(alpha = 1.1f, points = listOf(listOf(0.1, 0.2))),
            ),
        )
        assertFalse(codec.encode(invalidStroke).isSuccess)
        assertFalse(codec.encode(DrawingDocument(canvas = DrawingCanvas(backgroundAlpha = -0.1f))).isSuccess)
        assertTrue(codec.encode(DrawingDocument(canvas = DrawingCanvas(backgroundAlpha = 0.5f))).isSuccess)
    }

}
