package com.jaroapps.funnymail.features.drawing.presentation

import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jaroapps.funnymail.core.storage.DrawingDraftStorage
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.image.EdgeMap
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import com.jaroapps.funnymail.features.drawing.domain.image.EdgeMapVectorizer
import com.jaroapps.funnymail.features.drawing.domain.image.NoContoursFoundException
import com.jaroapps.funnymail.features.drawing.domain.image.PhotoEdgeDetector
import com.jaroapps.funnymail.features.drawing.domain.image.PhotoSketchSettings
import com.jaroapps.funnymail.features.drawing.domain.image.PhotoToSketchConverter
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingCanvas
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingText
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingSticker
import com.jaroapps.funnymail.features.drawing.platform.decodePhotoToGrayscale
import com.jaroapps.funnymail.features.drawing.platform.rememberPhotoPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import com.jaroapps.funnymail.resources.cancel
import com.jaroapps.funnymail.resources.changes_saved
import com.jaroapps.funnymail.resources.continue_drawing
import com.jaroapps.funnymail.resources.done
import com.jaroapps.funnymail.resources.draw_something
import com.jaroapps.funnymail.resources.drawing_background
import com.jaroapps.funnymail.resources.drawing_edits_saved
import com.jaroapps.funnymail.resources.drawing_saved_locally
import com.jaroapps.funnymail.resources.drawing_sent_chat
import com.jaroapps.funnymail.resources.eraser
import com.jaroapps.funnymail.resources.finish_drawing
import com.jaroapps.funnymail.resources.finish_drawing_hint
import com.jaroapps.funnymail.resources.gradient
import com.jaroapps.funnymail.resources.line_color
import com.jaroapps.funnymail.resources.import_image
import com.jaroapps.funnymail.resources.import_image_hint
import com.jaroapps.funnymail.resources.choose_from_gallery
import com.jaroapps.funnymail.resources.take_photo
import com.jaroapps.funnymail.resources.processing_image
import com.jaroapps.funnymail.resources.photo_conversion_complete
import com.jaroapps.funnymail.resources.photo_import_failed
import com.jaroapps.funnymail.resources.photo_no_contours
import com.jaroapps.funnymail.resources.save_changes
import com.jaroapps.funnymail.resources.save_drawing_local
import com.jaroapps.funnymail.resources.saving
import com.jaroapps.funnymail.resources.send_to_chat
import com.jaroapps.funnymail.resources.sending
import com.jaroapps.funnymail.resources.solid
import com.jaroapps.funnymail.resources.start_drawing_here
import com.jaroapps.funnymail.resources.Res

private val DrawingPalette = listOf(
    "#202124",
    "#D93025",
    "#F29900",
    "#188038",
    "#1A73E8",
    "#A142F4",
)

private val BrushWidths = listOf(2f, 5f, 9f, 15f, 23f, 36f, 52f, 72f)
private val BrushDotSizes = listOf(4.dp, 6.dp, 9.dp, 12.dp, 15.dp, 19.dp, 23.dp, 27.dp)

private data class PhotoConversionResult(
    val document: DrawingDocument,
    val edgeMap: EdgeMap,
    val sourceImage: GrayImage,
)

private fun convertPhotoToSketch(
    grayscale: GrayImage,
    settings: PhotoSketchSettings,
): PhotoConversionResult {
    val pipeline = PhotoToSketchConverter(
        edgeDetector = PhotoEdgeDetector(relativeHighThreshold = settings.edgeSensitivity),
        vectorizer = EdgeMapVectorizer(settings = settings),
    )
    val edgeMap = pipeline.detectEdges(grayscale)
    val document = pipeline.vectorize(edgeMap)
    return PhotoConversionResult(document, edgeMap, grayscale)
}

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
    val drawSomethingMessage = stringResource(Res.string.draw_something)
    val drawingEditsSavedMessage = stringResource(Res.string.drawing_edits_saved)
    val drawingSavedLocallyMessage = stringResource(Res.string.drawing_saved_locally)
    val drawingSentChatMessage = stringResource(Res.string.drawing_sent_chat)
    val importImageTitle = stringResource(Res.string.import_image)
    val importImageHint = stringResource(Res.string.import_image_hint)
    val chooseFromGalleryLabel = stringResource(Res.string.choose_from_gallery)
    val takePhotoLabel = stringResource(Res.string.take_photo)
    val processingImageLabel = stringResource(Res.string.processing_image)
    val photoConversionCompleteMessage = stringResource(Res.string.photo_conversion_complete)
    val photoImportFailedMessage = stringResource(Res.string.photo_import_failed)
    val photoNoContoursMessage = stringResource(Res.string.photo_no_contours)
    val coroutineScope = rememberCoroutineScope()
    val effectiveInitialDrawingJson = remember(initialDrawingJson) {
        initialDrawingJson ?: DrawingDraftStorage.load()
    }
    val initialDrawing = remember(effectiveInitialDrawingJson) {
        effectiveInitialDrawingJson?.let { drawingCodec.decode(it).getOrNull() }
    }
    var strokes by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.strokes ?: emptyList())
    }
    var textAnnotations by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.texts ?: emptyList())
    }
    var stickers by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.stickers ?: emptyList())
    }
    var pendingTextPlacement by remember { mutableStateOf<DrawingText?>(null) }
    var pendingStickerCode by remember { mutableStateOf<String?>(null) }
    var stickerCode by remember { mutableStateOf(":smile:") }
    var stickerScale by remember { mutableStateOf(1f) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showStickerDialog by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var editorTextSize by remember { mutableStateOf(64f) }
    var editorTextRotation by remember { mutableStateOf(0f) }
    var editorTextBold by remember { mutableStateOf(false) }
    var editorTextItalic by remember { mutableStateOf(false) }
    var editorTextUnderline by remember { mutableStateOf(false) }
    var photoMode by remember { mutableStateOf("CONTOURS") }
    var contourStrokes by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var redoStrokes by remember(effectiveInitialDrawingJson) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var savedDrawingJson by remember(effectiveInitialDrawingJson) { mutableStateOf(effectiveInitialDrawingJson) }
    var isSaving by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var activePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var selectedColor by remember(effectiveInitialDrawingJson) { mutableStateOf("#000000") }
    var pendingLineColor by remember { mutableStateOf("#FFFFFF") }
    var brushAlpha by remember(effectiveInitialDrawingJson) { mutableStateOf(1f) }
    var backgroundAlpha by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.backgroundAlpha ?: 1f)
    }
    var pendingBrushAlpha by remember { mutableStateOf(1f) }
    var pendingBackgroundAlpha by remember { mutableStateOf(1f) }
    var drawingTitle by remember(effectiveInitialDrawingJson) { mutableStateOf(initialDrawing?.title.orEmpty().take(20)) }
    var drawingCaption by remember(effectiveInitialDrawingJson) { mutableStateOf(initialDrawing?.caption.orEmpty().take(80)) }
    var brushWidth by remember { mutableStateOf(12f) }
    var eraserEnabled by remember { mutableStateOf(false) }
    var lineToolEnabled by remember { mutableStateOf(false) }
    var pendingLinePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var activePopup by remember { mutableStateOf<DrawingPopup?>(null) }
    var backgroundMode by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.backgroundMode ?: "SOLID")
    }
    var backgroundColor by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.backgroundColor ?: "#FFFFFF")
    }
    var gradientDirection by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.gradientDirection ?: "TOP_TO_BOTTOM")
    }
    var gradientStartColor by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.gradientStartColor ?: "#00AA00")
    }
    var gradientEndColor by remember(effectiveInitialDrawingJson) {
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
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var diagnosticEdgeMap by remember { mutableStateOf<EdgeMap?>(null) }
    var sourceGrayImage by remember(effectiveInitialDrawingJson) { mutableStateOf<GrayImage?>(null) }
    var photoQualitySettings by remember { mutableStateOf(PhotoSketchSettings()) }
    var appliedPhotoQualitySettings by remember { mutableStateOf(PhotoSketchSettings()) }
    var isReprocessingPhoto by remember { mutableStateOf(false) }
    var photoQualityError by remember { mutableStateOf<String?>(null) }
    var showPhotoQualitySettings by remember { mutableStateOf(false) }
    var showEdgeMapPreview by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasWidth by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.width ?: 1000)
    }
    var canvasHeight by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing?.canvas?.height ?: 1000)
    }
    var canvasAspectInitialized by remember(effectiveInitialDrawingJson) {
        mutableStateOf(initialDrawing != null)
    }
    var isDrawingGestureActive by remember { mutableStateOf(false) }
    // Eraser removes nearby vector strokes; it never paints with the background color.
    val paintColor = selectedColor
    val currentDrawing = remember(
        strokes,
        canvasWidth,
        canvasHeight,
        drawingTitle,
        drawingCaption,
        textAnnotations,
        stickers,
        backgroundMode,
        backgroundColor,
        gradientDirection,
        gradientStartColor,
        gradientEndColor,
        backgroundAlpha,
    ) {
        if (strokes.isEmpty() && textAnnotations.isEmpty() && stickers.isEmpty()) {
            null
        } else {
            DrawingDocument(
                canvas = DrawingCanvas(
                    width = canvasWidth,
                    height = canvasHeight,
                    backgroundColor = backgroundColor,
                    backgroundMode = backgroundMode,
                    gradientDirection = gradientDirection,
                    gradientStartColor = gradientStartColor,
                    gradientEndColor = gradientEndColor,
                    backgroundAlpha = backgroundAlpha,
                ),
                strokes = strokes,
                title = drawingTitle,
                caption = drawingCaption,
                texts = textAnnotations,
                stickers = stickers,
            )
        }
    }
    val currentDrawingEncoding = remember(currentDrawing) {
        currentDrawing?.let { drawingCodec.encodeWithSize(it) }
    }
    val currentDrawingJson = currentDrawingEncoding?.first?.getOrNull()
    val currentDrawingJsonBytes = currentDrawingEncoding?.second ?: 0

    // Keep the in-progress drawing in local storage so rotation/recreation does not discard strokes.
    LaunchedEffect(currentDrawingJson) {
        if (currentDrawingJson != null) DrawingDraftStorage.save(currentDrawingJson)
    }
    val currentDrawingError = currentDrawingEncoding?.first?.exceptionOrNull()?.message
    val currentDrawingPointCount = remember(strokes) {
        strokes.sumOf { it.points.size }
    }
    val currentDrawingJsonKiB = (currentDrawingJsonBytes / 1024.0 * 10.0).roundToInt() / 10.0
    val maxDrawingJsonKiB = (drawingCodec.maxJsonBytes / 1024.0 * 10.0).roundToInt() / 10.0
    val isCurrentDrawingSaved = currentDrawingJson != null && currentDrawingJson == savedDrawingJson

    val photoPicker = rememberPhotoPicker(
        onImagePicked = { imageBytes ->
            if (!isProcessingImage) {
                coroutineScope.launch {
                    isProcessingImage = true
                    statusMessage = null
                    try {
                        val settingsForImport = photoQualitySettings
                        val converted = withContext(Dispatchers.Default) {
                            val grayscale = decodePhotoToGrayscale(
                                imageBytes,
                                maxDimension = 1024,
                            ) ?: throw IllegalStateException(
                                "The selected photo could not be decoded for edge detection.",
                            )
                            // Keep the grayscale source in memory so the editor can regenerate the
                            // edge map live when the user changes quality controls.
                            convertPhotoToSketch(grayscale, settingsForImport)
                        }
                        val convertedDrawing = converted.document
                        sourceGrayImage = converted.sourceImage
                        contourStrokes = convertedDrawing.strokes
                        photoMode = "CONTOURS"
                        appliedPhotoQualitySettings = settingsForImport
                        diagnosticEdgeMap = converted.edgeMap
                        photoQualityError = null
                        showPhotoQualitySettings = false
                        canvasWidth = convertedDrawing.canvas.width
                        canvasHeight = convertedDrawing.canvas.height
                        canvasAspectInitialized = true
                        strokes = convertedDrawing.strokes
                        activePoints = emptyList()
                        redoStrokes = emptyList()
                        backgroundMode = "SOLID"
                        backgroundColor = "#FFFFFF"
                        selectedColor = "#202124"
                        eraserEnabled = false
                        statusMessage = photoConversionCompleteMessage
                        statusIsError = false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val detailLimitMessage = error.message?.takeIf {
                            it.startsWith("Карта содержит") || it.startsWith("Рисунок слишком большой")
                        }
                        statusMessage = when {
                            error is NoContoursFoundException -> photoNoContoursMessage
                            detailLimitMessage != null -> detailLimitMessage
                            else -> photoImportFailedMessage
                        }
                        statusIsError = true
                    } finally {
                        imageBytes.fill(0)
                        isProcessingImage = false
                    }
                }
            } else {
                imageBytes.fill(0)
            }
        },
        onError = {
            statusMessage = photoImportFailedMessage
            statusIsError = true
        },
    )

    LaunchedEffect(sourceGrayImage, photoQualitySettings, photoMode) {
        if (photoMode != "CONTOURS") {
            isReprocessingPhoto = false
            return@LaunchedEffect
        }
        val source = sourceGrayImage ?: return@LaunchedEffect
        val targetSettings = photoQualitySettings
        if (targetSettings == appliedPhotoQualitySettings) {
            photoQualityError = null
            isReprocessingPhoto = false
            return@LaunchedEffect
        }

        // Slider drags can emit many updates; debounce so the detector runs after the user pauses.
        delay(220)
        isReprocessingPhoto = true
        photoQualityError = null
        try {
            val converted = withContext(Dispatchers.Default) {
                convertPhotoToSketch(source, targetSettings)
            }
            if (sourceGrayImage === source && photoQualitySettings == targetSettings) {
                val document = converted.document
                diagnosticEdgeMap = converted.edgeMap
                contourStrokes = document.strokes
                canvasWidth = document.canvas.width
                canvasHeight = document.canvas.height
                canvasAspectInitialized = true
                if (photoMode == "CONTOURS") strokes = document.strokes
                activePoints = emptyList()
                redoStrokes = emptyList()
                appliedPhotoQualitySettings = targetSettings
                statusMessage = null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (sourceGrayImage === source && photoQualitySettings == targetSettings) {
                photoQualityError = error.message ?: "Не удалось применить настройки качества."
            }
        } finally {
            if (photoQualitySettings == targetSettings) isReprocessingPhoto = false
        }
    }

    val saveDrawingAction: () -> Unit = {
        val json = currentDrawingJson
        if (json == null) {
            statusMessage = currentDrawingError ?: drawSomethingMessage
            statusIsError = true
        } else if (!isSaving && !isCurrentDrawingSaved) {
            isSaving = true
            onSaveDrawing(json) { saveError ->
                isSaving = false
                if (saveError == null) {
                    DrawingDraftStorage.clear()
                    savedDrawingJson = json
                    statusMessage = if (effectiveInitialDrawingJson != null) {
                        drawingEditsSavedMessage
                    } else {
                        drawingSavedLocallyMessage
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
            statusMessage = currentDrawingError ?: drawSomethingMessage
            statusIsError = true
        } else if (!isSending) {
            isSending = true
            onSendDrawing(json) { sendError ->
                isSending = false
                if (sendError == null) {
                    DrawingDraftStorage.clear()
                    statusMessage = drawingSentChatMessage
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
                    lineToolEnabled, pendingTextPlacement, pendingStickerCode, stickerScale,
                    canvasSize.width, canvasSize.height, canvasWidth, canvasHeight, pendingLinePoints,
                ) {
                    if (lineToolEnabled || pendingTextPlacement != null || pendingStickerCode != null) {
                        detectTapGestures { tap ->
                            val viewportScale = min(
                                size.width.toFloat() / canvasWidth.toFloat(),
                                size.height.toFloat() / canvasHeight.toFloat(),
                            ).coerceAtLeast(0.0001f)
                            val viewportWidth = canvasWidth.toFloat() * viewportScale
                            val viewportHeight = canvasHeight.toFloat() * viewportScale
                            val viewportLeft = (size.width.toFloat() - viewportWidth) / 2f
                            val viewportTop = (size.height.toFloat() - viewportHeight) / 2f
                            if (tap.x in viewportLeft..(viewportLeft + viewportWidth) &&
                                tap.y in viewportTop..(viewportTop + viewportHeight)
                            ) {
                                val point = listOf(
                                    ((tap.x - viewportLeft) / viewportWidth).coerceIn(0f, 1f).toDouble(),
                                    ((tap.y - viewportTop) / viewportHeight).coerceIn(0f, 1f).toDouble(),
                                )
                                when {
                                    pendingTextPlacement != null -> {
                                        textAnnotations = textAnnotations + pendingTextPlacement!!.copy(x = point[0], y = point[1])
                                        pendingTextPlacement = null
                                        statusMessage = "Текст размещён"
                                    }
                                    pendingStickerCode != null -> {
                                        stickers = stickers + DrawingSticker(
                                            code = pendingStickerCode!!,
                                            x = point[0],
                                            y = point[1],
                                            scale = stickerScale,
                                        )
                                        pendingStickerCode = null
                                        statusMessage = "Смайлик размещён"
                                    }
                                    lineToolEnabled -> {
                                        pendingLinePoints = when (pendingLinePoints.size) {
                                            0 -> listOf(point)
                                            1 -> pendingLinePoints + point
                                            else -> listOf(pendingLinePoints.first(), point)
                                        }
                                        statusMessage = if (pendingLinePoints.size < 2) {
                                            "Поставьте вторую точку линии"
                                        } else {
                                            "Перетаскивайте любую точку линии; ✓ фиксирует линию"
                                        }
                                    }
                                }
                                statusIsError = false
                            }
                        }
                    }
                }
                .pointerInput(
                    lineToolEnabled,
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

                    var draggingEndpoint: Int? = null
                    detectDragGestures(
                        onDragStart = { offset ->
                            statusMessage = null
                            draggingEndpoint = if (lineToolEnabled && pendingLinePoints.size == 2) {
                                pendingLinePoints.indices
                                    .map { index ->
                                        val point = pendingLinePoints[index]
                                        val px = viewportLeft + (point[0] * viewportWidth).toFloat()
                                        val py = viewportTop + (point[1] * viewportHeight).toFloat()
                                        val dx = offset.x - px
                                        val dy = offset.y - py
                                        index to (dx * dx + dy * dy)
                                    }
                                    .minByOrNull { it.second }
                                    ?.takeIf { it.second <= 44f * 44f }
                                    ?.first
                            } else null
                            isDrawingGestureActive = !lineToolEnabled && draggingEndpoint == null &&
                                isInsideViewport(offset.x, offset.y)
                            activePoints = if (isDrawingGestureActive) {
                                listOf(normalizedPoint(offset.x, offset.y))
                            } else {
                                emptyList()
                            }
                        },
                        onDrag = { change, _ ->
                            if (draggingEndpoint != null && lineToolEnabled) {
                                change.consume()
                                val normalized = normalizedPoint(change.position.x, change.position.y)
                                pendingLinePoints = pendingLinePoints.mapIndexed { index, point ->
                                    if (index == draggingEndpoint) normalized else point
                                }
                            } else if (isDrawingGestureActive) {
                                change.consume()
                                activePoints = activePoints + listOf(
                                    normalizedPoint(change.position.x, change.position.y),
                                )
                            }
                        },
                        onDragEnd = {
                            if (draggingEndpoint != null) {
                                statusMessage = "Точка линии перемещена. Нажмите ✓ для фиксации"
                                statusIsError = false
                                draggingEndpoint = null
                            }
                            if (isDrawingGestureActive && activePoints.isNotEmpty()) {
                                if (eraserEnabled) {
                                    val eraseRadius = (brushWidth.toDouble() / min(canvasWidth, canvasHeight).coerceAtLeast(1)) * 1.8
                                    val erasedIndices = strokes.indices.filter { strokeIndex ->
                                        strokes[strokeIndex].points.any { point ->
                                            activePoints.any { eraserPoint ->
                                                val dx = point[0] - eraserPoint[0]
                                                val dy = point[1] - eraserPoint[1]
                                                sqrt(dx * dx + dy * dy) <= eraseRadius
                                            }
                                        }
                                    }
                                    if (erasedIndices.isNotEmpty()) {
                                        redoStrokes = redoStrokes + erasedIndices.map { strokes[it] }
                                        strokes = strokes.filterIndexed { index, _ -> index !in erasedIndices }
                                    }
                                } else {
                                    strokes = strokes + DrawingStroke(
                                        color = paintColor,
                                        width = brushWidth,
                                        alpha = brushAlpha,
                                        points = activePoints,
                                    )
                                    redoStrokes = emptyList()
                                }
                                // Quality controls regenerate the whole photo-derived vector.
                                // Close them before a manual stroke can be added or overwritten.
                                if (photoMode != "TRACE") sourceGrayImage = null
                                showPhotoQualitySettings = false
                            }
                            isDrawingGestureActive = false
                            activePoints = emptyList()
                        },
                        onDragCancel = {
                            isDrawingGestureActive = false
                            movingLineEndpointIndex = null
                            activePoints = emptyList()
                        },
                    )
                },
        ) {
            if (photoMode == "TRACE" && sourceGrayImage != null) {
                drawTracingImage(sourceGrayImage!!, alpha = 0.58f)
                drawRect(Color.White.copy(alpha = 0.22f))
            } else {
                drawDrawingBackground(
                    DrawingCanvas(
                        width = canvasWidth,
                        height = canvasHeight,
                        backgroundColor = backgroundColor,
                        backgroundMode = backgroundMode,
                        gradientDirection = gradientDirection,
                        gradientStartColor = gradientStartColor,
                        gradientEndColor = gradientEndColor,
                        backgroundAlpha = backgroundAlpha,
                    ),
                )
            }
            val visibleStrokes = buildList {
                addAll(strokes)
                if (pendingLinePoints.isNotEmpty()) {
                    add(
                        DrawingStroke(
                            color = selectedColor,
                            width = brushWidth,
                            alpha = brushAlpha,
                            points = pendingLinePoints,
                        ),
                    )
                } else if (activePoints.isNotEmpty() && !eraserEnabled && !lineToolEnabled) {
                    add(
                        DrawingStroke(
                            color = paintColor,
                            width = brushWidth,
                            alpha = brushAlpha,
                            points = activePoints,
                        ),
                    )
                }
            }
            drawDrawingStrokes(
                strokes = visibleStrokes,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
            )
        }

        currentDrawing?.let { document ->
            DrawingAnnotationsOverlay(
                drawing = document,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (strokes.isEmpty() && textAnnotations.isEmpty() && stickers.isEmpty() && activePoints.isEmpty() && photoMode != "TRACE") {
            Text(
                text = stringResource(Res.string.start_drawing_here),
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundToolButton(
                    enabled = !isProcessingImage,
                    onClick = { showPhotoSourceDialog = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = importImageTitle,
                        tint = Color(0xFF202124),
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (sourceGrayImage != null && photoMode == "CONTOURS") {
                    RoundToolButton(
                        enabled = !isProcessingImage && !isReprocessingPhoto,
                        onClick = { showPhotoQualitySettings = !showPhotoQualitySettings },
                    ) {
                        Text(
                            if (showPhotoQualitySettings) "Готово" else "Кач.",
                            color = Color(0xFF202124),
                            fontSize = 9.sp,
                        )
                    }
                }

                RoundToolButton(
                    background = if (lineToolEnabled) Color(0xFFFFF0C2) else Color.White,
                    onClick = {
                        lineToolEnabled = !lineToolEnabled
                        pendingLinePoints = emptyList()
                        activePoints = emptyList()
                        statusMessage = if (lineToolEnabled) {
                            "Коснитесь двух точек. Нажмите ✓ для фиксации линии."
                        } else null
                        statusIsError = false
                    },
                ) {
                    Text("Линия", color = Color(0xFF202124), fontSize = 9.sp)
                }

                if (diagnosticEdgeMap != null && photoMode == "CONTOURS") {
                    RoundToolButton(
                        onClick = { showEdgeMapPreview = true },
                    ) {
                        Text("Карта", color = Color(0xFF202124), fontSize = 9.sp)
                    }
                }

                RoundToolButton(
                    enabled = strokes.isNotEmpty(),
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            redoStrokes = redoStrokes + strokes.last()
                            strokes = strokes.dropLast(1)
                            if (photoMode != "TRACE") sourceGrayImage = null
                            showPhotoQualitySettings = false
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
                            if (photoMode != "TRACE") sourceGrayImage = null
                            showPhotoQualitySettings = false
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
                    if (lineToolEnabled) {
                        if (pendingLinePoints.size == 2) {
                            strokes = strokes + DrawingStroke(
                                color = selectedColor,
                                width = brushWidth,
                                alpha = brushAlpha,
                                points = pendingLinePoints,
                            )
                            redoStrokes = emptyList()
                            pendingLinePoints = emptyList()
                            lineToolEnabled = false
                            statusMessage = "Прямая линия зафиксирована"
                            statusIsError = false
                        } else {
                            statusMessage = "Поставьте две точки линии"
                            statusIsError = true
                        }
                    } else if (currentDrawingJson == null) {
                        statusMessage = currentDrawingError ?: drawSomethingMessage
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

        if (sourceGrayImage != null) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundToolButton(
                    background = if (photoMode == "TRACE") Color(0xFFFFF0C2) else Color.White,
                    onClick = {
                        if (photoMode != "TRACE") {
                            contourStrokes = strokes
                            strokes = emptyList()
                            photoMode = "TRACE"
                            showPhotoQualitySettings = false
                            statusMessage = "Калька: рисуйте поверх полупрозрачной фотографии"
                        }
                    },
                ) { Text("Калька", color = Color(0xFF202124), fontSize = 10.sp) }
                RoundToolButton(
                    background = if (photoMode == "CONTOURS") Color(0xFFFFF0C2) else Color.White,
                    onClick = {
                        if (photoMode != "CONTOURS") {
                            photoMode = "CONTOURS"
                            strokes = contourStrokes
                            showPhotoQualitySettings = false
                            statusMessage = "Контуры восстановлены"
                        }
                    },
                ) { Text("Контуры", color = Color(0xFF202124), fontSize = 9.sp) }
            }
        }

        statusMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (sourceGrayImage != null) 114.dp else 66.dp, start = 18.dp, end = 18.dp)
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

        if (!showPhotoQualitySettings) Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp, start = 12.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val overLimit = currentDrawing != null && currentDrawingJson == null
            Text(
                text = "JSON: $currentDrawingJsonBytes Б ($currentDrawingJsonKiB КиБ) / $maxDrawingJsonKiB КиБ",
                color = if (overLimit) MaterialTheme.colorScheme.error else Color(0xFF5F6368),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
            )
            Text(
                text = "Штрихов: ${strokes.size} · Точек: $currentDrawingPointCount",
                color = if (overLimit) MaterialTheme.colorScheme.error else Color(0xFF5F6368),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
            )
            if (overLimit) {
                Text(
                    text = currentDrawingError ?: "Превышен лимит сохранения",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                )
            }
        }

        if (showPhotoQualitySettings && sourceGrayImage != null && photoMode == "CONTOURS") {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.98f))
                    .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Качество эскиза",
                        color = Color(0xFF202124),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            photoQualitySettings = PhotoSketchSettings()
                            photoQualityError = null
                        },
                    ) { Text("Сброс", color = Color(0xFF1A73E8)) }
                    TextButton(onClick = { showPhotoQualitySettings = false }) {
                        Text("Свернуть", color = Color(0xFF202124))
                    }
                }
                Text(
                    "JSON: $currentDrawingJsonBytes Б ($currentDrawingJsonKiB КиБ) / $maxDrawingJsonKiB КиБ",
                    color = if (currentDrawingJson == null && currentDrawing != null) {
                        MaterialTheme.colorScheme.error
                    } else Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "Штрихов: ${strokes.size} · Точек: $currentDrawingPointCount",
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (currentDrawing != null && currentDrawingJson == null) {
                    Text(
                        currentDrawingError ?: "Рисунок превышает лимит сохранения.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isReprocessingPhoto || photoQualitySettings != appliedPhotoQualitySettings) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = Color(0xFFF5A800),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "Перестраиваю контуры…",
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                photoQualityError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                PhotoQualitySlider(
                    label = "Максимум точек",
                    value = photoQualitySettings.maxPoints.toFloat(),
                    valueLabel = photoQualitySettings.maxPoints.toString(),
                    valueRange = 1_000f..120_000f,
                    // Continuous slider keeps the preferred default (6,500) selectable rather
                    // than snapping to one of the evenly spaced discrete ticks.
                    steps = 0,
                    onValueChange = { photoQualitySettings = photoQualitySettings.copy(maxPoints = it.roundToInt()) },
                )
                PhotoQualitySlider(
                    label = "Чувствительность границ (ниже = больше деталей)",
                    value = photoQualitySettings.edgeSensitivity,
                    valueLabel = "${(photoQualitySettings.edgeSensitivity * 100f).roundToInt()}%",
                    valueRange = 0.04f..0.35f,
                    steps = 30,
                    onValueChange = { photoQualitySettings = photoQualitySettings.copy(edgeSensitivity = it) },
                )
                PhotoQualitySlider(
                    label = "Минимальная сила границы",
                    value = photoQualitySettings.minimumEdgeStrength.toFloat(),
                    valueLabel = photoQualitySettings.minimumEdgeStrength.toString(),
                    valueRange = 0f..200f,
                    steps = 19,
                    onValueChange = { photoQualitySettings = photoQualitySettings.copy(minimumEdgeStrength = it.roundToInt()) },
                )
                PhotoQualitySlider(
                    label = "Упрощение контуров",
                    value = photoQualitySettings.simplificationTolerance.toFloat(),
                    valueLabel = "${(photoQualitySettings.simplificationTolerance * 100.0).roundToInt() / 100.0}",
                    valueRange = 0f..5f,
                    steps = 19,
                    onValueChange = { photoQualitySettings = photoQualitySettings.copy(simplificationTolerance = it.toDouble()) },
                )
                PhotoQualitySlider(
                    label = "Толщина линий",
                    value = photoQualitySettings.strokeWidth,
                    valueLabel = "${(photoQualitySettings.strokeWidth * 10f).roundToInt() / 10f}",
                    valueRange = 0.8f..6f,
                    steps = 25,
                    onValueChange = { photoQualitySettings = photoQualitySettings.copy(strokeWidth = it) },
                )
                PhotoQualitySlider(
                    label = "Удалить слабые одиночные точки",
                    value = photoQualitySettings.isolatedPointRemovalPercent.toFloat(),
                    valueLabel = "${photoQualitySettings.isolatedPointRemovalPercent}%",
                    valueRange = 0f..80f,
                    steps = 15,
                    onValueChange = {
                        photoQualitySettings = photoQualitySettings.copy(isolatedPointRemovalPercent = it.roundToInt())
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Объединять точки в линии", color = Color(0xFF202124), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Выключите, чтобы видеть точки отдельно; лимит точек продолжит действовать.",
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = photoQualitySettings.mergePointsIntoLines,
                        onCheckedChange = {
                            photoQualitySettings = photoQualitySettings.copy(mergePointsIntoLines = it)
                        },
                    )
                }
                Text(
                    "Изменения применяются к предпросмотру автоматически. После начала ручного рисования панель качества закрывается, чтобы не перезаписать ваши штрихи.",
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            RoundToolButton(
                onClick = {
                    activePopup = null
                    editorText = ""
                    editorTextSize = 64f
                    editorTextRotation = 0f
                    editorTextBold = false
                    editorTextItalic = false
                    editorTextUnderline = false
                    showTextDialog = true
                },
            ) { Text("T", color = Color(0xFF202124), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }

            RoundToolButton(
                onClick = {
                    activePopup = null
                    stickerCode = ":smile:"
                    stickerScale = 1f
                    showStickerDialog = true
                },
            ) { Text("😊", fontSize = 22.sp) }

            RoundToolButton(
                onClick = {
                    activePopup = null
                    pendingLineColor = "#FFFFFF"
                    pendingBrushAlpha = 1f
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
                        pendingBackgroundColor = "#FFFFFF"
                        pendingBackgroundAlpha = 1f
                        pendingGradientDirection = gradientDirection
                        pendingGradientStartColor = "#FFFFFF"
                        pendingGradientEndColor = "#FFFFFF"
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

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Добавить текст") },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = editorText,
                        onValueChange = { editorText = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Текст") },
                        supportingText = { Text("${editorText.length}/240") },
                    )
                    Text("Размер шрифта: ${editorTextSize.roundToInt()}", color = Color(0xFF202124))
                    Slider(value = editorTextSize, onValueChange = { editorTextSize = it }, valueRange = 20f..120f)
                    Text("Угол: ${editorTextRotation.roundToInt()}°", color = Color(0xFF202124))
                    Slider(value = editorTextRotation, onValueChange = { editorTextRotation = it }, valueRange = -180f..180f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Жирный", modifier = Modifier.weight(1f), color = Color(0xFF202124))
                        Switch(checked = editorTextBold, onCheckedChange = { editorTextBold = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Курсив", modifier = Modifier.weight(1f), color = Color(0xFF202124))
                        Switch(checked = editorTextItalic, onCheckedChange = { editorTextItalic = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Подчёркнутый", modifier = Modifier.weight(1f), color = Color(0xFF202124))
                        Switch(checked = editorTextUnderline, onCheckedChange = { editorTextUnderline = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTextDialog = false
                        if (editorText.isNotBlank()) {
                            pendingTextPlacement = DrawingText(
                                text = editorText,
                                x = 0.5,
                                y = 0.5,
                                color = selectedColor,
                                fontSize = editorTextSize,
                                rotationDegrees = editorTextRotation,
                                bold = editorTextBold,
                                italic = editorTextItalic,
                                underline = editorTextUnderline,
                                alpha = brushAlpha,
                            )
                            lineToolEnabled = false
                            pendingStickerCode = null
                            statusMessage = "Коснитесь холста, чтобы разместить текст"
                            statusIsError = false
                        } else {
                            statusMessage = "Введите текст"
                            statusIsError = true
                        }
                    },
                    enabled = editorText.isNotBlank(),
                ) { Text("Разместить", color = Color(0xFF202124)) }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Отмена") } },
        )
    }

    if (showStickerDialog) {
        AlertDialog(
            onDismissRequest = { showStickerDialog = false },
            title = { Text("Добавить смайлик") },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        ":smile:" to "😊",
                        ":heart:" to "❤️",
                        ":wink:" to "😉",
                        ":laugh:" to "😂",
                        ":sad:" to "🙁",
                        ":cool:" to "😎",
                    ).chunked(3).forEach { options ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { (code, emoji) ->
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (stickerCode == code) Color(0xFFFFF0C2) else Color(0xFFF5F5F5))
                                        .clickable { stickerCode = code },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, fontSize = 27.sp) }
                            }
                        }
                    }
                    Text("Масштаб: ${(stickerScale * 100f).roundToInt()}%", color = Color(0xFF202124))
                    Slider(value = stickerScale, onValueChange = { stickerScale = it }, valueRange = 0.25f..4f)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStickerDialog = false
                        pendingStickerCode = stickerCode
                        pendingTextPlacement = null
                        lineToolEnabled = false
                        statusMessage = "Коснитесь холста, чтобы разместить смайлик"
                        statusIsError = false
                    },
                ) { Text("Разместить", color = Color(0xFF202124)) }
            },
            dismissButton = { TextButton(onClick = { showStickerDialog = false }) { Text("Отмена") } },
        )
    }

    if (showLineColorDialog) {
        AlertDialog(
            onDismissRequest = { showLineColorDialog = false },
            title = { Text(stringResource(Res.string.line_color)) },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColorWheel(
                        selectedColor = pendingLineColor,
                        onColorSelected = { pendingLineColor = it },
                    )
                    Text("Прозрачность ${(pendingBrushAlpha * 100f).roundToInt()}%", color = Color(0xFF202124))
                    Slider(
                        value = pendingBrushAlpha,
                        onValueChange = { pendingBrushAlpha = it },
                        valueRange = 0f..1f,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedColor = pendingLineColor
                        brushAlpha = pendingBrushAlpha
                        eraserEnabled = false
                        showLineColorDialog = false
                    },
                ) { Text(stringResource(Res.string.done), color = Color(0xFF202124)) }
            },
        )
    }

    if (showBackgroundDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundDialog = false },
            title = { Text(stringResource(Res.string.drawing_background)) },
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
                        listOf("SOLID" to stringResource(Res.string.solid), "GRADIENT" to stringResource(Res.string.gradient)).forEach { (tab, label) ->
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
                        Text("Прозрачность ${(pendingBackgroundAlpha * 100f).roundToInt()}%", color = Color(0xFF202124))
                        Slider(
                            value = pendingBackgroundAlpha,
                            onValueChange = { pendingBackgroundAlpha = it },
                            valueRange = 0f..1f,
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
                        Text("Прозрачность ${(pendingBackgroundAlpha * 100f).roundToInt()}%", color = Color(0xFF202124))
                        Slider(
                            value = pendingBackgroundAlpha,
                            onValueChange = { pendingBackgroundAlpha = it },
                            valueRange = 0f..1f,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backgroundMode = backgroundPickerTab
                        backgroundColor = pendingBackgroundColor
                        backgroundAlpha = pendingBackgroundAlpha
                        gradientDirection = pendingGradientDirection
                        gradientStartColor = pendingGradientStartColor
                        gradientEndColor = pendingGradientEndColor
                        showBackgroundDialog = false
                        statusMessage = null
                    },
                ) { Text(stringResource(Res.string.done), color = Color(0xFF202124)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDialog = false }) { Text(stringResource(Res.string.cancel), color = Color(0xFF202124)) }
            },
        )
    }

    if (showEdgeMapPreview) {
        val edgeMap = diagnosticEdgeMap
        if (edgeMap != null) {
            AlertDialog(
                onDismissRequest = { showEdgeMapPreview = false },
                title = { Text("Карта найденных границ") },
                containerColor = Color.White,
                titleContentColor = Color(0xFF202124),
                textContentColor = Color(0xFF202124),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Пикселей границ: ${edgeMap.strength.count {
                                val strength = it.toInt() and 0xFF
                                strength > 0 && strength >= photoQualitySettings.minimumEdgeStrength
                            }} · ${edgeMap.width} × ${edgeMap.height}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        ) {
                            drawRect(Color.White)
                            val scale = min(
                                size.width / edgeMap.width.toFloat(),
                                size.height / edgeMap.height.toFloat(),
                            )
                            val drawnWidth = edgeMap.width * scale
                            val drawnHeight = edgeMap.height * scale
                            val left = (size.width - drawnWidth) / 2f
                            val top = (size.height - drawnHeight) / 2f
                            // Downsample only this diagnostic view; the vector drawing uses every edge pixel.
                            val stepX = (edgeMap.width / 320).coerceAtLeast(1)
                            val stepY = (edgeMap.height / 320).coerceAtLeast(1)
                            for (y in 0 until edgeMap.height step stepY) {
                                val cellHeight = min(stepY, edgeMap.height - y)
                                for (x in 0 until edgeMap.width step stepX) {
                                    val cellWidth = min(stepX, edgeMap.width - x)
                                    var strength = 0
                                    for (sampleY in y until y + cellHeight) {
                                        for (sampleX in x until x + cellWidth) {
                                            strength = maxOf(
                                                strength,
                                                edgeMap.strength[sampleY * edgeMap.width + sampleX]
                                                    .toInt() and 0xFF,
                                            )
                                        }
                                    }
                                    if (strength == 0 || strength < photoQualitySettings.minimumEdgeStrength) continue
                                    val alpha = (0.25f + 0.75f * strength / 255f).coerceIn(0f, 1f)
                                    drawRect(
                                        color = Color.Black.copy(alpha = alpha),
                                        topLeft = Offset(left + x * scale, top + y * scale),
                                        size = Size(
                                            width = (cellWidth * scale).coerceAtLeast(1f),
                                            height = (cellHeight * scale).coerceAtLeast(1f),
                                        ),
                                    )
                                }
                            }
                        }
                        Text(
                            "Рисунок использует эту карту: слабые границы остаются светлыми, сильные — тёмными.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEdgeMapPreview = false }) {
                        Text("Закрыть", color = Color(0xFF202124))
                    }
                },
            )
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(importImageTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(importImageHint)
                    TextButton(onClick = {
                        showPhotoSourceDialog = false
                        photoPicker.openGallery()
                    }) { Text(chooseFromGalleryLabel) }
                    TextButton(onClick = {
                        showPhotoSourceDialog = false
                        photoPicker.openCamera()
                    }) { Text(takePhotoLabel) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (isProcessingImage) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = Color(0xFFF5A800))
                Text(
                    text = processingImageLabel,
                    color = Color(0xFF202124),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(Res.string.finish_drawing)) },
            containerColor = Color.White,
            titleContentColor = Color(0xFF202124),
            textContentColor = Color(0xFF202124),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.finish_drawing_hint))
                    OutlinedTextField(
                        value = drawingTitle,
                        onValueChange = { drawingTitle = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Заголовок (до 20 символов)") },
                        supportingText = { Text("${drawingTitle.length}/20") },
                    )
                    OutlinedTextField(
                        value = drawingCaption,
                        onValueChange = { drawingCaption = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Подпись (до 80 символов)") },
                        supportingText = { Text("${drawingCaption.length}/80") },
                    )
                }
            },
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
                                isSaving -> stringResource(Res.string.saving)
                                isCurrentDrawingSaved && effectiveInitialDrawingJson != null -> stringResource(Res.string.changes_saved)
                                effectiveInitialDrawingJson != null -> stringResource(Res.string.save_changes)
                                else -> stringResource(Res.string.save_drawing_local)
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
                        Text(if (isSending) stringResource(Res.string.sending) else stringResource(Res.string.send_to_chat))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(Res.string.continue_drawing))
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
            Text(stringResource(Res.string.eraser), color = Color(0xFF202124), fontSize = 12.sp)
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
            colors = listOf(
                canvas.gradientStartColor.toDrawingColor().copy(alpha = canvas.backgroundAlpha),
                canvas.gradientEndColor.toDrawingColor().copy(alpha = canvas.backgroundAlpha),
            ),
            start = start,
            end = end,
        )
    } else {
        SolidColor(canvas.backgroundColor.toDrawingColor().copy(alpha = canvas.backgroundAlpha))
    }
    drawRect(brush = brush)
}


@Composable
private fun PhotoQualitySlider(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = Color(0xFF202124),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                valueLabel,
                color = Color(0xFF5F6368),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTracingImage(
    image: GrayImage,
    alpha: Float,
) {
    val imageScale = min(size.width / image.width.toFloat(), size.height / image.height.toFloat())
    if (!imageScale.isFinite() || imageScale <= 0f) return
    val drawnWidth = image.width * imageScale
    val drawnHeight = image.height * imageScale
    val left = (size.width - drawnWidth) / 2f
    val top = (size.height - drawnHeight) / 2f
    val sampleX = (image.width + 179) / 180
    val sampleY = (image.height + 179) / 180
    for (y in 0 until image.height step sampleY) {
        val cellHeight = min(sampleY, image.height - y) * imageScale
        for (x in 0 until image.width step sampleX) {
            val value = image.pixels[y * image.width + x].coerceIn(0, 255) / 255f
            drawRect(
                color = Color(value, value, value, alpha.coerceIn(0f, 1f)),
                topLeft = Offset(left + x * imageScale, top + y * imageScale),
                size = Size(min(sampleX, image.width - x) * imageScale, cellHeight),
            )
        }
    }
}
