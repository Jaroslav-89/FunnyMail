package com.jaroapps.funnymail.features.drawing.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke

private val DrawingPalette = listOf(
    "#202124",
    "#D93025",
    "#F29900",
    "#188038",
    "#1A73E8",
    "#A142F4",
)

@Composable
fun DrawingScreen(
    onBack: () -> Unit,
    onSaveDrawing: (String, (String?) -> Unit) -> Unit,
    onSendDrawing: (String, (String?) -> Unit) -> Unit,
) {
    var strokes by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var savedDrawingJson by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var activePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(DrawingPalette.first()) }
    var brushWidth by remember { mutableStateOf(10f) }
    var eraserEnabled by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val drawingCodec = remember { DrawingCodec() }
    val currentDrawingJson = remember(strokes) {
        if (strokes.isEmpty()) {
            null
        } else {
            drawingCodec.encode(
                DrawingDocument(
                    canvas = DrawingCanvas(
                        width = 1000,
                        height = 1000,
                        backgroundColor = "#FFFFFF",
                    ),
                    strokes = strokes,
                ),
            ).getOrNull()
        }
    }
    val isCurrentDrawingSaved = currentDrawingJson != null && currentDrawingJson == savedDrawingJson
    val paintColor = if (eraserEnabled) "#FFFFFF" else selectedColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Новый рисунок",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "Нарисуй что-нибудь от руки",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawingPalette.forEach { colorHex ->
                val chosen = !eraserEnabled && selectedColor == colorHex
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(if (chosen) 2.dp else 0.dp, CircleShape)
                        .clip(CircleShape)
                        .background(colorHex.toDrawingColor())
                        .border(
                            width = if (chosen) 3.dp else 1.dp,
                            color = if (chosen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable {
                            selectedColor = colorHex
                            eraserEnabled = false
                            statusMessage = null
                        },
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { eraserEnabled = !eraserEnabled; statusMessage = null },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
            ) {
                Text(if (eraserEnabled) "Кисть" else "Ластик")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Толщина линии", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("${brushWidth.toInt()}", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = brushWidth,
                onValueChange = {
                    brushWidth = it
                    statusMessage = null
                },
                valueRange = 3f..27f,
                steps = 23,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .pointerInput(paintColor, brushWidth) {
                    fun normalizedPoint(x: Float, y: Float): List<Double> {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        return listOf(
                            (x / width).coerceIn(0f, 1f).toDouble(),
                            (y / height).coerceIn(0f, 1f).toDouble(),
                        )
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            statusMessage = null
                            activePoints = listOf(normalizedPoint(offset.x, offset.y))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activePoints = activePoints + listOf(
                                normalizedPoint(
                                    change.position.x,
                                    change.position.y,
                                ),
                            )
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                strokes = strokes + DrawingStroke(
                                    color = paintColor,
                                    width = brushWidth,
                                    points = activePoints,
                                )
                            }
                            activePoints = emptyList()
                        },
                        onDragCancel = { activePoints = emptyList() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize().padding(1.dp)) {


                strokes.forEach { stroke -> drawDrawingStroke(stroke) }
                if (activePoints.isNotEmpty()) {
                    drawDrawingStroke(
                        DrawingStroke(
                            color = paintColor,
                            width = brushWidth,
                            points = activePoints,
                        ),
                    )
                }
            }

            if (strokes.isEmpty() && activePoints.isEmpty()) {
                Text(
                    text = "Начни рисовать здесь",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) strokes = strokes.dropLast(1)
                    statusMessage = null
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Отменить")
            }
            OutlinedButton(
                onClick = {
                    strokes = emptyList()
                    activePoints = emptyList()
                    statusMessage = null
                },
                enabled = strokes.isNotEmpty() || activePoints.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Очистить")
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                color = if (statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val json = currentDrawingJson
                    if (json == null) {
                        statusMessage = "Сначала нарисуй что-нибудь на холсте."
                        statusIsError = true
                    } else if (!isSaving && !isCurrentDrawingSaved) {
                        isSaving = true
                        onSaveDrawing(json) { saveError ->
                            isSaving = false
                            if (saveError == null) {
                                savedDrawingJson = json
                                statusMessage = "Рисунок сохранён в локальную галерею."
                                statusIsError = false
                            } else {
                                statusMessage = saveError
                                statusIsError = true
                            }
                        }
                    }
                },
                enabled = currentDrawingJson != null && !isSaving && !isCurrentDrawingSaved,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        isSaving -> "Сохраняем…"
                        isCurrentDrawingSaved -> "Сохранено"
                        else -> "Сохранить рисунок"
                    },
                )
            }
            Button(
                onClick = {
                    val json = currentDrawingJson
                    if (json == null) {
                        statusMessage = "Сначала нарисуй что-нибудь на холсте."
                        statusIsError = true
                    } else if (!isSending) {
                        isSending = true
                        onSendDrawing(json) { sendError ->
                            isSending = false
                            if (sendError == null) {
                                statusMessage = "Рисунок отправлен в чат."
                                statusIsError = false
                            } else {
                                statusMessage = sendError
                                statusIsError = true
                            }
                        }
                    }
                },
                enabled = currentDrawingJson != null && !isSending,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isSending) "Отправляем…" else "Отправить")
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrawingStroke(stroke: DrawingStroke) {
    val color = stroke.color.toDrawingColor()
    val lineWidth = (stroke.width * size.minDimension / 1000f)
        .coerceAtLeast(1f)
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = lineWidth / 2f,
            center = androidx.compose.ui.geometry.Offset(
                x = (point[0] * size.width).toFloat(),
                y = (point[1] * size.height).toFloat(),
            ),
        )
    } else {
        val path = Path()
        stroke.points.forEachIndexed { index, point ->
            val x = (point[0] * size.width).toFloat()
            val y = (point[1] * size.height).toFloat()
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

private fun String.toDrawingColor(): Color {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0x202124L
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}
