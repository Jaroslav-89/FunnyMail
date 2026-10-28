package com.jaroapps.funnymail.features.drawing.domain.image

/**
 * Platform bridge for the optional DexiNed ONNX runtime.
 *
 * The classic detector remains available in commonMain. iOS registers an implementation from its
 * SwiftUI entry point because ONNX Runtime is provided there by Swift Package Manager.
 */
interface NeuralEdgeInferenceProvider {
    fun detect(image: GrayImage): EdgeMap?
}

/** Registry used only by the iOS Swift/ONNX bridge; Android owns its runtime in androidMain. */
object NeuralEdgeInferenceProviderRegistry {
    var provider: NeuralEdgeInferenceProvider? = null
}
