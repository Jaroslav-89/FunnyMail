package com.jaroapps.funnymail.features.letter.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingStrokes

/**
 * Read-only full-screen view of a drawing sent to a chat.
 */
@Composable
fun DrawingViewerScreen(
    drawing: DrawingDocument,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDrawingStrokes(
                strokes = drawing.strokes,
                canvasWidth = drawing.canvas.width,
                canvasHeight = drawing.canvas.height,
                contentInsetFraction = 0.02f,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(48.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE8EAED), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(23.dp)) {
                val stroke = Stroke(
                    width = size.minDimension * 0.095f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val arrow = Path().apply {
                    moveTo(size.width * 0.78f, size.height * 0.50f)
                    lineTo(size.width * 0.22f, size.height * 0.50f)
                    moveTo(size.width * 0.45f, size.height * 0.23f)
                    lineTo(size.width * 0.18f, size.height * 0.50f)
                    lineTo(size.width * 0.45f, size.height * 0.77f)
                }
                drawPath(arrow, Color(0xFF202124), style = stroke)
            }
        }
    }
}
