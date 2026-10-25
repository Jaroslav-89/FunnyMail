package com.jaroapps.funnymail.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp

@Composable
fun PaperPlusIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.085f
        val paper = Path().apply {
            moveTo(size.width * 0.23f, size.height * 0.10f)
            lineTo(size.width * 0.60f, size.height * 0.10f)
            lineTo(size.width * 0.77f, size.height * 0.27f)
            lineTo(size.width * 0.77f, size.height * 0.48f)
            moveTo(size.width * 0.60f, size.height * 0.10f)
            lineTo(size.width * 0.60f, size.height * 0.27f)
            lineTo(size.width * 0.77f, size.height * 0.27f)
            moveTo(size.width * 0.23f, size.height * 0.10f)
            lineTo(size.width * 0.23f, size.height * 0.88f)
            lineTo(size.width * 0.53f, size.height * 0.88f)
        }
        drawPath(
            path = paper,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.68f, size.height * 0.61f),
            end = Offset(size.width * 0.68f, size.height * 0.94f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.515f, size.height * 0.775f),
            end = Offset(size.width * 0.845f, size.height * 0.775f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun DrawingListIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.085f
        val dotRadius = size.minDimension * 0.055f
        val rowYs = listOf(0.22f, 0.50f, 0.78f)
        rowYs.forEach { y ->
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(size.width * 0.18f, size.height * y),
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.34f, size.height * y),
                end = Offset(size.width * 0.86f, size.height * y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun TrashCanIcon(
    modifier: Modifier = Modifier.size(22.dp),
    color: Color,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val bin = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.32f)
            lineTo(size.width * 0.75f, size.height * 0.32f)
            lineTo(size.width * 0.70f, size.height * 0.88f)
            lineTo(size.width * 0.30f, size.height * 0.88f)
            close()
            moveTo(size.width * 0.16f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.22f)
            moveTo(size.width * 0.39f, size.height * 0.22f)
            lineTo(size.width * 0.42f, size.height * 0.12f)
            lineTo(size.width * 0.58f, size.height * 0.12f)
            lineTo(size.width * 0.61f, size.height * 0.22f)
        }
        drawPath(
            path = bin,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.45f, size.height * 0.43f),
            end = Offset(size.width * 0.45f, size.height * 0.75f),
            strokeWidth = strokeWidth * 0.75f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.56f, size.height * 0.43f),
            end = Offset(size.width * 0.56f, size.height * 0.75f),
            strokeWidth = strokeWidth * 0.75f,
            cap = StrokeCap.Round,
        )
    }
}
