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
        require(drawing.strokes.size <= maxStrokes) {
            "Слишком много штрихов."
        }

        var pointCount = 0
        drawing.strokes.forEach { stroke ->
            require(HEX_COLOR.matches(stroke.color)) { "Некорректный цвет штриха." }
            require(stroke.width.isFinite() && stroke.width in MIN_STROKE_WIDTH..MAX_STROKE_WIDTH) {
                "Некорректная толщина кисти."
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
        // Leave roughly 256 KiB for the surrounding letter/message fields in Firestore.
        const val DEFAULT_MAX_JSON_BYTES = 900 * 1024
        const val DEFAULT_MAX_STROKES = 120_000
        const val DEFAULT_MAX_POINTS = 500_000
        const val MAX_CANVAS_DIMENSION = 4_096
        const val MIN_STROKE_WIDTH = 0.1f
        const val MAX_STROKE_WIDTH = 100f
        val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
