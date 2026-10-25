package com.jaroapps.funnymail.features.drawing.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val BrushWidths = listOf(3f, 7f, 12f, 19f, 27f)
private val BrushDotSizes = listOf(6.dp, 9.dp, 13.dp, 17.dp, 22.dp)

private enum class DrawingPopup {
    Colors,
    Width,
}

private enum class DrawingToolIcon {
    Back,
    Undo,
    Redo,
    Check,
}

@Composable
fun DrawingScreen(
    onBack: () -> Unit,
    onSaveDrawing: (String, (String?) -> Unit) -> Unit,
    onSendDrawing: (String, (String?) -> Unit) -> Unit,
) {
    var strokes by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var redoStrokes by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var savedDrawingJson by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var activePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(DrawingPalette.first()) }
    var brushWidth by remember { mutableStateOf(12f) }
    var eraserEnabled by remember { mutableStateOf(false) }
    var activePopup by remember { mutableStateOf<DrawingPopup?>(null) }
    var showFinishDialog by remember { mutableStateOf(false) }
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

    val saveDrawingAction: () -> Unit = {
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
    }

    val sendDrawingAction: () -> Unit = {
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(paintColor, brushWidth) {
                    fun normalizedPoint(x: Float, y: Float): List<Double> {
                        val canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                        val canvasHeight = size.height.toFloat().coerceAtLeast(1f)
                        return listOf(
                            (x / canvasWidth).coerceIn(0f, 1f).toDouble(),
                            (y / canvasHeight).coerceIn(0f, 1f).toDouble(),
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
                                normalizedPoint(change.position.x, change.position.y),
                            )
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                strokes = strokes + DrawingStroke(
                                    color = paintColor,
                                    width = brushWidth,
                                    points = activePoints,
                                )
                                redoStrokes = emptyList()
                            }
                            activePoints = emptyList()
                        },
                        onDragCancel = { activePoints = emptyList() },
                    )
                },
        ) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundToolButton(onClick = onBack) {
                DrawingToolGlyph(
                    icon = DrawingToolIcon.Back,
                    color = Color(0xFF202124),
                    modifier = Modifier.size(23.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundToolButton(
                    enabled = strokes.isNotEmpty(),
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            redoStrokes = redoStrokes + strokes.last()
                            strokes = strokes.dropLast(1)
                            statusMessage = null
                        }
                    },
                ) {
                    DrawingToolGlyph(
                        icon = DrawingToolIcon.Undo,
                        color = if (strokes.isNotEmpty()) Color(0xFF202124) else Color(0xFFB8BBC0),
                        modifier = Modifier.size(23.dp),
                    )
                }

                RoundToolButton(
                    enabled = redoStrokes.isNotEmpty(),
                    onClick = {
                        if (redoStrokes.isNotEmpty()) {
                            strokes = strokes + redoStrokes.last()
                            redoStrokes = redoStrokes.dropLast(1)
                            statusMessage = null
                        }
                    },
                ) {
                    DrawingToolGlyph(
                        icon = DrawingToolIcon.Redo,
                        color = if (redoStrokes.isNotEmpty()) Color(0xFF202124) else Color(0xFFB8BBC0),
                        modifier = Modifier.size(23.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            RoundToolButton(
                onClick = {
                    if (currentDrawingJson == null) {
                        statusMessage = "Сначала нарисуй что-нибудь на холсте."
                        statusIsError = true
                    } else {
                        showFinishDialog = true
                    }
                },
                background = Color(0xFF188038),
            ) {
                DrawingToolGlyph(
                    icon = DrawingToolIcon.Check,
                    color = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
        }

        statusMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp, start = 18.dp, end = 18.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = message,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else Color(0xFF188038),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AnimatedVisibility(
                visible = activePopup == DrawingPopup.Colors,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Row(
                    modifier = Modifier
                        .shadow(5.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(28.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawingPalette.forEach { colorHex ->
                        val selected = !eraserEnabled && selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colorHex.toDrawingColor())
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color(0xFF202124) else Color(0xFFDADCE0),
                                    shape = CircleShape,
                                )
                                .clickable {
                                    selectedColor = colorHex
                                    eraserEnabled = false
                                    activePopup = null
                                    statusMessage = null
                                },
                        )
                    }

                    TextButton(
                        onClick = {
                            eraserEnabled = true
                            activePopup = null
                            statusMessage = null
                        },
                        modifier = Modifier
                            .height(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(
                                width = if (eraserEnabled) 2.dp else 1.dp,
                                color = if (eraserEnabled) Color(0xFF202124) else Color(0xFFDADCE0),
                                shape = CircleShape,
                            ),
                    ) {
                        Text("Ластик", color = Color(0xFF202124), fontSize = 12.sp)
                    }
                }
            }

            AnimatedVisibility(
                visible = activePopup == DrawingPopup.Width,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Row(
                    modifier = Modifier
                        .shadow(5.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(28.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BrushWidths.forEachIndexed { index, width ->
                        val selected = brushWidth == width
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    brushWidth = width
                                    activePopup = null
                                    statusMessage = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(BrushDotSizes[index] + 8.dp)
                                        .border(2.dp, Color(0xFF1A73E8), CircleShape),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(BrushDotSizes[index])
                                    .clip(CircleShape)
                                    .background(Color(0xFF202124)),
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorToolButton(
                    selectedColor = selectedColor,
                    eraserEnabled = eraserEnabled,
                    onClick = {
                        activePopup = if (activePopup == DrawingPopup.Colors) null else DrawingPopup.Colors
                    },
                )

                RoundToolButton(
                    onClick = {
                        activePopup = if (activePopup == DrawingPopup.Width) null else DrawingPopup.Width
                    },
                ) {
                    val selectedWidthIndex = BrushWidths.indexOf(brushWidth).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .size(BrushDotSizes[selectedWidthIndex])
                            .clip(CircleShape)
                            .background(Color(0xFF202124)),
                    )
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Готово с рисунком") },
            text = { Text("Сохрани рисунок в локальную галерею или отправь его в этот чат.") },
            confirmButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(
                        onClick = {
                            showFinishDialog = false
                            saveDrawingAction()
                        },
                        enabled = !isSaving && !isCurrentDrawingSaved,
                    ) {
                        Text(if (isSaving) "Сохраняем…" else "Сохранить в галерею")
                    }
                    Button(
                        onClick = {
                            showFinishDialog = false
                            sendDrawingAction()
                        },
                        enabled = !isSending,
                    ) {
                        Text(if (isSending) "Отправляем…" else "Отправить в чат")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Продолжить рисовать")
                }
            },
        )
    }
}

@Composable
private fun RoundToolButton(
    enabled: Boolean = true,
    background: Color = Color.White,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Color(0xFFE8EAED), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ColorToolButton(
    selectedColor: String,
    eraserEnabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = if (eraserEnabled) RoundedCornerShape(24.dp) else CircleShape
    Box(
        modifier = Modifier
            .width(if (eraserEnabled) 76.dp else 48.dp)
            .height(48.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(if (eraserEnabled) Color.White else selectedColor.toDrawingColor())
            .border(
                width = 1.dp,
                color = if (eraserEnabled) Color(0xFF202124) else Color(0xFFE8EAED),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (eraserEnabled) {
            Text("Ластик", color = Color(0xFF202124), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrawingToolGlyph(
    icon: DrawingToolIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val lineWidth = size.minDimension * 0.095f
        val stroke = Stroke(
            width = lineWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (icon) {
            DrawingToolIcon.Back -> {
                val arrow = Path().apply {
                    moveTo(size.width * 0.78f, size.height * 0.50f)
                    lineTo(size.width * 0.22f, size.height * 0.50f)
                    moveTo(size.width * 0.45f, size.height * 0.23f)
                    lineTo(size.width * 0.18f, size.height * 0.50f)
                    lineTo(size.width * 0.45f, size.height * 0.77f)
                }
                drawPath(arrow, color, style = stroke)
            }

            DrawingToolIcon.Undo -> {
                val arrow = Path().apply {
                    moveTo(size.width * 0.76f, size.height * 0.34f)
                    lineTo(size.width * 0.46f, size.height * 0.34f)
                    cubicTo(
                        size.width * 0.27f, size.height * 0.34f,
                        size.width * 0.22f, size.height * 0.47f,
                        size.width * 0.22f, size.height * 0.61f,
                    )
                    moveTo(size.width * 0.08f, size.height * 0.47f)
                    lineTo(size.width * 0.22f, size.height * 0.63f)
                    lineTo(size.width * 0.38f, size.height * 0.47f)
                }
                drawPath(arrow, color, style = stroke)
            }

            DrawingToolIcon.Redo -> {
                val arrow = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.34f)
                    lineTo(size.width * 0.54f, size.height * 0.34f)
                    cubicTo(
                        size.width * 0.73f, size.height * 0.34f,
                        size.width * 0.78f, size.height * 0.47f,
                        size.width * 0.78f, size.height * 0.61f,
                    )
                    moveTo(size.width * 0.62f, size.height * 0.47f)
                    lineTo(size.width * 0.78f, size.height * 0.63f)
                    lineTo(size.width * 0.92f, size.height * 0.47f)
                }
                drawPath(arrow, color, style = stroke)
            }

            DrawingToolIcon.Check -> {
                val check = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.74f)
                    lineTo(size.width * 0.83f, size.height * 0.27f)
                }
                drawPath(check, color, style = stroke)
            }
        }
    }
}

private fun DrawScope.drawDrawingStroke(stroke: DrawingStroke) {
    val color = stroke.color.toDrawingColor()
    val lineWidth = (stroke.width * size.minDimension / 1000f)
        .coerceAtLeast(1f)
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = lineWidth / 2f,
            center = Offset(
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
