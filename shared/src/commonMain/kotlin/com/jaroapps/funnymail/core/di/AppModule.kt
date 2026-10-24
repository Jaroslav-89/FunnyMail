package com.jaroapps.funnymail.core.di

import com.jaroapps.funnymail.features.auth.data.FirebaseAuthRepository
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthViewModel
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.usecase.DecodeDrawingUseCase
import com.jaroapps.funnymail.features.drawing.domain.usecase.EncodeDrawingUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shared dependency graph.
 *
 * Platform services and repository implementations can be added through modules as
 * Firestore and Room adapters are introduced.
 */
val appModule = module {
    single { DrawingCodec() }
    factory { EncodeDrawingUseCase(get()) }
    factory { DecodeDrawingUseCase(get()) }

    single<AuthRepository> { FirebaseAuthRepository() }
    viewModel { AuthViewModel(get()) }
}
