package com.jaroapps.funnymail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.chat.presentation.ChatDetailsScreen
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthScreen
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.drawing.presentation.DrawingScreen
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import com.jaroapps.funnymail.features.letter.presentation.LettersScreen
import com.jaroapps.funnymail.features.placeholder.presentation.PlaceholderScreen
import kotlinx.coroutines.CancellationException
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
            draftJson = lastDraftJson,
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
    draftJson: String?,
    onClearDraft: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    val localDrawingRepository = koinInject<LocalDrawingRepository>()
    val authRepository = koinInject<AuthRepository>()
    val signedInUser by authRepository.currentUser.collectAsState(initial = null)
    var returnHomeAfterSignIn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(signedInUser?.id, destination, returnHomeAfterSignIn) {
        if (returnHomeAfterSignIn && signedInUser != null && destination == AppDestination.Auth) {
            returnHomeAfterSignIn = false
            destination = AppDestination.Home
        }
    }
    val chatRepository = koinInject<ChatRepository>()
    val letterRepository = koinInject<LetterRepository>()
    val coroutineScope = rememberCoroutineScope()

    val createInviteForChat: (String, (ChatInvite?, String?) -> Unit) -> Unit = { chatId, reportResult ->
        coroutineScope.launch {
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    reportResult(null, "Войдите в аккаунт, чтобы приглашать друзей.")
                } else {
                    chatRepository.createInvite(chatId, user.id).fold(
                        onSuccess = { invite -> reportResult(invite, null) },
                        onFailure = { error ->
                            reportResult(null, error.message ?: "Не удалось создать приглашение.")
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportResult(null, error.message ?: "Не удалось создать приглашение.")
            }
        }
    }

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
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
                        onAuthorize = {
                            returnHomeAfterSignIn = true
                            destination = AppDestination.Auth
                        },
                        onOpenAccount = {
                            returnHomeAfterSignIn = false
                            destination = AppDestination.Auth
                        },
                        onOpenChat = { chat ->
                            selectedChat = chat
                            destination = AppDestination.ChatDetail
                        },
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
                    AppDestination.ChatDetail -> {
                        val chat = selectedChat
                        if (chat == null) {
                            PlaceholderScreen(
                                title = "Чат не выбран",
                                description = "Вернитесь к списку чатов и выберите чат.",
                                onBack = { destination = AppDestination.Home },
                            )
                        } else {
                            ChatDetailsScreen(
                                chat = chat,
                                draftJson = draftJson,
                                onBack = { destination = AppDestination.Home },
                                onDraftSent = onClearDraft,
                                onCreateDrawing = { destination = AppDestination.Drawing },
                                onOpenDrawings = { destination = AppDestination.Letters },
                                onCreateInvite = createInviteForChat,
                            )
                        }
                    }
                }
            }
        }
    }
}
