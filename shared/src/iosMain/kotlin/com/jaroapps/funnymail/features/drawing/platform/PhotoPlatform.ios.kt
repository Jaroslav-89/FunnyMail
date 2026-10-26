package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.LocalUIViewController
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
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
        onFinished()
        val data = image?.let { UIImageJPEGRepresentation(it, 0.9) }
        val length = data?.length?.toInt() ?: 0
        val bytes = if (data != null && length > 0) {
            data.bytes?.reinterpret<kotlinx.cinterop.ByteVar>()?.readBytes(length)
        } else null
        if (bytes.isNullOrEmpty()) onError() else onPicked(bytes)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onFinished()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun decodeImageToGrayscale(imageBytes: ByteArray, maxDimension: Int): GrayImage =
    withContext(Dispatchers.Main) {
    // UIKit image decoding/drawing runs on the main queue; contour detection returns to Default.
    require(imageBytes.isNotEmpty()) { "Empty image data" }
    require(maxDimension >= 3)
    val data = imageBytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.convert())
    }
    val image = UIImage(data = data) ?: error("Unsupported image format")
    val imageWidth = image.size.useContents { width }.roundToInt().coerceAtLeast(3)
    val imageHeight = image.size.useContents { height }.roundToInt().coerceAtLeast(3)
    val factor = minOf(1.0, maxDimension.toDouble() / max(imageWidth, imageHeight).toDouble())
    val width = (imageWidth * factor).roundToInt().coerceAtLeast(3)
    val height = (imageHeight * factor).roundToInt().coerceAtLeast(3)

    // Drawing UIImage into a new context normalizes EXIF/UIImage orientation before edge detection.
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0)
    val normalized = try {
        image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        UIGraphicsGetImageFromCurrentImageContext() ?: error("Could not normalize image orientation")
    } finally {
        UIGraphicsEndImageContext()
    }
    val cgImage = normalized.CGImage ?: error("Could not decode image pixels")
    val raw = ByteArray(width * height * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    raw.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.convert(),
            height = height.convert(),
            bitsPerComponent = 8.convert(),
            bytesPerRow = (width * 4).convert(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: error("Could not allocate image pixel buffer")
        CGContextTranslateCTM(context, 0.0, height.toDouble())
        CGContextScaleCTM(context, 1.0, -1.0)
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
        CGContextRelease(context)
    }
    val pixels = IntArray(width * height) { index ->
        val offset = index * 4
        val red = raw[offset].toInt() and 0xFF
        val green = raw[offset + 1].toInt() and 0xFF
        val blue = raw[offset + 2].toInt() and 0xFF
        (red * 299 + green * 587 + blue * 114) / 1_000
    }
    GrayImage(width, height, pixels)
}
