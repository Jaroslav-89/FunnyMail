package com.jaroapps.funnymail.features.drawing.domain.usecase

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument

/** Validates and encodes a drawing before it crosses the persistence boundary. */
class EncodeDrawingUseCase(
    private val drawingCodec: DrawingCodec,
) {
    operator fun invoke(drawing: DrawingDocument): Result<String> =
        drawingCodec.encode(drawing)
}
