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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaroapps.funnymail.core.ui.AppBackButton
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    initialDrawingJson: String? = null,
) {
    val drawingCodec = remember { DrawingCodec() }
    val initialDrawing = remember(initialDrawingJson) {
        initialDrawingJson?.let { drawingCodec.decode(it).getOrNull() }
    }
    var strokes by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.strokes ?: emptyList())
    }
    var redoStrokes by remember(initialDrawingJson) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var savedDrawingJson by remember(initialDrawingJson) { mutableStateOf(initialDrawingJson) }
    var isSaving by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var activePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var selectedColor by remember(initialDrawingJson) { mutableStateOf("#000000") }
    var brushWidth by remember { mutableStateOf(12f) }
    var eraserEnabled by remember { mutableStateOf(false) }
    var activePopup by remember { mutableStateOf<DrawingPopup?>(null) }
    var backgroundMode by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.backgroundMode ?: "SOLID")
    }
    var backgroundColor by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.backgroundColor ?: "#FFFFFF")
    }
    var gradientDirection by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.gradientDirection ?: "TOP_TO_BOTTOM")
    }
    var gradientStartColor by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.gradientStartColor ?: "#00AA00")
    }
    var gradientEndColor by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.gradientEndColor ?: "#F5A800")
    }
    var showLineColorDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var backgroundPickerTab by remember { mutableStateOf("SOLID") }
    var pendingBackgroundColor by remember { mutableStateOf("#FFFFFF") }
    var pendingGradientDirection by remember { mutableStateOf("TOP_TO_BOTTOM") }
    var pendingGradientStartColor by remember { mutableStateOf("#00AA00") }
    var pendingGradientEndColor by remember { mutableStateOf("#F5A800") }
    var pendingGradientEndpoint by remember { mutableStateOf(0) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasWidth by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.width ?: 1000)
    }
    var canvasHeight by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.height ?: 1000)
    }
    var canvasAspectInitialized by remember(initialDrawingJson) {
        mutableStateOf(initialDrawing != null)
    }
    var isDrawingGestureActive by remember { mutableStateOf(false) }
    val paintColor = if (eraserEnabled) {
        if (backgroundMode == "GRADIENT") gradientStartColor else backgroundColor
    } else {
        selectedColor
    }
    val currentDrawingJson = remember(
        strokes,
        canvasWidth,
        canvasHeight,
        backgroundMode,
        backgroundColor,
        gradientDirection,
        gradientStartColor,
        gradientEndColor,
    ) {
        if (strokes.isEmpty()) {
            null
        } else {
            drawingCodec.encode(
                DrawingDocument(
                    canvas = DrawingCanvas(
                        width = canvasWidth,
                        height = canvasHeight,
                        backgroundColor = backgroundColor,
                        backgroundMode = backgroundMode,
                        gradientDirection = gradientDirection,
                        gradientStartColor = gradientStartColor,
                        gradientEndColor = gradientEndColor,
                    ),
                    strokes = strokes,
                ),
            ).getOrNull()
        }
    }
    val isCurrentDrawingSaved = currentDrawingJson != null && currentDrawingJson == savedDrawingJson

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
                    statusMessage = if (initialDrawingJson != null) {
                        "Изменения сохранены в галерее."
                    } else {
                        "Рисунок сохранён в локальную галерею."
                    }
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
                .onSizeChanged { newSize ->
                    canvasSize = newSize
                    if (!canvasAspectInitialized && newSize.width > 0 && newSize.height > 0) {
                        canvasHeight = (
                            canvasWidth.toFloat() * newSize.height / newSize.width
                        ).roundToInt().coerceIn(1, 4096)
                        canvasAspectInitialized = true
                    }
                }
                .pointerInput(
                    paintColor,
                    brushWidth,
                    canvasSize.width,
                    canvasSize.height,
                    canvasWidth,
                    canvasHeight,
                ) {
                    val viewportScale = min(
                        size.width.toFloat() / canvasWidth.toFloat(),
                        size.height.toFloat() / canvasHeight.toFloat(),
                    ).coerceAtLeast(0.0001f)
                    val viewportWidth = canvasWidth.toFloat() * viewportScale
                    val viewportHeight = canvasHeight.toFloat() * viewportScale
                    val viewportLeft = (size.width.toFloat() - viewportWidth) / 2f
                    val viewportTop = (size.height.toFloat() - viewportHeight) / 2f

                    fun isInsideViewport(x: Float, y: Float): Boolean =
                        x >= viewportLeft &&
                            x <= viewportLeft + viewportWidth &&
                            y >= viewportTop &&
                            y <= viewportTop + viewportHeight

                    fun normalizedPoint(x: Float, y: Float): List<Double> = listOf(
                        ((x - viewportLeft) / viewportWidth).coerceIn(0f, 1f).toDouble(),
                        ((y - viewportTop) / viewportHeight).coerceIn(0f, 1f).toDouble(),
                    )

                    detectDragGestures(
                        onDragStart = { offset ->
                            statusMessage = null
                            isDrawingGestureActive = isInsideViewport(offset.x, offset.y)
                            activePoints = if (isDrawingGestureActive) {
                                listOf(normalizedPoint(offset.x, offset.y))
                            } else {
                                emptyList()
                            }
                        },
                        onDrag = { change, _ ->
                            if (isDrawingGestureActive) {
                                change.consume()
                                activePoints = activePoints + listOf(
                                    normalizedPoint(change.position.x, change.position.y),
                                )
                            }
                        },
                        onDragEnd = {
                            if (isDrawingGestureActive && activePoints.isNotEmpty()) {
                                strokes = strokes + DrawingStroke(
                                    color = paintColor,
                                    width = brushWidth,
                                    points = activePoints,
                                )
                                redoStrokes = emptyList()
                            }
                            isDrawingGestureActive = false
                            activePoints = emptyList()
                        },
                        onDragCancel = {
                            isDrawingGestureActive = false
                            activePoints = emptyList()
                        },
                    )
                },
        ) {
            drawDrawingBackground(
                DrawingCanvas(
                    width = canvasWidth,
                    height = canvasHeight,
                    backgroundColor = backgroundColor,
                    backgroundMode = backgroundMode,
                    gradientDirection = gradientDirection,
                    gradientStartColor = gradientStartColor,
                    gradientEndColor = gradientEndColor,
                ),
            )
            val visibleStrokes = if (activePoints.isEmpty()) {
                strokes
            } else {
                strokes + DrawingStroke(
                    color = paintColor,
                    width = brushWidth,
                    points = activePoints,
                )
            }
            drawDrawingStrokes(
                strokes = visibleStrokes,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
            )
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
            AppBackButton(onClick = onBack, keepLightAppearance = true)

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
                background = Color(0xFFF5A800),
            ) {
                DrawingToolGlyph(
                    icon = DrawingToolIcon.Check,
                    color = Color(0xFF202124),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            RoundToolButton(
                onClick = {
                    activePopup = null
                    showLineColorDialog = true
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(CircleShape)
                        .background(selectedColor.toDrawingColor())
                        .border(1.dp, Color(0xFFE8EAED), CircleShape),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
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

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(10.dp))
                    .clickable {
                        backgroundPickerTab = backgroundMode
                        pendingBackgroundColor = backgroundColor
                        pendingGradientDirection = gradientDirection
                        pendingGradientStartColor = gradientStartColor
                        pendingGradientEndColor = gradientEndColor
                        pendingGradientEndpoint = 0
                        showBackgroundDialog = true
                        activePopup = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (backgroundMode == "GRADIENT") {
                                Brush.linearGradient(
                                    listOf(gradientStartColor.toDrawingColor(), gradientEndColor.toDrawingColor()),
                                )
                            } else {
                                SolidColor(backgroundColor.toDrawingColor())
                            },
                        )
                        .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(5.dp)),
                )
            }
        }
    }

    if (showLineColorDialog) {
        AlertDialog(
            onDismissRequest = { showLineColorDialog = false },
            title = { Text("Цвет линии") },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                ColorWheel(
                    selectedColor = selectedColor,
                    onColorSelected = {
                        selectedColor = it
                        eraserEnabled = false
                        statusMessage = null
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showLineColorDialog = false }) { Text("Готово", color = Color(0xFF202124)) }
            },
        )
    }

    if (showBackgroundDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundDialog = false },
            title = { Text("Фон рисунка") },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("SOLID" to "Однотонный", "GRADIENT" to "Градиент").forEach { (tab, label) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (backgroundPickerTab == tab) Color(0xFFF5A800)
                                        else Color(0xFFF5F5F5),
                                    )
                                    .clickable { backgroundPickerTab = tab }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (backgroundPickerTab == tab) Color(0xFF202124)
                                    else Color(0xFF202124),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    if (backgroundPickerTab == "SOLID") {
                        ColorWheel(
                            selectedColor = pendingBackgroundColor,
                            onColorSelected = { pendingBackgroundColor = it },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                "LEFT_TO_RIGHT" to "→",
                                "TOP_TO_BOTTOM" to "↓",
                                "TOP_LEFT_TO_BOTTOM_RIGHT" to "↘",
                                "TOP_RIGHT_TO_BOTTOM_LEFT" to "↙",
                            ).forEach { (direction, glyph) ->
                                Box(
                                    modifier = Modifier
                                        .size(39.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (pendingGradientDirection == direction) Color(0xFFF5A800)
                                            else Color(0xFFF5F5F5),
                                        )
                                        .border(
                                            1.dp,
                                            if (pendingGradientDirection == direction) Color(0xFFF5A800)
                                            else Color(0xFFDADCE0),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { pendingGradientDirection = direction },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        glyph,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pendingGradientDirection == direction) Color(0xFF202124)
                                        else Color(0xFF202124),
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(pendingGradientStartColor.toDrawingColor())
                                    .border(
                                        if (pendingGradientEndpoint == 0) 3.dp else 1.dp,
                                        if (pendingGradientEndpoint == 0) Color(0xFF202124)
                                        else Color(0xFFDADCE0),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { pendingGradientEndpoint = 0 },
                            )
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(pendingGradientEndColor.toDrawingColor())
                                    .border(
                                        if (pendingGradientEndpoint == 1) 3.dp else 1.dp,
                                        if (pendingGradientEndpoint == 1) Color(0xFF202124)
                                        else Color(0xFFDADCE0),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { pendingGradientEndpoint = 1 },
                            )
                        }
                        ColorWheel(
                            selectedColor = if (pendingGradientEndpoint == 0) {
                                pendingGradientStartColor
                            } else {
                                pendingGradientEndColor
                            },
                            onColorSelected = { color ->
                                if (pendingGradientEndpoint == 0) pendingGradientStartColor = color
                                else pendingGradientEndColor = color
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backgroundMode = backgroundPickerTab
                        backgroundColor = pendingBackgroundColor
                        gradientDirection = pendingGradientDirection
                        gradientStartColor = pendingGradientStartColor
                        gradientEndColor = pendingGradientEndColor
                        showBackgroundDialog = false
                        statusMessage = null
                    },
                ) { Text("Готово", color = Color(0xFF202124)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDialog = false }) { Text("Отмена", color = Color(0xFF202124)) }
            },
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Готово с рисунком") },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
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
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF202124)),
                    ) {
                        Text(
                            when {
                                isSaving -> "Сохраняем…"
                                isCurrentDrawingSaved && initialDrawingJson != null -> "Изменения сохранены"
                                initialDrawingJson != null -> "Сохранить изменения"
                                else -> "Сохранить в галерею"
                            },
                        )
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

private fun String.toDrawingColor(): Color {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0x202124L
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}

@Composable
private fun ColorWheel(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .size(238.dp)
            .pointerInput(onColorSelected) {
                detectTapGestures { point ->
                    onColorSelected(colorAtWheelPoint(point.x, point.y, size.width.toFloat(), size.height.toFloat()))
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
                center = center,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White,
                0.30f to Color.White,
                0.65f to Color.Transparent,
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.65f to Color.Transparent,
                1f to Color.Black,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        val (hue, saturation, value) = selectedColor.toWheelHsv()
        val normalizedRadius = if (value < 0.999f) {
            0.65f + (1f - value) * 0.35f
        } else {
            saturation * 0.65f
        }
        val angle = (hue * PI.toFloat() / 180f)
        val selectedPoint = Offset(
            x = center.x + cos(angle) * radius * normalizedRadius,
            y = center.y + sin(angle) * radius * normalizedRadius,
        )
        drawCircle(Color.White, radius = 8.dp.toPx(), center = selectedPoint)
        drawCircle(Color.Black, radius = 5.dp.toPx(), center = selectedPoint)
    }
}

private fun colorAtWheelPoint(x: Float, y: Float, width: Float, height: Float): String {
    val centerX = width / 2f
    val centerY = height / 2f
    val dx = x - centerX
    val dy = y - centerY
    val maximumRadius = min(width, height) / 2f
    val normalizedRadius = (sqrt(dx * dx + dy * dy) / maximumRadius).coerceIn(0f, 1f)
    val saturation = if (normalizedRadius <= 0.65f) {
        (normalizedRadius / 0.65f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val value = if (normalizedRadius <= 0.65f) {
        1f
    } else {
        ((1f - normalizedRadius) / 0.35f).coerceIn(0f, 1f)
    }
    val hue = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI) + 360.0) % 360.0
    val sector = hue / 60.0
    val chroma = value.toDouble() * saturation.toDouble()
    val xComponent = chroma * (1.0 - abs((sector % 2.0) - 1.0))
    val components = when {
        sector < 1.0 -> Triple(chroma, xComponent, 0.0)
        sector < 2.0 -> Triple(xComponent, chroma, 0.0)
        sector < 3.0 -> Triple(0.0, chroma, xComponent)
        sector < 4.0 -> Triple(0.0, xComponent, chroma)
        sector < 5.0 -> Triple(xComponent, 0.0, chroma)
        else -> Triple(chroma, 0.0, xComponent)
    }
    val minimum = value.toDouble() - chroma
    fun toHex(value: Double): String = (value.coerceIn(0.0, 1.0) * 255.0)
        .toInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    return "#" + toHex(components.first + minimum) +
        toHex(components.second + minimum) + toHex(components.third + minimum)
}

private fun String.toWheelHsv(): Triple<Float, Float, Float> {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0L
    val red = ((rgb shr 16) and 0xFF).toFloat() / 255f
    val green = ((rgb shr 8) and 0xFF).toFloat() / 255f
    val blue = (rgb and 0xFF).toFloat() / 255f
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (maximum == 0f) 0f else delta / maximum
    return Triple(hue, saturation, maximum)
}

fun DrawScope.drawDrawingBackground(canvas: DrawingCanvas) {
    val brush = if (canvas.backgroundMode.equals("GRADIENT", ignoreCase = true)) {
        val start = when (canvas.gradientDirection.uppercase()) {
            "LEFT_TO_RIGHT" -> Offset(0f, size.height / 2f)
            "TOP_LEFT_TO_BOTTOM_RIGHT" -> Offset.Zero
            "TOP_RIGHT_TO_BOTTOM_LEFT" -> Offset(size.width, 0f)
            else -> Offset(size.width / 2f, 0f)
        }
        val end = when (canvas.gradientDirection.uppercase()) {
            "LEFT_TO_RIGHT" -> Offset(size.width, size.height / 2f)
            "TOP_LEFT_TO_BOTTOM_RIGHT" -> Offset(size.width, size.height)
            "TOP_RIGHT_TO_BOTTOM_LEFT" -> Offset(0f, size.height)
            else -> Offset(size.width / 2f, size.height)
        }
        Brush.linearGradient(
            colors = listOf(canvas.gradientStartColor.toDrawingColor(), canvas.gradientEndColor.toDrawingColor()),
            start = start,
            end = end,
        )
    } else {
        SolidColor(canvas.backgroundColor.toDrawingColor())
    }
    drawRect(brush = brush)
}
