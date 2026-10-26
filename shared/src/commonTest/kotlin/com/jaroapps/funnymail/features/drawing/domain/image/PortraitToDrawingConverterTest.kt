package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortraitToDrawingConverterTest {
    @Test
    fun preservesRawMaskComponentsAndAllFaceLandmarkPoints() {
        val width = 100
        val height = 140
        val mask = ByteArray(width * height)
        for (y in 12..128) for (x in 32..78) {
            mask[y * width + x] = 255.toByte()
        }
        // This tiny component is model output too; raw conversion must not discard it.
        for (y in 4..6) for (x in 4..6) {
            mask[y * width + x] = 255.toByte()
        }
        val facialPoints = listOf(
            NormalizedPhotoPoint(0.43123, 0.31234),
            NormalizedPhotoPoint(0.46321, 0.29123),
            NormalizedPhotoPoint(0.51765, 0.29321),
            NormalizedPhotoPoint(0.55432, 0.31456),
            NormalizedPhotoPoint(0.51234, 0.34678),
            NormalizedPhotoPoint(0.46789, 0.34987),
        )
        val features = PortraitFeatures(
            sourceWidth = width,
            sourceHeight = height,
            maskWidth = width,
            maskHeight = height,
            personMask = mask,
            faceContours = listOf(
                PortraitFeatureContour(points = facialPoints, closed = true),
            ),
        )

        val drawing = PortraitToDrawingConverter().convert(features)
        val outline = drawing.strokes.first()

        assertTrue(
            drawing.strokes.size >= 3,
            "The main mask, the small mask component, and the face contour should all survive",
        )
        assertTrue(outline.points.size > 200, "Raw silhouette boundary points should not be simplified")
        assertTrue(drawing.strokes.all { it.width == 2f }, "Fine model contours should use thin lines")
        assertEquals(
            facialPoints.size + 1,
            drawing.strokes.last().points.size,
            "Closed face landmarks should retain every model point and close the path",
        )
        assertTrue(
            drawing.strokes.last().points.first()[0] == facialPoints.first().x &&
                drawing.strokes.last().points.first()[1] == facialPoints.first().y,
            "Landmark coordinates must be copied without quantization",
        )
        assertTrue(
            drawing.strokes.sumOf { it.points.size } <= 5_000,
            "The serialized point budget must remain bounded",
        )
        assertTrue(drawing.strokes.flatMap { it.points }.all { point ->
            point.size == 2 && point.all { it in 0.0..1.0 }
        })
        assertTrue(
            drawing.strokes.flatMap { it.points }.any { point ->
                (point[0] * 10_000.0).roundToInt() / 10_000.0 != point[0] ||
                    (point[1] * 10_000.0).roundToInt() / 10_000.0 != point[1]
            },
            "Coordinates should not be rounded to four decimal places",
        )
        val json = DrawingCodec().encode(drawing).getOrThrow()
        assertTrue(json.encodeToByteArray().size <= 256 * 1024)
    }
}
