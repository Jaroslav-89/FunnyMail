package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.LocalUIViewController
import com.jaroapps.funnymail.features.drawing.domain.image.NormalizedPhotoPoint
import com.jaroapps.funnymail.features.drawing.domain.image.PortraitFeatureContour
import com.jaroapps.funnymail.features.drawing.domain.image.PortraitFeatures
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelFormatType_OneComponent8
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIViewController
import platform.Vision.VNDetectFaceLandmarksRequest
import platform.Vision.VNFaceLandmarkRegion2D
import platform.Vision.VNFaceObservation
import platform.Vision.VNGeneratePersonSegmentationRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNPixelBufferObservation
import platform.Vision.VNRequest
import platform.darwin.NSObject
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
actual fun rememberPhotoPicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: () -> Unit,
): PhotoPickerLauncher {
    val viewController = LocalUIViewController.current
    val latestPicked = rememberUpdatedState(onImagePicked)
    val latestError = rememberUpdatedState(onError)
    return remember(viewController) {
        IosPhotoPickerLauncher(
            viewController = viewController,
            onImagePicked = { latestPicked.value(it) },
            onError = { latestError.value() },
        )
    }
}

private class IosPhotoPickerLauncher(
    private val viewController: UIViewController,
    private val onImagePicked: (ByteArray) -> Unit,
    private val onError: () -> Unit,
) : PhotoPickerLauncher {
    private var retainedDelegate: PhotoPickerDelegate? = null

    override fun openGallery() {
        present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)
    }

    override fun openCamera() {
        present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
    }

    private fun present(sourceType: UIImagePickerControllerSourceType) {
        if (!UIImagePickerController.isSourceTypeAvailable(sourceType)) {
            onError()
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = sourceType
        val delegate = PhotoPickerDelegate(
            onPicked = onImagePicked,
            onError = onError,
            onFinished = { retainedDelegate = null },
        )
        // UIKit keeps delegate references weakly, so retain it until the picker is dismissed.
        retainedDelegate = delegate
        picker.delegate = delegate
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val onPicked: (ByteArray) -> Unit,
    private val onError: () -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        // Downsample immediately, so a full-resolution camera image is never retained as a byte array.
        val bytes = image?.toBoundedJpeg(maxDimension = 1024)
        if (bytes.isNullOrEmpty()) onError() else onPicked(bytes)
        onFinished()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onFinished()
    }
}


@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toBoundedJpeg(maxDimension: Int): ByteArray? {
    require(maxDimension >= 3)
    val sourceWidth = size.useContents { width }.roundToInt().coerceAtLeast(3)
    val sourceHeight = size.useContents { height }.roundToInt().coerceAtLeast(3)
    val factor = minOf(1.0, maxDimension.toDouble() / max(sourceWidth, sourceHeight).toDouble())
    val targetWidth = (sourceWidth * factor).roundToInt().coerceAtLeast(3)
    val targetHeight = (sourceHeight * factor).roundToInt().coerceAtLeast(3)

    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()),
        true,
        1.0,
    )
    val thumbnail = try {
        drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
        UIGraphicsGetImageFromCurrentImageContext()
    } finally {
        UIGraphicsEndImageContext()
    } ?: return null
    val data = UIImageJPEGRepresentation(thumbnail, 0.9) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return null
    return data.bytes?.reinterpret<kotlinx.cinterop.ByteVar>()?.readBytes(length)
}

/**
 * Uses Apple's on-device Vision pipeline on iOS: person segmentation provides the large outer
 * silhouette and face landmarks provide selected semantic contours (face, eyes, brows, nose, lips).
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun analyzePortraitLocally(
    imageBytes: ByteArray,
    maxDimension: Int,
): PortraitFeatures? {
    if (imageBytes.isEmpty() || maxDimension < 256) return null
    val normalizedImage = withContext(Dispatchers.Main) {
        val data = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.convert())
        }
        val original = UIImage(data = data) ?: return@withContext null
        val sourceWidth = original.size.useContents { width }.roundToInt().coerceAtLeast(3)
        val sourceHeight = original.size.useContents { height }.roundToInt().coerceAtLeast(3)
        val scale = minOf(1.0, maxDimension.toDouble() / max(sourceWidth, sourceHeight).toDouble())
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(3)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(3)
        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(width.toDouble(), height.toDouble()),
            true,
            1.0,
        )
        try {
            // Drawing the UIImage normalizes its orientation before passing it to Vision.
            original.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        }
    } ?: return null

    return withContext(Dispatchers.Default) {
        runCatching { analyzeWithAppleVision(normalizedImage) }.getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun analyzeWithAppleVision(image: UIImage): PortraitFeatures? {
    val cgImage = image.CGImage ?: return null
    val sourceWidth = CGImageGetWidth(cgImage).toInt().coerceAtLeast(3)
    val sourceHeight = CGImageGetHeight(cgImage).toInt().coerceAtLeast(3)

    val segmentationRequest = VNGeneratePersonSegmentationRequest()
    segmentationRequest.outputPixelFormat = kCVPixelFormatType_OneComponent8
    val faceLandmarkRequest = VNDetectFaceLandmarksRequest()
    val handler = VNImageRequestHandler(cgImage = cgImage, options = emptyMap<Any?, Any>())
    val requests: List<VNRequest> = listOf(segmentationRequest, faceLandmarkRequest)
    handler.performRequests(requests = requests, error = null)

    val observation = segmentationRequest.results?.firstOrNull() as? VNPixelBufferObservation
        ?: return null
    val pixelBuffer = observation.pixelBuffer
    CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
    val maskBytes = try {
        val maskWidth = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val maskHeight = CVPixelBufferGetHeight(pixelBuffer).toInt()
        val rowBytes = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
        val base = CVPixelBufferGetBaseAddress(pixelBuffer)?.reinterpret<UByteVar>()
            ?: return null
        ByteArray(maskWidth * maskHeight) { index ->
            val y = index / maskWidth
            val x = index % maskWidth
            base[y * rowBytes + x].toByte()
        }
    } finally {
        CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
    }

    val face = faceLandmarkRequest.results
        ?.maxByOrNull { candidate ->
            candidate.boundingBox.useContents { size.width * size.height }
        }
    val faceContours = if (face == null) {
        emptyList()
    } else {
        val bounds = face.boundingBox.useContents {
            doubleArrayOf(origin.x, origin.y, size.width, size.height)
        }
        val landmarks = face.landmarks
        listOfNotNull(
            appleVisionContour(landmarks?.faceContour, bounds, true),
            appleVisionContour(landmarks?.leftEye, bounds, true),
            appleVisionContour(landmarks?.rightEye, bounds, true),
            appleVisionContour(landmarks?.leftEyebrow, bounds, false),
            appleVisionContour(landmarks?.rightEyebrow, bounds, false),
            appleVisionContour(landmarks?.nose, bounds, false),
            appleVisionContour(landmarks?.noseCrest, bounds, false),
            appleVisionContour(landmarks?.outerLips, bounds, true),
            appleVisionContour(landmarks?.innerLips, bounds, true),
        )
    }

    return PortraitFeatures(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        maskWidth = CVPixelBufferGetWidth(pixelBuffer).toInt(),
        maskHeight = CVPixelBufferGetHeight(pixelBuffer).toInt(),
        personMask = maskBytes,
        faceContours = faceContours,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun appleVisionContour(
    region: VNFaceLandmarkRegion2D?,
    faceBounds: DoubleArray,
    closed: Boolean,
): PortraitFeatureContour? {
    if (region == null || region.pointCount.toInt() < 2) return null
    val rawPoints = region.normalizedPoints ?: return null
    val points = ArrayList<NormalizedPhotoPoint>(region.pointCount.toInt())
    for (index in 0 until region.pointCount.toInt()) {
        val point = rawPoints[index]
        val normalizedX = faceBounds[0] + point.x.toDouble() * faceBounds[2]
        val normalizedY = 1.0 - (faceBounds[1] + point.y.toDouble() * faceBounds[3])
        points += NormalizedPhotoPoint(
            x = normalizedX.coerceIn(0.0, 1.0),
            y = normalizedY.coerceIn(0.0, 1.0),
        )
    }
    return PortraitFeatureContour(points, closed)
}
