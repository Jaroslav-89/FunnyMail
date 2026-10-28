package com.jaroapps.funnymail.features.drawing.domain.image

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument

/**
 * Photo-to-vector pipeline. Edge detection and vectorization are separate so the current classic
 * baseline can be compared with a future neural EdgeMap producer without changing the editor.
 */
class PhotoToSketchConverter(
    private val edgeDetector: PhotoEdgeDetector = PhotoEdgeDetector(),
    private val vectorizer: EdgeMapVectorizer = EdgeMapVectorizer(),
) {
    fun detectEdges(image: GrayImage): EdgeMap = edgeDetector.detect(image)

    fun vectorize(edgeMap: EdgeMap): DrawingDocument = vectorizer.convert(edgeMap)

    fun convert(image: GrayImage): DrawingDocument = vectorize(detectEdges(image))
}
