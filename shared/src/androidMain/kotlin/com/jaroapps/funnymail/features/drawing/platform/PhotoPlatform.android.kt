package com.jaroapps.funnymail.features.drawing.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
actual fun rememberPhotoPicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: () -> Unit,
): PhotoPickerLauncher {
    val context = LocalContext.current
    val latestOnImagePicked by rememberUpdatedState(onImagePicked)
    val latestOnError by rememberUpdatedState(onError)
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected image")
            }.onSuccess(latestOnImagePicked).onFailure { latestOnError() }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val file = cameraFile
        cameraFile = null
        if (captured && file != null) {
            runCatching { file.inputStream().use { it.readBytes() } }
                .onSuccess(latestOnImagePicked)
                .onFailure { latestOnError() }
        }
        // The camera's temporary file is deleted immediately. Only an in-memory bitmap is used
        // for tracing, and that bitmap is not part of the saved drawing document.
        file?.delete()
    }

    return remember(context, galleryLauncher, cameraLauncher) {
        object : PhotoPickerLauncher {
            override fun openGallery() {
                galleryLauncher.launch("image/*")
            }

            override fun openCamera() {
                runCatching {
                    val file = File.createTempFile("funnymail_photo_", ".jpg", context.cacheDir)
                    cameraFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraLauncher.launch(uri)
                }.onFailure {
                    cameraFile?.delete()
                    cameraFile = null
                    latestOnError()
                }
            }
        }
    }
}

actual fun decodePhotoForTracing(
    imageBytes: ByteArray,
    maxDimension: Int,
): ImageBitmap? {
    if (imageBytes.isEmpty() || maxDimension < 3) return null
    val bitmap = runCatching {
        decodeTracingBitmap(imageBytes, maxDimension)
    }.getOrNull() ?: return null
    // Do not recycle this bitmap: Compose draws from it for the lifetime of the tracing overlay.
    return bitmap.asImageBitmap()
}

private fun decodeTracingBitmap(imageBytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
    if (bounds.outWidth < 3 || bounds.outHeight < 3) return null

    var sampleSize = 1
    while (
        max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension &&
        sampleSize < (1 shl 15)
    ) {
        sampleSize *= 2
    }
    val sampled = BitmapFactory.decodeByteArray(
        imageBytes,
        0,
        imageBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null

    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(imageBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }

    val oriented = if (
        orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) {
        sampled
    } else {
        Bitmap.createBitmap(
            sampled, 0, 0, sampled.width, sampled.height, matrix, true,
        ).also { transformed ->
            if (transformed !== sampled) sampled.recycle()
        }
    }

    val factor = minOf(
        1f,
        maxDimension.toFloat() / max(oriented.width, oriented.height).toFloat(),
    )
    val targetWidth = (oriented.width * factor).roundToInt().coerceAtLeast(3)
    val targetHeight = (oriented.height * factor).roundToInt().coerceAtLeast(3)
    return if (targetWidth != oriented.width || targetHeight != oriented.height) {
        Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true).also {
            if (it !== oriented) oriented.recycle()
        }
    } else {
        oriented
    }
}
