package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import com.jaroapps.funnymail.features.drawing.domain.image.GrayImage

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

/** Decode, normalize orientation and downsample the image into grayscale pixels. */
expect suspend fun decodeImageToGrayscale(imageBytes: ByteArray, maxDimension: Int): GrayImage
