package com.jaroapps.funnymail.features.letter.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
import org.jetbrains.compose.resources.stringResource
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingStrokes
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingBackground
import com.jaroapps.funnymail.resources.back_to_chat
import com.jaroapps.funnymail.resources.Res

/**
 * Read-only full-screen view of a drawing sent to a chat.
 */
@Composable
fun DrawingViewerScreen(
    drawing: DrawingDocument,
    onBack: () -> Unit,
) {
    val backToChatDescription = stringResource(Res.string.back_to_chat)
    val repository = koinInject<LocalDrawingRepository>()
    val codec = remember { DrawingCodec() }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
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
            description = backToChatDescription,
            keepLightAppearance = true,
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            saveMessage?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFF202124),
                    modifier = Modifier.background(Color.White.copy(alpha = 0.92f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Button(
                enabled = !isSaving,
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        saveMessage = null
                        scope.launch {
                            val result = codec.encode(drawing).fold(
                                onSuccess = { encoded -> runCatching { repository.saveDrawing(encoded) } },
                                onFailure = { error -> Result.failure(error) },
                            )
                            isSaving = false
                            saveMessage = if (result.isSuccess) "Сохранено в галерею" else "Не удалось сохранить рисунок"
                        }
                    }
                },
            ) {
                Text(if (isSaving) "Сохранение…" else "Сохранить в галерею")
            }
        }
    }
}
