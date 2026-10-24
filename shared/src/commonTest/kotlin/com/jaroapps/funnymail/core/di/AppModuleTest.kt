package com.jaroapps.funnymail.core.di

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.usecase.DecodeDrawingUseCase
import com.jaroapps.funnymail.features.drawing.domain.usecase.EncodeDrawingUseCase
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.koin.dsl.koinApplication

class AppModuleTest {
    @Test
    fun drawingServicesAreRegisteredAndCodecIsASingleton() {
        val application = koinApplication {
            modules(appModule)
        }

        try {
            val firstCodec = application.koin.get<DrawingCodec>()
            val secondCodec = application.koin.get<DrawingCodec>()

            assertSame(firstCodec, secondCodec)
            assertNotNull(application.koin.get<EncodeDrawingUseCase>())
            assertNotNull(application.koin.get<DecodeDrawingUseCase>())
        } finally {
            application.close()
        }
    }
}
