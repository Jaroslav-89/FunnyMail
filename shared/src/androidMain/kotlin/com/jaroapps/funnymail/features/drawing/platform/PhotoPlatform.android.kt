package com.jaroapps.funnymail.features.drawing.platform

import android.content.Context
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
import com.jaroapps.funnymail.features.drawing.domain.image.EdgeMap
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
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
    DexiNedRuntime.applicationContext = context.applicationContext
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

/**
 * Attempts local DexiNed inference. The ONNX model is downloaded once, verified, and cached in
 * the app's private files directory. Any load/inference error returns null so the caller can fall
 * back to the classic detector. Photo pixels are never uploaded.
 */
actual suspend fun detectPhotoEdgesWithDexiNed(image: GrayImage): EdgeMap? =
    withContext(Dispatchers.IO) {
        try {
            val context = DexiNedRuntime.applicationContext ?: return@withContext null
            DexiNedRuntime.detect(context, image)?.thresholdAndLink()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

private object DexiNedRuntime {
    @Volatile
    var applicationContext: Context? = null

    private const val MODEL_URL =
        "https://huggingface.co/opencv/edge_detection_dexined/resolve/main/edge_detection_dexined_2024sep.onnx"
    private const val MODEL_NAME = "edge_detection_dexined_2024sep.onnx"
    private const val MODEL_SHA256 =
        "a50d01dc8481549c7dedb9eb3e0123b810a016520df75e4669a504609982cdd0"
    private const val INPUT_SIZE = 512
    private val modelPixelCount = INPUT_SIZE * INPUT_SIZE

    @Volatile
    private var cachedSession: OrtSession? = null

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    fun detect(context: Context, image: GrayImage): EdgeMap {
        val session = synchronized(this) {
            cachedSession ?: run {
                val modelFile = ensureModelFile(context)
                val options = OrtSession.SessionOptions()
                try {
                    options.setIntraOpNumThreads(2)
                    environment.createSession(modelFile.absolutePath, options).also {
                        cachedSession = it
                    }
                } finally {
                    options.close()
                }
            }
        }
        val inputName = session.inputNames.firstOrNull()
            ?: error("DexiNed model has no input")
        val tensorData = FloatArray(3 * modelPixelCount)
        for (channel in 0 until 3) {
            // The shared V10/V11 decode contract is grayscale. Replicate luminance into B/G/R,
            // then use OpenCV Zoo's published DexiNed mean subtraction for each channel.
            val mean = when (channel) {
                0 -> 103.5f
                1 -> 116.2f
                else -> 123.6f
            }
            val channelOffset = channel * modelPixelCount
            for (y in 0 until INPUT_SIZE) {
                val sourceY = (y.toLong() * image.height / INPUT_SIZE).toInt().coerceIn(0, image.height - 1)
                for (x in 0 until INPUT_SIZE) {
                    val sourceX = (x.toLong() * image.width / INPUT_SIZE).toInt().coerceIn(0, image.width - 1)
                    val luminance = image.pixels[sourceY * image.width + sourceX].toFloat()
                    tensorData[channelOffset + y * INPUT_SIZE + x] = luminance - mean
                }
            }
        }

        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(tensorData),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )
        inputTensor.use {
            val outputNames = session.outputNames.toList()
            val result = session.run(mapOf(inputName to inputTensor), outputNames.toSet())
            result.use { outputs ->
                val accum = FloatArray(modelPixelCount)
                var validOutputs = 0
                for (name in outputNames) {
                    val output = outputs.get(name) as? OnnxTensor ?: continue
                    val buffer = output.floatBuffer.duplicate()
                    buffer.rewind()
                    if (buffer.remaining() < modelPixelCount) continue
                    val logits = FloatArray(modelPixelCount)
                    buffer.get(logits)

                    var minValue = Float.POSITIVE_INFINITY
                    var maxValue = Float.NEGATIVE_INFINITY
                    val probabilities = FloatArray(modelPixelCount) { index ->
                        val value = logits[index]
                        val probability = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
                        minValue = min(minValue, probability)
                        maxValue = max(maxValue, probability)
                        probability
                    }
                    val range = (maxValue - minValue).coerceAtLeast(1e-6f)
                    for (index in probabilities.indices) {
                        accum[index] += (probabilities[index] - minValue) / range * 255f
                    }
                    validOutputs++
                }
                check(validOutputs > 0) { "DexiNed model returned no usable edge maps" }

                val confidence512 = ByteArray(modelPixelCount) { index ->
                    (accum[index] / validOutputs).toInt().coerceIn(0, 255).toByte()
                }
                // Map the model confidence back to the photo's decoded dimensions before shared
                // hysteresis and vectorization.
                val restored = ByteArray(image.width * image.height)
                for (y in 0 until image.height) {
                    val sourceY = (y.toLong() * INPUT_SIZE / image.height).toInt().coerceIn(0, INPUT_SIZE - 1)
                    for (x in 0 until image.width) {
                        val sourceX = (x.toLong() * INPUT_SIZE / image.width).toInt().coerceIn(0, INPUT_SIZE - 1)
                        restored[y * image.width + x] = confidence512[sourceY * INPUT_SIZE + sourceX]
                    }
                }
                EdgeMap(image.width, image.height, restored)
            }
        }
    }

    private fun ensureModelFile(context: Context): java.io.File {
        val directory = java.io.File(context.filesDir, "edge-models")
        if (!directory.exists() && !directory.mkdirs()) error("Cannot create model cache directory")
        val target = java.io.File(directory, MODEL_NAME)
        if (target.isFile && target.length() > 40_000_000L) return target

        target.delete()
        val temporary = java.io.File(directory, "$MODEL_NAME.download")
        temporary.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FunnyMail-PhotoSketch/1.0")
        }
        try {
            check(connection.responseCode in 200..299) {
                "DexiNed model download failed: HTTP ${connection.responseCode}"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val checksum = digest.digest().joinToString("") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            check(checksum.equals(MODEL_SHA256, ignoreCase = true)) {
                "DexiNed model checksum mismatch"
            }
            check(temporary.renameTo(target)) { "Cannot move verified model into cache" }
            return target
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }
}

