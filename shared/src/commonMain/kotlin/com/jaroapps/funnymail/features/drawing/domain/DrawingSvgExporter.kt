package com.jaroapps.funnymail.features.drawing.domain

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument

/** Builds a self-contained SVG representation of a drawing for cross-app sharing. */
object DrawingSvgExporter {
    fun export(drawing: DrawingDocument): String {
        val width = drawing.canvas.width.coerceAtLeast(1)
        val height = drawing.canvas.height.coerceAtLeast(1)
        val gradient = if (drawing.canvas.backgroundMode == "GRADIENT") {
            val direction = when (drawing.canvas.gradientDirection) {
                "LEFT_TO_RIGHT" -> listOf("0%", "50%", "100%", "50%")
                "TOP_LEFT_TO_BOTTOM_RIGHT" -> listOf("0%", "0%", "100%", "100%")
                "TOP_RIGHT_TO_BOTTOM_LEFT" -> listOf("100%", "0%", "0%", "100%")
                else -> listOf("50%", "0%", "50%", "100%")
            }
            "<defs><linearGradient id=\"drawingGradient\" x1=\"" + direction[0] +
                "\" y1=\"" + direction[1] + "\" x2=\"" + direction[2] +
                "\" y2=\"" + direction[3] + "\"><stop offset=\"0%\" stop-color=\"" +
                drawing.canvas.gradientStartColor + "\"/><stop offset=\"100%\" stop-color=\"" +
                drawing.canvas.gradientEndColor + "\"/></linearGradient></defs>"
        } else ""
        val background = if (drawing.canvas.backgroundMode == "GRADIENT") {
            "url(#drawingGradient)"
        } else drawing.canvas.backgroundColor
        val body = StringBuilder()
        drawing.strokes.forEach { stroke ->
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                body.append("<circle cx=\"" + (point[0] * width) + "\" cy=\"" +
                    (point[1] * height) + "\" r=\"" + (stroke.width / 2f) +
                    "\" fill=\"" + stroke.color + "\" opacity=\"" +
                    stroke.alpha.coerceIn(0f, 1f) + "\"/>")
            } else if (stroke.points.isNotEmpty()) {
                val path = stroke.points.mapIndexed { index, point ->
                    (if (index == 0) "M " else "L ") + (point[0] * width) + " " + (point[1] * height)
                }.joinToString(" ")
                body.append("<path d=\"" + path + "\" fill=\"none\" stroke=\"" +
                    stroke.color + "\" stroke-width=\"" + stroke.width +
                    "\" stroke-opacity=\"" + stroke.alpha.coerceIn(0f, 1f) +
                    "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
            }
        }
        drawing.texts.forEach { item ->
            val styles = buildString {
                if (item.bold) append(" font-weight=\"bold\"")
                if (item.italic) append(" font-style=\"italic\"")
                if (item.underline) append(" text-decoration=\"underline\"")
            }
            val x = item.x * width
            val y = item.y * height
            body.append("<text x=\"" + x + "\" y=\"" + y +
                "\" font-family=\"sans-serif\" font-size=\"" + item.fontSize +
                "\" fill=\"" + item.color + "\" opacity=\"" + item.alpha.coerceIn(0f, 1f) +
                "\"" + styles + " transform=\"rotate(" + item.rotationDegrees + " " + x + " " + y +
                ")\">" + item.text.svgEscape() + "</text>")
        }
        drawing.stickers.forEach { item ->
            val emoji = when (item.code) {
                ":smile:" -> "😊"
                ":heart:" -> "❤️"
                ":wink:" -> "😉"
                ":laugh:" -> "😂"
                ":sad:" -> "🙁"
                ":cool:" -> "😎"
                else -> "😊"
            }
            val x = item.x * width
            val y = item.y * height
            body.append("<text x=\"" + x + "\" y=\"" + y +
                "\" font-family=\"sans-serif\" font-size=\"" + (72f * item.scale) +
                "\" transform=\"rotate(" + item.rotationDegrees + " " + x + " " + y +
                ")\">" + emoji.svgEscape() + "</text>")
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" +
            width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height + "\">" +
            gradient + "<rect width=\"100%\" height=\"100%\" fill=\"" + background +
            "\" fill-opacity=\"" + drawing.canvas.backgroundAlpha.coerceIn(0f, 1f) +
            "\"/>" + body + "</svg>"
    }
}

private fun String.svgEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

fun DrawingDocument.toSvg(): String = DrawingSvgExporter.export(this)
