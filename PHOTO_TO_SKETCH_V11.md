# Photo-to-sketch V11

V11 keeps the V10 classical edge detector as the default and adds DexiNed as an optional alternative.

## In the app

- **CV** uses the common Kotlin edge detector from V10.
- **AI** uses the DexiNed ONNX model bundled into the installed app. It does not download the model at runtime.
- If model verification/loading fails, DexiNed produces no usable paths, or drawing-document validation fails, the import falls back to CV.
- **Карта** opens the intermediate edge map for the last successful import.
- Resulting contours are converted into the existing `DrawingDocument` and remain editable in the drawing editor.

## Model and packaging

The model is approximately 47.2 MB. It is included as an app resource, so AI inference is available offline after installation. The packaged app will be larger by roughly the model's size before archive compression.

To keep the ONNX binary out of Git history, the model is downloaded **during the build**, not on the user's device:
- Android Gradle build fetches it into `models/`.
- Xcode build fetches the same file into that shared assets location and copies it into the iOS app bundle.
- Both paths verify the SHA-256 and fail the build if the download is incomplete or mismatched.
- Once the model file exists locally and passes verification, ordinary builds reuse it without fetching it again.

The first build on a fresh checkout therefore needs internet access. The installed app does not need internet to run DexiNed.

Model source: [OpenCV DexiNed ONNX model](https://huggingface.co/opencv/edge_detection_dexined)

- File: `edge_detection_dexined_2024sep.onnx`
- SHA-256: `a50d01dc8481549c7dedb9eb3e0123b810a016520df75e4669a504609982cdd0`
- License: MIT, as stated in the model repository.

## Implementation outline

1. The platform decoder produces a temporary grayscale image.
2. DexiNed receives a 512 × 512 NCHW tensor with B/G/R grayscale channels and the published mean subtraction values (103.5, 116.2, 123.6).
3. Side-output predictions are sigmoid-activated, normalized, and averaged into an edge-confidence map.
4. Shared Kotlin hysteresis thresholding links weak edge responses to strong ones.
5. The shared vectorizer converts edge paths into normalized editable drawing strokes.
