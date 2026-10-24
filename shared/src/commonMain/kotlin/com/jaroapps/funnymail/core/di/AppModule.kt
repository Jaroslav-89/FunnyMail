package com.jaroapps.funnymail.core.di

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.usecase.DecodeDrawingUseCase
import com.jaroapps.funnymail.features.drawing.domain.usecase.EncodeDrawingUseCase
import org.koin.dsl.module

/**
 * Shared dependency graph.
 *
 * Platform services and repository implementations will be added through modules
 * when Firebase and Room adapters are introduced.
 */
val appModule = module {
    single { DrawingCodec() }
    factory { EncodeDrawingUseCase(get()) }
    factory { DecodeDrawingUseCase(get()) }
}
