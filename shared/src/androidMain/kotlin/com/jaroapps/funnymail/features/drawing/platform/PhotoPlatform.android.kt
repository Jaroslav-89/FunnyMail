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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * Decodes an orientation-corrected, bounded photo and returns a temporary grayscale pixel buffer.
 * No image bytes or intermediate pixels are persisted.
 */
actual suspend fun decodePhotoToGrayscale(
    imageBytes: ByteArray,
    maxDimension: Int,
): GrayImage? {
    if (imageBytes.isEmpty() || maxDimension < 64) return null
    return withContext(Dispatchers.Default) {
        val bitmap = runCatching { decodeAnalysisBitmap(imageBytes, maxDimension) }.getOrNull()
            ?: return@withContext null
        try {
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val grayscale = IntArray(argb.size) { index ->
                val color = argb[index]
                val red = (color shr 16) and 0xFF
                val green = (color shr 8) and 0xFF
                val blue = color and 0xFF
                (299 * red + 587 * green + 114 * blue + 500) / 1_000
            }
            GrayImage(bitmap.width, bitmap.height, grayscale)
        } finally {
            bitmap.recycle()
        }
    }
}

private fun decodeAnalysisBitmap(imageBytes: ByteArray, maxDimension: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
    require(bounds.outWidth >= 3 && bounds.outHeight >= 3) { "Unsupported image format" }

    var sampleSize = 1
    while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension &&
        sampleSize < (1 shl 15)
    ) {
        sampleSize *= 2
    }
    val sampled = BitmapFactory.decodeByteArray(
        imageBytes,
        0,
        imageBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: error("Unsupported image format")

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

    val oriented = if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) sampled else Bitmap.createBitmap(
        sampled, 0, 0, sampled.width, sampled.height, matrix, true,
    ).also { transformed ->
        if (transformed !== sampled) sampled.recycle()
    }
    val factor = minOf(1f, maxDimension.toFloat() / max(oriented.width, oriented.height).toFloat())
    val targetWidth = (oriented.width * factor).roundToInt().coerceAtLeast(3)
    val targetHeight = (oriented.height * factor).roundToInt().coerceAtLeast(3)
    return if (targetWidth != oriented.width || targetHeight != oriented.height) {
        Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true).also {
            if (it !== oriented) oriented.recycle()
        }
    } else oriented
}

