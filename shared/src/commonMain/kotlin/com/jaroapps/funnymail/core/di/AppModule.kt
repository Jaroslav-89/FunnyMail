package com.jaroapps.funnymail.core.di

import com.jaroapps.funnymail.features.auth.data.FirebaseAuthRepository
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthViewModel
import com.jaroapps.funnymail.features.chat.data.FirebaseChatRepository
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.chat.presentation.ChatViewModel
import com.jaroapps.funnymail.features.chat.presentation.ChatDetailsViewModel
import com.jaroapps.funnymail.features.drawing.data.local.LocalDrawingDatabase
import com.jaroapps.funnymail.features.drawing.data.local.RoomLocalDrawingRepository
import com.jaroapps.funnymail.features.drawing.data.local.buildLocalDrawingDatabase
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.drawing.domain.usecase.DecodeDrawingUseCase
import com.jaroapps.funnymail.features.drawing.domain.usecase.EncodeDrawingUseCase
import com.jaroapps.funnymail.features.letter.data.FirebaseLetterRepository
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Shared dependency graph for local drawing storage and Firebase features. */
val appModule = module {
    single { DrawingCodec() }
    factory { EncodeDrawingUseCase(get()) }
    factory { DecodeDrawingUseCase(get()) }

    single<LocalDrawingDatabase> { buildLocalDrawingDatabase() }
    single<LocalDrawingRepository> {
        RoomLocalDrawingRepository(
            dao = get<LocalDrawingDatabase>().localDrawingDao(),
            drawingCodec = get(),
        )
    }

    single<AuthRepository> { FirebaseAuthRepository() }
    single<ChatRepository> { FirebaseChatRepository() }
    single<LetterRepository> { FirebaseLetterRepository(drawingCodec = get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { ChatDetailsViewModel(get(), get()) }
}
