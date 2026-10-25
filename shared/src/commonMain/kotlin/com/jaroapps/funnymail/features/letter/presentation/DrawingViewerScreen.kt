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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.core.ui.AppBackButton
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingStrokes
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingBackground

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
        drawDrawingBackground(drawing.canvas)
            drawDrawingStrokes(
                strokes = drawing.strokes,
                canvasWidth = drawing.canvas.width,
                canvasHeight = drawing.canvas.height,
                contentInsetFraction = 0.02f,
            )
        }

        AppBackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            description = "Назад в чат",
            keepLightAppearance = true,
        )
    }
}
