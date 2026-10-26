package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
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
        // Normalize EXIF/UIImage orientation and bound memory before returning transient bytes.
        val bytes = image?.toBoundedJpeg(maxDimension = 2_048)
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

actual fun decodePhotoForTracing(
    imageBytes: ByteArray,
    maxDimension: Int,
): ImageBitmap? {
    if (imageBytes.isEmpty() || maxDimension < 3) return null
    // PhotoPickerDelegate already normalizes orientation and limits the longer edge to maxDimension.
    return runCatching {
        Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
    }.getOrNull()
}
