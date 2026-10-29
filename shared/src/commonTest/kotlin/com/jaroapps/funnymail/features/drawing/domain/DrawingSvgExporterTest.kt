package com.jaroapps.funnymail.features.drawing.domain

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingSticker
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingText
import kotlin.test.Test
import kotlin.test.assertTrue

class DrawingSvgExporterTest {
    @Test
    fun exportsVectorStrokesEscapedRichTextAndEmoji() {
        val drawing = DrawingDocument(
            strokes = listOf(
                DrawingStroke(
                    color = "#102030",
                    width = 4f,
                    alpha = 0.5f,
                    points = listOf(listOf(0.1, 0.2), listOf(0.8, 0.7)),
                ),
            ),
            texts = listOf(
                DrawingText(
                    text = "<hello & goodbye>",
                    x = 0.2,
                    y = 0.4,
                    bold = true,
                    italic = true,
                    underline = true,
                    rotationDegrees = 25f,
                ),
            ),
            stickers = listOf(DrawingSticker(code = ":smile:", x = 0.6, y = 0.7, scale = 1.5f)),
        )

        val svg = DrawingSvgExporter.export(drawing)

        assertTrue(svg.startsWith("<?xml version=\"1.0\""))
        assertTrue(svg.contains("<path d=\"M "))
        assertTrue(svg.contains("stroke-opacity=\"0.5\""))
        assertTrue(svg.contains("&lt;hello &amp; goodbye&gt;"))
        assertTrue(svg.contains("font-weight=\"bold\""))
        assertTrue(svg.contains("font-style=\"italic\""))
        assertTrue(svg.contains("text-decoration=\"underline\""))
        assertTrue(svg.contains("😊"))
    }

    @Test
    fun exportsGradientBackgroundAndOpacity() {
        val drawing = DrawingDocument(
            canvas = DrawingCanvas(
                backgroundMode = "GRADIENT",
                gradientDirection = "LEFT_TO_RIGHT",
                gradientStartColor = "#000000",
                gradientEndColor = "#FFFFFF",
                backgroundAlpha = 0.6f,
            ),
        )

        val svg = DrawingSvgExporter.export(drawing)

        assertTrue(svg.contains("<linearGradient"))
        assertTrue(svg.contains("fill=\"url(#drawingGradient)\""))
        assertTrue(svg.contains("fill-opacity=\"0.6\""))
    }
}
