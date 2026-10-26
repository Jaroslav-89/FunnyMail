package com.jaroapps.funnymail.features.drawing.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

interface PhotoPickerLauncher {
    fun openGallery()
    fun openCamera()
}

/** Returns encoded bytes temporarily for tracing; the caller keeps them in memory only. */
@Composable
expect fun rememberPhotoPicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: () -> Unit,
): PhotoPickerLauncher

/**
 * Decodes and bounds a chosen image for the tracing overlay. The returned bitmap is transient
 * UI state only and is never included in a DrawingDocument.
 */
expect fun decodePhotoForTracing(imageBytes: ByteArray, maxDimension: Int): ImageBitmap?
