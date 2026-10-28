# Photo-to-sketch V11

V11 keeps the V10 classical edge detector as the default and adds DexiNed as an optional alternative.

## In the app

- **CV** uses the common Kotlin edge detector from V10.
- **AI** tries the DexiNed ONNX model. If the model cannot be downloaded or loaded, produces no usable paths, or fails drawing-document validation, the import falls back to CV.
- **Карта** opens the intermediate edge map for the last successful import.
- Resulting contours are converted into the existing `DrawingDocument` and remain editable in the drawing editor.

## Model and privacy

The model is downloaded only after the user selects AI and imports a photo. The file is approximately 47.2 MB and is cached in the app's private local storage. The photo is processed locally and is not uploaded by this feature.

Model source: [OpenCV DexiNed ONNX model](https://huggingface.co/opencv/edge_detection_dexined)

- File: `edge_detection_dexined_2024sep.onnx`
- SHA-256: `a50d01dc8481549c7dedb9eb3e0123b810a016520df75e4669a504609982cdd0`
- License: MIT, as stated in the model repository.

The first AI run requires an internet connection to download the model. Cached model bytes are checked against the SHA-256 before reuse; an incorrect or incomplete cache is removed and downloaded again.

## Implementation outline

1. The platform decoder produces a temporary grayscale image.
2. DexiNed receives a 512 × 512 NCHW tensor with B/G/R grayscale channels and the published mean subtraction values (103.5, 116.2, 123.6).
3. Side-output predictions are sigmoid-activated, normalized, and averaged into an edge-confidence map.
4. Shared Kotlin hysteresis thresholding links weak edge responses to strong ones.
5. The shared vectorizer converts edge paths into normalized editable drawing strokes.

The detector and vectorizer are separate so the classical baseline remains independently testable and can be compared against the neural map.
