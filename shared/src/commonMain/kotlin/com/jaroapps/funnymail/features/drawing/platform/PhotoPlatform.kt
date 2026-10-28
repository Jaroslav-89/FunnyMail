package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage
import com.jaroapps.funnymail.features.drawing.domain.image.EdgeMap

interface PhotoPickerLauncher {
    fun openGallery()
    fun openCamera()
}

/** Returns encoded bytes temporarily for local conversion; implementations do not persist them. */
@Composable
expect fun rememberPhotoPicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: () -> Unit,
): PhotoPickerLauncher

/** Decodes a selected photo into a bounded grayscale buffer for local edge detection. */
expect suspend fun decodePhotoToGrayscale(imageBytes: ByteArray, maxDimension: Int): GrayImage?
/** Tries DexiNed locally. Returns null when the optional runtime/model is unavailable. */
expect suspend fun detectPhotoEdgesWithDexiNed(image: GrayImage): EdgeMap?
