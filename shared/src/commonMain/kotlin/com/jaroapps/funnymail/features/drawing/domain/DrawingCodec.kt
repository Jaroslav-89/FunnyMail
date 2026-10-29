package com.jaroapps.funnymail.features.drawing.domain

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Validates and serializes drawings before they are persisted or sent to Firestore.
 * Keep these limits conservative because the drawing is stored in the letter document.
 */
class DrawingCodec(
    val maxJsonBytes: Int = DEFAULT_MAX_JSON_BYTES,
    private val maxStrokes: Int = DEFAULT_MAX_STROKES,
    private val maxPoints: Int = DEFAULT_MAX_POINTS,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun encode(drawing: DrawingDocument): Result<String> = runCatching {
        validate(drawing)
        val serialized = json.encodeToString(drawing)
        require(serialized.encodeToByteArray().size <= maxJsonBytes) {
            "Рисунок слишком большой. Максимальный размер — ${maxJsonBytes / 1024} КиБ."
        }
        serialized
    }

    /**
     * Encodes the document once and returns both validation result and exact UTF-8 JSON byte size.
     * The size remains available even when persistence limits reject the drawing, so the editor
     * can display useful diagnostics instead of reporting a misleading zero size.
     */
    fun encodeWithSize(drawing: DrawingDocument): Pair<Result<String>, Int> {
        val serializedResult = runCatching { json.encodeToString(drawing) }
        if (serializedResult.isFailure) return serializedResult to 0
        val serialized = serializedResult.getOrThrow()
        val sizeBytes = serialized.encodeToByteArray().size
        val result = runCatching {
            validate(drawing)
            require(sizeBytes <= maxJsonBytes) {
                "Рисунок занимает ${sizeBytes / 1024} КиБ. Максимальный размер JSON — ${maxJsonBytes / 1024} КиБ."
            }
            serialized
        }
        return result to sizeBytes
    }

    /** Returns encoded size without validating persistence limits. */
    fun encodedSizeBytes(drawing: DrawingDocument): Int =
        json.encodeToString(drawing).encodeToByteArray().size

    fun decode(serialized: String): Result<DrawingDocument> = runCatching {
        require(serialized.encodeToByteArray().size <= maxJsonBytes) {
            "Размер JSON превышает допустимый лимит."
        }
        val drawing = json.decodeFromString<DrawingDocument>(serialized)
        validate(drawing)
        drawing
    }.recoverCatching { error ->
        if (error is SerializationException) {
            throw IllegalArgumentException("Не удалось прочитать формат рисунка.", error)
        }
        throw error
    }

    private fun validate(drawing: DrawingDocument) {
        require(drawing.version == DrawingDocument.CURRENT_VERSION) {
            "Неподдерживаемая версия рисунка: ${drawing.version}."
        }
        require(drawing.title.length <= MAX_TITLE_LENGTH) {
            "Заголовок рисунка не должен превышать $MAX_TITLE_LENGTH символов."
        }
        require(drawing.caption.length <= MAX_CAPTION_LENGTH) {
            "Описание рисунка не должно превышать $MAX_CAPTION_LENGTH символов."
        }
        require(drawing.canvas.width in 1..MAX_CANVAS_DIMENSION) {
            "Некорректная ширина холста."
        }
        require(drawing.canvas.height in 1..MAX_CANVAS_DIMENSION) {
            "Некорректная высота холста."
        }
        require(HEX_COLOR.matches(drawing.canvas.backgroundColor)) {
            "Некорректный цвет фона."
        }
        require(drawing.canvas.backgroundMode == "SOLID" || drawing.canvas.backgroundMode == "GRADIENT") {
            "Некорректный режим фона."
        }
        require(
            drawing.canvas.gradientDirection in setOf(
                "LEFT_TO_RIGHT",
                "TOP_TO_BOTTOM",
                "TOP_LEFT_TO_BOTTOM_RIGHT",
                "TOP_RIGHT_TO_BOTTOM_LEFT",
            ),
        ) { "Некорректное направление градиента." }
        require(HEX_COLOR.matches(drawing.canvas.gradientStartColor)) {
            "Некорректный первый цвет градиента."
        }
        require(HEX_COLOR.matches(drawing.canvas.gradientEndColor)) {
            "Некорректный второй цвет градиента."
        }
        require(drawing.canvas.backgroundAlpha.isFinite() && drawing.canvas.backgroundAlpha in 0f..1f) {
            "Некорректная прозрачность фона."
        }
        require(drawing.texts.size <= MAX_ANNOTATIONS) { "Слишком много текстовых элементов." }
        drawing.texts.forEach { annotation ->
            require(annotation.text.isNotBlank() && annotation.text.length <= MAX_TEXT_LENGTH) {
                "Текст должен содержать от 1 до $MAX_TEXT_LENGTH символов."
            }
            require(HEX_COLOR.matches(annotation.color)) { "Некорректный цвет текста." }
            require(annotation.x.isFinite() && annotation.x in 0.0..1.0 &&
                annotation.y.isFinite() && annotation.y in 0.0..1.0) {
                "Координаты текста должны находиться в диапазоне от 0 до 1."
            }
            require(annotation.fontSize.isFinite() && annotation.fontSize in MIN_TEXT_SIZE..MAX_TEXT_SIZE) {
                "Некорректный размер шрифта."
            }
            require(annotation.rotationDegrees.isFinite() && annotation.rotationDegrees in -360f..360f) {
                "Некорректный угол текста."
            }
            require(annotation.alpha.isFinite() && annotation.alpha in 0f..1f) {
                "Некорректная прозрачность текста."
            }
        }
        require(drawing.stickers.size <= MAX_ANNOTATIONS) { "Слишком много смайликов." }
        drawing.stickers.forEach { sticker ->
            require(sticker.code in STICKER_CODES) { "Неизвестный код смайлика." }
            require(sticker.x.isFinite() && sticker.x in 0.0..1.0 &&
                sticker.y.isFinite() && sticker.y in 0.0..1.0) {
                "Координаты смайлика должны находиться в диапазоне от 0 до 1."
            }
            require(sticker.scale.isFinite() && sticker.scale in MIN_STICKER_SCALE..MAX_STICKER_SCALE) {
                "Некорректный масштаб смайлика."
            }
            require(sticker.rotationDegrees.isFinite() && sticker.rotationDegrees in -360f..360f) {
                "Некорректный угол смайлика."
            }
        }
        require(drawing.strokes.size <= maxStrokes) {
            "Слишком много штрихов."
        }

        var pointCount = 0
        drawing.strokes.forEach { stroke ->
            require(HEX_COLOR.matches(stroke.color)) { "Некорректный цвет штриха." }
            require(stroke.width.isFinite() && stroke.width in MIN_STROKE_WIDTH..MAX_STROKE_WIDTH) {
                "Некорректная толщина кисти."
            }
            require(stroke.alpha.isFinite() && stroke.alpha in 0f..1f) {
                "Некорректная прозрачность штриха."
            }
            require(stroke.points.isNotEmpty()) { "Штрих должен содержать точки." }
            stroke.points.forEach { point ->
                require(point.size == 2) { "Точка должна содержать координаты x и y." }
                require(point.all { it.isFinite() && it in 0.0..1.0 }) {
                    "Координаты должны находиться в диапазоне от 0 до 1."
                }
                pointCount++
                require(pointCount <= maxPoints) { "Слишком много точек на рисунке." }
            }
        }
    }

    private companion object {
        // Detailed edge-map vector drawings may use more points than hand-drawn sketches.
        // Keep the drawing JSON below the 250 KiB app limit and Firestore's document-size budget.
        const val DEFAULT_MAX_JSON_BYTES = 250 * 1024
        const val DEFAULT_MAX_STROKES = 120_000
        const val DEFAULT_MAX_POINTS = 500_000
        const val MAX_CANVAS_DIMENSION = 4_096
        const val MAX_TITLE_LENGTH = 20
        const val MAX_CAPTION_LENGTH = 80
        const val MAX_TEXT_LENGTH = 240
        const val MAX_ANNOTATIONS = 100
        const val MIN_TEXT_SIZE = 8f
        const val MAX_TEXT_SIZE = 256f
        const val MIN_STICKER_SCALE = 0.25f
        const val MAX_STICKER_SCALE = 4f
        val STICKER_CODES = setOf(":smile:", ":heart:", ":wink:", ":laugh:", ":sad:", ":cool:")
        const val MIN_STROKE_WIDTH = 0.1f
        const val MAX_STROKE_WIDTH = 100f
        val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
