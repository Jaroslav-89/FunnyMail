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
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthScreen
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.chat.presentation.ChatsScreen
import com.jaroapps.funnymail.features.drawing.presentation.DrawingScreen
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import com.jaroapps.funnymail.features.letter.presentation.LettersScreen
import com.jaroapps.funnymail.features.placeholder.presentation.PlaceholderScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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
    val authRepository = koinInject<AuthRepository>()
    val letterRepository = koinInject<LetterRepository>()
    val coroutineScope = rememberCoroutineScope()

    val sendDrawingToChat: (String, (String?) -> Unit) -> Unit = { json, reportResult ->
        val chatId = selectedChat?.id
        if (chatId == null) {
            reportResult("Сначала откройте чат, в который нужно отправить рисунок.")
        } else {
            coroutineScope.launch {
                try {
                    val user = authRepository.currentUser.first()
                    if (user == null) {
                        reportResult("Войдите в аккаунт, чтобы отправлять рисунки.")
                    } else {
                        val drawing = DrawingCodec().decode(json).getOrElse { error ->
                            reportResult(error.message ?: "Не удалось прочитать рисунок.")
                            return@launch
                        }
                        letterRepository.sendLetter(
                            chatId = chatId,
                            authorUserId = user.id,
                            drawing = drawing,
                        ).fold(
                            onSuccess = {
                                reportResult(null)
                                if (destination == AppDestination.Drawing) {
                                    destination = AppDestination.ChatDetail
                                }
                            },
                            onFailure = { error ->
                                reportResult(error.message ?: "Не удалось отправить рисунок в чат.")
                            },
                        )
                    }
                } catch (error: Exception) {
                    reportResult(error.message ?: "Не удалось отправить рисунок в чат.")
                }
            }
        }
    }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    AppDestination.Home -> HomeScreen(
                        onOpenChats = { destination = AppDestination.Chats },
                        onOpenAccount = { destination = AppDestination.Auth },
                        hasDraft = hasDraft,
                    )
                    AppDestination.Auth -> AuthScreen(
                        firebaseConfigured = firebaseConfigured,
                        onBack = { destination = AppDestination.Home },
                    )
                    AppDestination.Drawing -> DrawingScreen(
                        onBack = { destination = AppDestination.ChatDetail },
                        onSaveDrawing = { json, reportSaveResult ->
                            coroutineScope.launch {
                                try {
                                    localDrawingRepository.saveDrawing(json)
                                    reportSaveResult(null)
                                } catch (error: Exception) {
                                    reportSaveResult(
                                        error.message ?: "Не удалось сохранить рисунок в галерею.",
                                    )
                                }
                            }
                        },
                        onSendDrawing = sendDrawingToChat,
                    )
                    AppDestination.Letters -> LettersScreen(
                        draftJson = draftJson,
                        onBack = { destination = AppDestination.ChatDetail },
                        onClearDraft = onClearDraft,
                        onSendDrawing = sendDrawingToChat,
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
                                onCreateDrawing = { destination = AppDestination.Drawing },
                                onOpenDrawings = { destination = AppDestination.Letters },
                            )
                        }
                    }
                }
            }
        }
    }
}
