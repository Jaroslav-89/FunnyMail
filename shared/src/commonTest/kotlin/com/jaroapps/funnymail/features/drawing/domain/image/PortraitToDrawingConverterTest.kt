package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortraitToDrawingConverterTest {
    @Test
    fun keepsLargestPersonSilhouetteAndNamedFaceContoursButDropsTinyMaskIsland() {
        val width = 100
        val height = 140
        val mask = ByteArray(width * height)
        for (y in 12..128) for (x in 32..78) {
            mask[y * width + x] = 255.toByte()
        }
        // An unrelated small region should not become a separate drawing stroke.
        for (y in 4..6) for (x in 4..6) {
            mask[y * width + x] = 255.toByte()
        }
        val features = PortraitFeatures(
            sourceWidth = width,
            sourceHeight = height,
            maskWidth = width,
            maskHeight = height,
            personMask = mask,
            faceContours = listOf(
                PortraitFeatureContour(
                    points = listOf(
                        NormalizedPhotoPoint(0.43, 0.31),
                        NormalizedPhotoPoint(0.46, 0.29),
                        NormalizedPhotoPoint(0.51, 0.29),
                        NormalizedPhotoPoint(0.55, 0.31),
                        NormalizedPhotoPoint(0.51, 0.34),
                        NormalizedPhotoPoint(0.46, 0.34),
                    ),
                    strokeWidth = 7f,
                    closed = true,
                ),
            ),
        )

        val drawing = PortraitToDrawingConverter().convert(features)
        val outline = drawing.strokes.first()
        val outlinePoints = outline.points
        val xValues = outlinePoints.map { it[0] }

        assertTrue(drawing.strokes.size in 2..3, "The person outline and face contour should survive")
        assertEquals(5f, outline.width, "V2 post-processing uses a consistent 5px stroke width")
        assertTrue(xValues.minOrNull()!! > 0.20, "The outline should follow the dominant person mask")
        assertTrue(xValues.maxOrNull()!! < 0.90)
        assertTrue(drawing.strokes.all { it.width == 5f }, "Imported model contours should use V2 line width")
        assertTrue(
            drawing.strokes.sumOf { it.points.size } <= 7_000,
            "V2 point budget must remain bounded",
        )
        assertTrue(drawing.strokes.flatMap { it.points }.all { point ->
            point.size == 2 && point.all { it in 0.0..1.0 }
        })
        val json = DrawingCodec().encode(drawing).getOrThrow()
        assertTrue(json.encodeToByteArray().size <= 256 * 1024)
    }
}
