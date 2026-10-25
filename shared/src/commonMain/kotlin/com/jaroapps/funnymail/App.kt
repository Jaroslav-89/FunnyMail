package com.jaroapps.funnymail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import com.jaroapps.funnymail.resources.Res
import com.jaroapps.funnymail.core.di.appModule
import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.core.navigation.AppDestination
import com.jaroapps.funnymail.core.navigation.PlatformBackHandler
import com.jaroapps.funnymail.core.notifications.registerPushToken
import com.jaroapps.funnymail.core.notifications.unregisterPushToken
import com.jaroapps.funnymail.core.storage.DrawingDraftStorage
import com.jaroapps.funnymail.core.storage.ThemePreferenceStorage
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.chat.presentation.ChatDetailsScreen
import com.jaroapps.funnymail.features.chat.presentation.ChatEditScreen
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import com.jaroapps.funnymail.features.auth.presentation.AuthScreen
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.drawing.presentation.DrawingScreen
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import com.jaroapps.funnymail.features.letter.presentation.DrawingViewerScreen
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
    var darkThemeEnabled by rememberSaveable { mutableStateOf(ThemePreferenceStorage.loadDarkTheme()) }
    val inviteLoginRequiredText = stringResource(Res.string.invite_login_required)
    val inviteCreateFailedText = stringResource(Res.string.invite_create_failed)
    val sendDrawingOpenChatFirstText = stringResource(Res.string.send_drawing_open_chat_first)
    val sendDrawingLoginRequiredText = stringResource(Res.string.send_drawing_login_required)
    val readingDrawingFailedText = stringResource(Res.string.reading_drawing_failed_short)
    val sendingDrawingFailedText = stringResource(Res.string.sending_drawing_failed)
    val savingDrawingFailedText = stringResource(Res.string.saving_drawing_failed)
    val chatNotSelectedText = stringResource(Res.string.chat_not_selected)
    val chooseChatHintText = stringResource(Res.string.choose_chat_hint)
    val drawingUnavailableText = stringResource(Res.string.drawing_unavailable)
    val selectDrawingHintText = stringResource(Res.string.select_drawing_hint)
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var viewingDrawing by remember { mutableStateOf<DrawingDocument?>(null) }
    var editingDrawingId by remember { mutableStateOf<Long?>(null) }
    var editingDrawingJson by remember { mutableStateOf<String?>(null) }
    var drawingReturnDestination by remember { mutableStateOf(AppDestination.ChatDetail) }
    val localDrawingRepository = koinInject<LocalDrawingRepository>()
    val authRepository = koinInject<AuthRepository>()
    val signedInUser by authRepository.currentUser.collectAsState(initial = null)
    var pushTokenUserId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(signedInUser?.id, firebaseConfigured) {
        val nextUserId = signedInUser?.id
        val previousUserId = pushTokenUserId
        if (previousUserId != null && previousUserId != nextUserId) {
            try {
                unregisterPushToken(previousUserId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Token cleanup is best-effort; the server also removes invalid tokens.
            }
        }
        if (nextUserId != null && firebaseConfigured) {
            try {
                registerPushToken(nextUserId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep sign-in and chat access available if push permissions are denied.
            }
        }
        pushTokenUserId = nextUserId
    }
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

    val closeDrawing: () -> Unit = {
        destination = drawingReturnDestination
        editingDrawingId = null
        editingDrawingJson = null
        drawingReturnDestination = AppDestination.ChatDetail
    }

    PlatformBackHandler(enabled = destination != AppDestination.Home) {
        when (destination) {
            AppDestination.Home -> Unit
            AppDestination.Auth,
            AppDestination.ChatDetail -> destination = AppDestination.Home
            AppDestination.ChatEdit -> destination = AppDestination.ChatDetail
            AppDestination.Drawing -> closeDrawing()
            AppDestination.Letters -> destination = AppDestination.ChatDetail
            AppDestination.DrawingViewer -> {
                viewingDrawing = null
                destination = AppDestination.ChatDetail
            }
        }
    }

    val createInviteForChat: (String, (ChatInvite?, String?) -> Unit) -> Unit = { chatId, reportResult ->
        coroutineScope.launch {
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    reportResult(null, inviteLoginRequiredText)
                } else {
                    chatRepository.createInvite(chatId, user.id).fold(
                        onSuccess = { invite -> reportResult(invite, null) },
                        onFailure = { error ->
                            reportResult(null, error.message ?: inviteCreateFailedText)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportResult(null, error.message ?: inviteCreateFailedText)
            }
        }
    }

    val sendDrawingToChat: (String, (String?) -> Unit) -> Unit = { json, reportResult ->
        val chatId = selectedChat?.id
        if (chatId == null) {
            reportResult(sendDrawingOpenChatFirstText)
        } else {
            coroutineScope.launch {
                try {
                    val user = authRepository.currentUser.first()
                    if (user == null) {
                        reportResult(sendDrawingLoginRequiredText)
                    } else {
                        val drawing = DrawingCodec().decode(json).getOrElse { error ->
                            reportResult(error.message ?: readingDrawingFailedText)
                            return@launch
                        }
                        letterRepository.sendLetter(
                            chatId = chatId,
                            authorUserId = user.id,
                            drawing = drawing,
                        ).fold(
                            onSuccess = {
                                reportResult(null)
                                if (destination == AppDestination.Drawing || destination == AppDestination.Letters) {
                                    destination = AppDestination.ChatDetail
                                    editingDrawingId = null
                                    editingDrawingJson = null
                                    drawingReturnDestination = AppDestination.ChatDetail
                                }
                            },
                            onFailure = { error ->
                                reportResult(error.message ?: sendingDrawingFailedText)
                            },
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportResult(error.message ?: sendingDrawingFailedText)
                }
            }
        }
    }

    val appColorScheme = if (darkThemeEnabled) {
        darkColorScheme(
            primary = Color(0xFFF5A800),
            onPrimary = Color(0xFF202124),
            background = Color(0xFF101114),
            surface = Color(0xFF181A1D),
            surfaceVariant = Color(0xFF292B2F),
            onSurface = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFFC7C9CF),
            outlineVariant = Color(0xFF4A4D53),
            secondaryContainer = Color(0xFF3A3326),
            onSecondaryContainer = Color(0xFFFFF1D0),
            error = Color(0xFFFF7777),
            errorContainer = Color(0xFF4A2528),
            onErrorContainer = Color(0xFFFFDADD),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFF5A800),
            onPrimary = Color(0xFF202124),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF5F5F5),
            onSurface = Color(0xFF202124),
            onSurfaceVariant = Color(0xFF5F6368),
            outlineVariant = Color(0xFFDADCE0),
            secondaryContainer = Color(0xFFFFF0C2),
            onSecondaryContainer = Color(0xFF202124),
        )
    }

    SideEffect {
        ThemePreferenceStorage.updateSystemBars(darkThemeEnabled)
    }

    MaterialTheme(colorScheme = appColorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
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
                        darkThemeEnabled = darkThemeEnabled,
                        onDarkThemeEnabledChange = { enabled ->
                            darkThemeEnabled = enabled
                            ThemePreferenceStorage.saveDarkTheme(enabled)
                        },
                        onBack = { destination = AppDestination.Home },
                    )
                    AppDestination.Drawing -> DrawingScreen(
                        initialDrawingJson = editingDrawingJson,
                        onBack = closeDrawing,
                        onSaveDrawing = { json, reportSaveResult ->
                            coroutineScope.launch {
                                try {
                                    val drawingId = editingDrawingId
                                    if (drawingId != null) {
                                        localDrawingRepository.updateDrawing(drawingId, json)
                                    } else {
                                        localDrawingRepository.saveDrawing(json)
                                    }
                                    reportSaveResult(null)
                                } catch (error: Exception) {
                                    reportSaveResult(
                                        error.message ?: savingDrawingFailedText,
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
                        onEditDrawing = { drawingId, drawingJson ->
                            editingDrawingId = drawingId
                            editingDrawingJson = drawingJson
                            drawingReturnDestination = AppDestination.Letters
                            destination = AppDestination.Drawing
                        },
                        onSendDrawing = sendDrawingToChat,
                    )
                    AppDestination.ChatDetail -> {
                        val chat = selectedChat
                        if (chat == null) {
                            PlaceholderScreen(
                                title = chatNotSelectedText,
                                description = chooseChatHintText,
                                onBack = { destination = AppDestination.Home },
                            )
                        } else {
                            ChatDetailsScreen(
                                chat = chat,
                                onBack = { destination = AppDestination.Home },
                                onEditChat = { destination = AppDestination.ChatEdit },
                                onCreateDrawing = {
                                    editingDrawingId = null
                                    editingDrawingJson = null
                                    drawingReturnDestination = AppDestination.ChatDetail
                                    destination = AppDestination.Drawing
                                },
                                onOpenDrawings = {
                                    editingDrawingId = null
                                    editingDrawingJson = null
                                    destination = AppDestination.Letters
                                },
                                onViewDrawing = { drawing ->
                                    viewingDrawing = drawing
                                    destination = AppDestination.DrawingViewer
                                },
                                onChatUpdated = { updatedChat -> selectedChat = updatedChat },
                                onCreateInvite = createInviteForChat,
                            )
                        }
                    }
                    AppDestination.ChatEdit -> {
                        val chat = selectedChat
                        if (chat == null) {
                            PlaceholderScreen(
                                title = chatNotSelectedText,
                                description = chooseChatHintText,
                                onBack = { destination = AppDestination.Home },
                            )
                        } else {
                            ChatEditScreen(
                                chat = chat,
                                currentUserId = signedInUser?.id,
                                onBack = { destination = AppDestination.ChatDetail },
                                onChatUpdated = { updatedChat -> selectedChat = updatedChat },
                                onChatRemoved = {
                                    selectedChat = null
                                    destination = AppDestination.Home
                                },
                                onCreateInvite = createInviteForChat,
                            )
                        }
                    }
                    AppDestination.DrawingViewer -> {
                        val drawing = viewingDrawing
                        if (drawing == null) {
                            PlaceholderScreen(
                                title = drawingUnavailableText,
                                description = selectDrawingHintText,
                                onBack = {
                                    viewingDrawing = null
                                    destination = AppDestination.ChatDetail
                                },
                            )
                        } else {
                            DrawingViewerScreen(
                                drawing = drawing,
                                onBack = {
                                    viewingDrawing = null
                                    destination = AppDestination.ChatDetail
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
