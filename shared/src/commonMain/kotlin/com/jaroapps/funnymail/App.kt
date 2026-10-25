package com.jaroapps.funnymail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.jaroapps.funnymail.core.di.appModule
import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.core.navigation.AppDestination
import com.jaroapps.funnymail.core.storage.DrawingDraftStorage
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.presentation.ChatDetailsScreen
import com.jaroapps.funnymail.features.drawing.data.local.LocalDrawingDatabaseBuilder
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthScreen
import com.jaroapps.funnymail.features.chat.presentation.ChatsScreen
import com.jaroapps.funnymail.features.drawing.presentation.DrawingScreen
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.letter.presentation.LettersScreen
import com.jaroapps.funnymail.features.placeholder.presentation.PlaceholderScreen
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    val firebaseConfigured = remember { FirebaseRuntimeStatus.initialize() }
    var lastDraftJson by remember {
        mutableStateOf(
            DrawingDraftStorage.load()?.let { saved ->
                if (DrawingCodec().decode(saved).isSuccess) saved
                else {
                    DrawingDraftStorage.clear()
                    null
                }
            },
        )
    }

    KoinApplication(
        configuration = koinConfiguration { modules(appModule) },
    ) {
        FunnyMailContent(
            firebaseConfigured = firebaseConfigured,
            hasDraft = lastDraftJson != null,
            draftJson = lastDraftJson,
            onDraftPrepared = { json ->
                DrawingDraftStorage.save(json)
                lastDraftJson = json
            },
            onClearDraft = {
                DrawingDraftStorage.clear()
                lastDraftJson = null
            },
        )
    }
}

@Composable
private fun FunnyMailContent(
    firebaseConfigured: Boolean,
    hasDraft: Boolean,
    draftJson: String?,
    onDraftPrepared: (String) -> Unit,
    onClearDraft: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    val localDrawingRepository = koinInject<LocalDrawingRepository>()
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    AppDestination.Home -> HomeScreen(
                        onCreateLetter = { destination = AppDestination.Drawing },
                        onOpenLetters = { destination = AppDestination.Letters },
                        onOpenChats = { destination = AppDestination.Chats },
                        onOpenAccount = { destination = AppDestination.Auth },
                        hasDraft = hasDraft,
                    )
                    AppDestination.Auth -> AuthScreen(
                        firebaseConfigured = firebaseConfigured,
                        onBack = { destination = AppDestination.Home },
                    )
                    AppDestination.Drawing -> DrawingScreen(
                        onBack = { destination = AppDestination.Home },
                        onPrepared = { json, reportSaveResult ->
                            coroutineScope.launch {
                                try {
                                    localDrawingRepository.saveDrawing(json)
                                    onDraftPrepared(json)
                                    reportSaveResult(null)
                                    destination = AppDestination.Home
                                } catch (error: Exception) {
                                    reportSaveResult(
                                        error.message ?: "Не удалось сохранить рисунок в галерею.",
                                    )
                                }
                            }
                        },
                    )
                    AppDestination.Letters -> LettersScreen(
                        draftJson = draftJson,
                        onBack = { destination = AppDestination.Home },
                        onClearDraft = onClearDraft,
                    )
                    AppDestination.Chats -> ChatsScreen(
                        onBack = { destination = AppDestination.Home },
                        onOpenAccount = { destination = AppDestination.Auth },
                        onOpenChat = { chat ->
                            selectedChat = chat
                            destination = AppDestination.ChatDetail
                        },
                    )
                    AppDestination.ChatDetail -> {
                        val chat = selectedChat
                        if (chat == null) {
                            PlaceholderScreen(
                                title = "Чат не выбран",
                                description = "Вернитесь к списку чатов и выберите чат.",
                                onBack = { destination = AppDestination.Chats },
                            )
                        } else {
                            ChatDetailsScreen(
                                chat = chat,
                                draftJson = draftJson,
                                onBack = { destination = AppDestination.Chats },
                                onDraftSent = onClearDraft,
                            )
                        }
                    }
                }
            }
        }
    }
}
