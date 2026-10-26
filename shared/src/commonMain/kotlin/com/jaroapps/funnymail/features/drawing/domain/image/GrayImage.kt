package com.jaroapps.funnymail.features.drawing.domain.image

/** Transient, downsampled grayscale pixels; never persisted or serialized. */
data class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width >= 3 && height >= 3) { "Изображение слишком маленькое для поиска контуров." }
        require(pixels.size == width * height) { "Некорректный буфер изображения." }
    }
}
