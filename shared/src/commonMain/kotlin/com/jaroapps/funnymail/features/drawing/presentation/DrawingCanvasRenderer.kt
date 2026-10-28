package com.jaroapps.funnymail.features.drawing.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
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


/**
 * Shared annotation layer used by the editor, gallery previews and full-screen viewer.
 * All saved positions are normalized in the same aspect-preserving coordinate space as strokes.
 */
@Composable
fun DrawingAnnotationsOverlay(
    drawing: DrawingDocument,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val logicalWidth = drawing.canvas.width.coerceAtLeast(1)
        val logicalHeight = drawing.canvas.height.coerceAtLeast(1)
        val scale = minOf(
            maxWidth.value / logicalWidth.toFloat(),
            maxHeight.value / logicalHeight.toFloat(),
        ).takeIf { it.isFinite() && it > 0f } ?: return@BoxWithConstraints
        val contentWidth = (logicalWidth * scale).dp
        val contentHeight = (logicalHeight * scale).dp
        val offsetX = (maxWidth - contentWidth) / 2
        val offsetY = (maxHeight - contentHeight) / 2

        drawing.texts.forEach { annotation ->
            Text(
                text = annotation.text,
                modifier = Modifier
                    .offset(
                        x = offsetX + (annotation.x * logicalWidth * scale).dp,
                        y = offsetY + (annotation.y * logicalHeight * scale).dp,
                    )
                    .androidxComposeRotate(annotation.rotationDegrees),
                style = TextStyle(
                    color = annotation.color.toDrawingRenderColor().copy(alpha = annotation.alpha.coerceIn(0f, 1f)),
                    fontSize = (annotation.fontSize * scale).sp,
                    fontWeight = if (annotation.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (annotation.underline) TextDecoration.Underline else TextDecoration.None,
                ),
            )
        }
        drawing.stickers.forEach { sticker ->
            Text(
                text = when (sticker.code) {
                    ":smile:" -> "😊"
                    ":heart:" -> "❤️"
                    ":wink:" -> "😉"
                    ":laugh:" -> "😂"
                    ":sad:" -> "🙁"
                    ":cool:" -> "😎"
                    else -> "😊"
                },
                modifier = Modifier
                    .offset(
                        x = offsetX + (sticker.x * logicalWidth * scale).dp,
                        y = offsetY + (sticker.y * logicalHeight * scale).dp,
                    )
                    .androidxComposeRotate(sticker.rotationDegrees),
                fontSize = (72f * scale * sticker.scale).sp,
                maxLines = 1,
            )
        }
    }
}

private fun Modifier.androidxComposeRotate(degrees: Float): Modifier =
    androidx.compose.ui.draw.rotate(degrees)
