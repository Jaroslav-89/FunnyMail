package com.jaroapps.funnymail.features.letter.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke

@Composable
fun LettersScreen(
    draftJson: String?,
    onBack: () -> Unit,
) {
    val draft = remember(draftJson) {
        draftJson?.let { DrawingCodec().decode(it).getOrNull() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Мои письма",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (draft == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Пока пусто", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Создай рисунок с главного экрана. Постоянное хранение и отправка писем " +
                            "появятся после подключения локального хранилища и Firestore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else {
            Text("Последний черновик", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${draft.strokes.size} штрихов · ${draft.strokes.sumOf { it.points.size }} точек",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DrawingPreview(
                drawing = draft,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = "Черновик доступен только в текущем сеансе приложения. Он ещё не " +
                        "отправлен и будет потерян после закрытия приложения.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DrawingPreview(
    drawing: DrawingDocument,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Canvas(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        drawing.strokes.forEach { stroke ->
            drawPreviewStroke(stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewStroke(
    stroke: DrawingStroke,
) {
    if (stroke.points.isEmpty()) return

    val lineColor = stroke.color.toPreviewColor()
    val lineWidth = (stroke.width * size.minDimension / 1000f).coerceAtLeast(1f)

    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = lineColor,
            radius = lineWidth / 2f,
            center = androidx.compose.ui.geometry.Offset(
                x = (point[0] * size.width).toFloat(),
                y = (point[1] * size.height).toFloat(),
            ),
        )
        return
    }

    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = (point[0] * size.width).toFloat()
        val y = (point[1] * size.height).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun String.toPreviewColor(): Color {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0x202124L
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}
