package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.LocalUIViewController
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import com.jaroapps.funnymail.features.drawing.domain.image.EdgeMap
import com.jaroapps.funnymail.features.drawing.domain.image.NeuralEdgeInferenceProviderRegistry
import kotlinx.coroutines.CancellationException
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
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
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
 * Creates an oriented, bounded grayscale copy for the shared edge detector.
 * The source photo and temporary pixel buffer remain in memory only.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun decodePhotoToGrayscale(
    imageBytes: ByteArray,
    maxDimension: Int,
): GrayImage? {
    if (imageBytes.isEmpty() || maxDimension < 64) return null
    val cgImage = withContext(Dispatchers.Main) {
        val data = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.convert())
        }
        val original = UIImage(data = data) ?: return@withContext null
        val originalCGImage = original.CGImage ?: return@withContext null
        val sourceWidth = CGImageGetWidth(originalCGImage).toInt().coerceAtLeast(3)
        val sourceHeight = CGImageGetHeight(originalCGImage).toInt().coerceAtLeast(3)
        val factor = minOf(1.0, maxDimension.toDouble() / max(sourceWidth, sourceHeight).toDouble())
        val width = (sourceWidth * factor).roundToInt().coerceAtLeast(3)
        val height = (sourceHeight * factor).roundToInt().coerceAtLeast(3)

        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(width.toDouble(), height.toDouble()),
            true,
            1.0,
        )
        val normalized = try {
            // The picker exports a normalized JPEG, and drawing again ensures bounded orientation.
            original.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        } ?: return@withContext null
        normalized.CGImage
    } ?: return null

    return withContext(Dispatchers.Default) {
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            data = null,
            width = width.convert(),
            height = height.convert(),
            bitsPerComponent = 8.convert(),
            bytesPerRow = (width * 4).convert(),
            space = colorSpace,
            // RGBA bytes: premultiplied-last alpha with big-endian 32-bit channel order.
            bitmapInfo = 0x4001u,
        ) ?: run {
            CGColorSpaceRelease(colorSpace)
            return@withContext null
        }
        try {
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
            val pixels = CGBitmapContextGetData(context)?.reinterpret<UByteVar>()
                ?: return@withContext null
            val grayscale = IntArray(width * height) { index ->
                val offset = index * 4
                val red = pixels[offset].toInt()
                val green = pixels[offset + 1].toInt()
                val blue = pixels[offset + 2].toInt()
                (299 * red + 587 * green + 114 * blue + 500) / 1_000
            }
            GrayImage(width, height, grayscale)
        } finally {
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
    }
}


/**
 * Uses the Swift Package Manager ONNX Runtime bridge when the app has registered it.
 * If the optional model isn't cached or cannot be downloaded, return null and keep the
 * shared classic detector as a fallback.
 */
actual suspend fun detectPhotoEdgesWithDexiNed(image: GrayImage): EdgeMap? =
    withContext(Dispatchers.Default) {
        try {
            NeuralEdgeInferenceProviderRegistry.provider
                ?.detect(image)
                ?.thresholdAndLink()
                ?.takeIf { it.edgeCount() >= 2 }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
