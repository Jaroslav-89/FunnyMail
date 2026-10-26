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
import com.jaroapps.funnymail.features.drawing.domain.image.NormalizedPhotoPoint
import com.jaroapps.funnymail.features.drawing.domain.image.PortraitFeatureContour
import com.jaroapps.funnymail.features.drawing.domain.image.PortraitFeatures
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

private data class FaceContourSpec(
    val type: Int,
    val closed: Boolean = false,
)

private val portraitFaceContourSpecs = listOf(
    FaceContourSpec(FaceContour.FACE, closed = true),
    FaceContourSpec(FaceContour.LEFT_EYE, closed = true),
    FaceContourSpec(FaceContour.RIGHT_EYE, closed = true),
    FaceContourSpec(FaceContour.LEFT_EYEBROW_TOP),
    FaceContourSpec(FaceContour.RIGHT_EYEBROW_TOP),
    FaceContourSpec(FaceContour.NOSE_BRIDGE),
    FaceContourSpec(FaceContour.NOSE_BOTTOM),
    FaceContourSpec(FaceContour.UPPER_LIP_TOP),
    FaceContourSpec(FaceContour.LOWER_LIP_BOTTOM),
)

/**
 * Runs bundled ML Kit models locally. A confidence mask gives the main person silhouette; ML
 * Kit's face contour detector contributes only named facial contours, not arbitrary texture edges.
 */
actual suspend fun analyzePortraitLocally(
    imageBytes: ByteArray,
    maxDimension: Int,
): PortraitFeatures? {
    require(maxDimension >= 256)
    if (imageBytes.isEmpty()) return null
    val bitmap = runCatching { decodeAnalysisBitmap(imageBytes, maxDimension) }.getOrNull() ?: return null
    val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build(),
    )
    val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build(),
    )
    try {
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val mask = segmenter.process(inputImage).awaitResult()
        val maskWidth = mask.width
        val maskHeight = mask.height
        val maskBuffer = mask.buffer.order(ByteOrder.nativeOrder())
        maskBuffer.rewind()
        val confidence = ByteArray(maskWidth * maskHeight)
        for (index in confidence.indices) {
            confidence[index] = (maskBuffer.getFloat().coerceIn(0f, 1f) * 255f).toInt().toByte()
        }

        val faceContours = try {
            val faces = faceDetector.process(inputImage).awaitResult()
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            face?.let { detectedFace ->
                portraitFaceContourSpecs.mapNotNull { spec ->
                    val points = detectedFace.getContour(spec.type)?.points.orEmpty()
                        .map { point ->
                            NormalizedPhotoPoint(
                                x = (point.x / bitmap.width.toDouble()).coerceIn(0.0, 1.0),
                                y = (point.y / bitmap.height.toDouble()).coerceIn(0.0, 1.0),
                            )
                        }
                    if (points.size < 4) null else {
                        PortraitFeatureContour(points, spec.closed)
                    }
                }
            }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

        return PortraitFeatures(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            maskWidth = maskWidth,
            maskHeight = maskHeight,
            personMask = confidence,
            faceContours = faceContours,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The shared screen reports an import error; this branch has no classical conversion fallback.
        null
    } finally {
        faceDetector.close()
        segmenter.close()
        bitmap.recycle()
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

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }
