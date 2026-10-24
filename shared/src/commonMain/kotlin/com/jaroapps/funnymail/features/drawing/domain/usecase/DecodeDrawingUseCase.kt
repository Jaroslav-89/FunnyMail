package com.jaroapps.funnymail.features.drawing.domain.usecase

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument

/** Parses and validates persisted drawing JSON before displaying a letter. */
class DecodeDrawingUseCase(
    private val drawingCodec: DrawingCodec,
) {
    operator fun invoke(json: String): Result<DrawingDocument> =
        drawingCodec.decode(json)
}
