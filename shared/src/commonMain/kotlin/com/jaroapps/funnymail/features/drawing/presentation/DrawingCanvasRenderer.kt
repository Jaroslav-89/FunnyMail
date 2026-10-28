package com.jaroapps.funnymail.features.drawing.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.min

/**
 * Renders normalized drawing coordinates inside a viewport without changing their aspect ratio.
 * The document's canvas dimensions describe the coordinate system used when the drawing was made.
 */
internal fun DrawScope.drawDrawingStrokes(
    strokes: List<DrawingStroke>,
    canvasWidth: Int,
    canvasHeight: Int,
    contentInsetFraction: Float = 0f,
) {
    val logicalWidth = canvasWidth.coerceAtLeast(1).toFloat()
    val logicalHeight = canvasHeight.coerceAtLeast(1).toFloat()

    // Preview surfaces clip to rounded corners, and strokes may touch the canvas edges.
    // Reserve an inner margin so the artwork's top/bottom and edge strokes remain visible.
    val inset = size.minDimension * contentInsetFraction.coerceIn(0f, 0.2f)
    val availableWidth = (size.width - inset * 2f).coerceAtLeast(0f)
    val availableHeight = (size.height - inset * 2f).coerceAtLeast(0f)
    val scale = min(availableWidth / logicalWidth, availableHeight / logicalHeight)
    if (!scale.isFinite() || scale <= 0f) return

    val contentWidth = logicalWidth * scale
    val contentHeight = logicalHeight * scale
    val offsetX = inset + (availableWidth - contentWidth) / 2f
    val offsetY = inset + (availableHeight - contentHeight) / 2f

    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val color = stroke.color.toDrawingRenderColor().copy(alpha = stroke.alpha.coerceIn(0f, 1f))
        // Photo-derived map strokes use widths 1..2 as screen-pixel weights. Scaling them by
        // the normalized canvas scale would round both values down to the same one-pixel line,
        // making the requested thickness change invisible on a phone-sized canvas.
        val lineWidth = if (stroke.width <= 2f) {
            stroke.width.coerceAtLeast(1f)
        } else {
            (stroke.width * scale).coerceAtLeast(1f)
        }

        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            drawCircle(
                color = color,
                radius = lineWidth / 2f,
                center = Offset(
                    x = offsetX + (point[0] * contentWidth).toFloat(),
                    y = offsetY + (point[1] * contentHeight).toFloat(),
                ),
            )
        } else {
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = offsetX + (point[0] * contentWidth).toFloat()
                val y = offsetY + (point[1] * contentHeight).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = lineWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

private fun String.toDrawingRenderColor(): androidx.compose.ui.graphics.Color {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0x202124L
    return androidx.compose.ui.graphics.Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}
