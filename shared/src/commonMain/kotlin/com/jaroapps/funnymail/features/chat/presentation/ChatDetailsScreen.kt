package com.jaroapps.funnymail.features.chat.presentation

import org.jetbrains.compose.resources.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaroapps.funnymail.core.ui.AccountPersonIcon
import com.jaroapps.funnymail.core.ui.AppBackButton
import com.jaroapps.funnymail.core.ui.DrawingListIcon
import com.jaroapps.funnymail.core.ui.EditPencilIcon
import com.jaroapps.funnymail.core.ui.PersonPlusIcon
import com.jaroapps.funnymail.core.ui.PaperPlusIcon
import com.jaroapps.funnymail.core.ui.TrashCanIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import com.jaroapps.funnymail.features.letter.presentation.DrawingPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.jaroapps.funnymail.resources.add_to_chat
import com.jaroapps.funnymail.resources.add_user_to_chat
import com.jaroapps.funnymail.resources.already_in_chat
import com.jaroapps.funnymail.resources.automatic_delete_failed
import com.jaroapps.funnymail.resources.chat_drawing_delete_failed
import com.jaroapps.funnymail.resources.chat_drawing_delete_success
import com.jaroapps.funnymail.resources.delete_own_drawing
import com.jaroapps.funnymail.resources.done
import com.jaroapps.funnymail.resources.drawing_from_member
import com.jaroapps.funnymail.resources.drawing_menu_close
import com.jaroapps.funnymail.resources.drawing_menu_open
import com.jaroapps.funnymail.resources.edit_chat
import com.jaroapps.funnymail.resources.email_or_username
import com.jaroapps.funnymail.resources.empty_chat_letters
import com.jaroapps.funnymail.resources.find_user
import com.jaroapps.funnymail.resources.find_user_hint
import com.jaroapps.funnymail.resources.invite_code
import com.jaroapps.funnymail.resources.invite_code_expires
import com.jaroapps.funnymail.resources.invite_create_code
import com.jaroapps.funnymail.resources.new_drawing
import com.jaroapps.funnymail.resources.open_drawing_fullscreen
import com.jaroapps.funnymail.resources.open_my_drawings
import com.jaroapps.funnymail.resources.stroke_point_count
import com.jaroapps.funnymail.resources.user_add_failed
import com.jaroapps.funnymail.resources.user_added
import com.jaroapps.funnymail.resources.user_already_member
import com.jaroapps.funnymail.resources.user_not_found
import com.jaroapps.funnymail.resources.user_search_failed
import com.jaroapps.funnymail.resources.username_at
import com.jaroapps.funnymail.resources.you_already_member
import com.jaroapps.funnymail.resources.you_sent
import com.jaroapps.funnymail.resources.Res

@Composable
fun ChatDetailsScreen(
    chat: Chat,
    onBack: () -> Unit,
    onEditChat: () -> Unit,
    onCreateDrawing: () -> Unit,
    onOpenDrawings: () -> Unit,
    onViewDrawing: (DrawingDocument) -> Unit,
    onChatUpdated: (Chat) -> Unit,
    onCreateInvite: (String, (ChatInvite?, String?) -> Unit) -> Unit,
) {
    val viewModel: ChatDetailsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val authRepository = koinInject<AuthRepository>()
    val chatRepository = koinInject<ChatRepository>()
    val letterRepository = koinInject<LetterRepository>()
    val coroutineScope = rememberCoroutineScope()
    val automaticDeleteFailedText = stringResource(Res.string.automatic_delete_failed)
    val drawingDeletedText = stringResource(Res.string.chat_drawing_delete_success)
    val drawingDeleteFailedText = stringResource(Res.string.chat_drawing_delete_failed)
    val userNotFoundText = stringResource(Res.string.user_not_found)
    val userAlreadyMemberText = stringResource(Res.string.user_already_member)
    val youAlreadyMemberText = stringResource(Res.string.you_already_member)
    val userSearchFailedText = stringResource(Res.string.user_search_failed)
    val userAddedText = stringResource(Res.string.user_added)
    val addUserFailedText = stringResource(Res.string.user_add_failed)
    val editChatDescription = stringResource(Res.string.edit_chat)
    val addUserDescription = stringResource(Res.string.add_user_to_chat)
    val newDrawingDescription = stringResource(Res.string.new_drawing)
    val myDrawingsDescription = stringResource(Res.string.open_my_drawings)
    val openActionsMenuDescription = stringResource(Res.string.drawing_menu_open)
    val closeActionsMenuDescription = stringResource(Res.string.drawing_menu_close)
    val lettersListState = rememberLazyListState()
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var isCreatingInvite by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<ChatInvite?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var userSearchQuery by rememberSaveable(chat.id) { mutableStateOf("") }
    var foundUser by remember { mutableStateOf<UserProfile?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var isSearchingUser by remember { mutableStateOf(false) }
    var isAddingUser by remember { mutableStateOf(false) }
    var authorNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var drawingActionMessage by remember(chat.id) { mutableStateOf<String?>(null) }
    var activeChat by remember(chat.id) { mutableStateOf(chat) }
    var cleanupAttemptedIds by remember(chat.id, activeChat.maxLettersCount) {
        mutableStateOf<Set<String>>(emptySet())
    }

    DisposableEffect(chat.id) {
        viewModel.observeLetters(chat.id)
        onDispose { viewModel.stopObserving(chat.id) }
    }

    // Watch chat metadata too, so limit changes made by the creator take effect
    // immediately on every connected member's device.
    LaunchedEffect(chat.id, state.currentUser?.id) {
        val userId = state.currentUser?.id ?: return@LaunchedEffect
        chatRepository.observeChats(userId).collect { chats ->
            chats.firstOrNull { it.id == chat.id }?.let { activeChat = it }
        }
    }

    LaunchedEffect(chat.id, state.letters.lastOrNull()?.id, state.isLoading) {
        if (state.letters.isNotEmpty()) {
            lettersListState.scrollToItem(0)
        }
    }

    LaunchedEffect(chat.id, state.letters.size, state.currentUser?.id) {
        state.currentUser?.id?.let { userId ->
            chatRepository.markChatAsRead(chat.id, userId)
        }
    }

    // Client-side retention avoids Cloud Functions and runs after each Firestore
    // snapshot. Members prune their own excess drawings; the creator can prune any
    // excess drawing. Firestore Rules continue to enforce this permission boundary.
    LaunchedEffect(
        chat.id,
        activeChat.maxLettersCount,
        activeChat.creatorUserId,
        activeChat.memberIds,
        state.letters,
        state.isLoading,
        state.currentUser?.id,
    ) {
        val limit = activeChat.maxLettersCount ?: return@LaunchedEffect
        val userId = state.currentUser?.id ?: return@LaunchedEffect
        if (state.isLoading || limit < 1 || state.letters.size <= limit) return@LaunchedEffect

        val existingIds = state.letters.map { it.id }.toSet()
        val stillPresentAttempts = cleanupAttemptedIds.intersect(existingIds)
        if (stillPresentAttempts != cleanupAttemptedIds) {
            cleanupAttemptedIds = stillPresentAttempts
        }

        val creatorId = activeChat.creatorUserId.ifBlank {
            activeChat.memberIds.firstOrNull().orEmpty()
        }
        val isCreator = userId == creatorId
        val excessCount = (state.letters.size - limit).coerceAtLeast(0)
        val excessLetters = state.letters.take(excessCount).filter { letter ->
            (isCreator || letter.authorUserId == userId) && letter.id !in cleanupAttemptedIds
        }
        if (excessLetters.isEmpty()) return@LaunchedEffect

        cleanupAttemptedIds = cleanupAttemptedIds + excessLetters.map { it.id }
        coroutineScope.launch {
            excessLetters.forEach { letter ->
                letterRepository.deleteLetter(chat.id, letter.id, userId).onFailure { error ->
                    drawingActionMessage = drawingDeleteFailedText
                        ?: automaticDeleteFailedText
                }
            }
        }
    }

    LaunchedEffect(
        state.letters,
        state.currentUser?.id,
        state.currentUser?.customName,
        state.currentUser?.username,
        state.currentUser?.providerDisplayName,
        state.currentUser?.generatedName,
    ) {
        val userIds = state.letters.map { it.authorUserId }.distinct()
        val names = mutableMapOf<String, String>()
        userIds.forEach { authorId ->
            val profile = if (authorId == state.currentUser?.id) {
                state.currentUser
            } else {
                authRepository.getPublicProfile(authorId).getOrNull()
            }
            if (profile != null) names[authorId] = profile.chatDisplayName
        }
        authorNames = names
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.letters.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.letters.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.empty_chat_letters),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                LazyColumn(
                    state = lettersListState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(top = 68.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 0.dp,
                        end = 16.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.letters.asReversed(), key = { it.id }) { letter ->
                        ChatLetterCard(
                            letter = letter,
                            currentUserId = state.currentUser?.id,
                            authorDisplayName = authorNames[letter.authorUserId],
                            onClick = { onViewDrawing(letter.drawing) },
                            onDelete = {
                                val userId = state.currentUser?.id
                                if (userId != null) {
                                    coroutineScope.launch {
                                        letterRepository.deleteLetter(chat.id, letter.id, userId).fold(
                                            onSuccess = { drawingActionMessage = drawingDeletedText },
                                            onFailure = { error ->
                                                drawingActionMessage = drawingDeleteFailedText
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBackButton(onClick = onBack)

            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeChat.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeChat.memberIds.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AccountPersonIcon(
                        modifier = Modifier.padding(start = 3.dp).size(18.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ChatHeaderIconButton(onClick = onEditChat, description = editChatDescription) {
                EditPencilIcon(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            ChatHeaderIconButton(
                onClick = {
                    generatedInvite = null
                    inviteError = null
                    showInviteDialog = true
                },
                description = addUserDescription,
            ) {
                PersonPlusIcon(
                    modifier = Modifier.size(23.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        (drawingActionMessage ?: state.message)?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp, start = 12.dp, end = 12.dp)
                    .background(
                        color = if (state.messageIsError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (state.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FloatingActionButton(
                        onClick = {
                            actionsExpanded = false
                            onCreateDrawing()
                        },
                        modifier = Modifier.size(52.dp).semantics { contentDescription = newDrawingDescription },
                        shape = CircleShape,
                        containerColor = Color(0xFFF5A800),
                        contentColor = Color(0xFF202124),
                    ) {
                        PaperPlusIcon(color = Color(0xFF202124))
                    }
                    FloatingActionButton(
                        onClick = {
                            actionsExpanded = false
                            onOpenDrawings()
                        },
                        modifier = Modifier.size(52.dp).semantics { contentDescription = myDrawingsDescription },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        DrawingListIcon(color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            FloatingActionButton(
                onClick = { actionsExpanded = !actionsExpanded },
                modifier = Modifier.size(58.dp).semantics {
                    contentDescription = if (actionsExpanded) closeActionsMenuDescription else openActionsMenuDescription
                },
                shape = CircleShape,
                containerColor = Color(0xFFF5A800),
                contentColor = Color(0xFF202124),
            ) {
                Text(
                    text = "+",
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    modifier = Modifier.rotate(if (actionsExpanded) 45f else 0f),
                )
            }
        }

        if (showInviteDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isCreatingInvite && !isSearchingUser && !isAddingUser) showInviteDialog = false
                },
                title = { Text(stringResource(Res.string.add_user_to_chat)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(Res.string.find_user_hint))
                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = {
                                userSearchQuery = it
                                foundUser = null
                                searchMessage = null
                            },
                            label = { Text(stringResource(Res.string.email_or_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                if (!isSearchingUser && userSearchQuery.isNotBlank()) {
                                    isSearchingUser = true
                                    foundUser = null
                                    searchMessage = null
                                    coroutineScope.launch {
                                        authRepository.findUser(userSearchQuery).fold(
                                            onSuccess = { result ->
                                                foundUser = result
                                                searchMessage = when {
                                                    result == null -> userNotFoundText
                                                    result.id in chat.memberIds -> userAlreadyMemberText
                                                    result.id == state.currentUser?.id -> youAlreadyMemberText
                                                    else -> null
                                                }
                                                isSearchingUser = false
                                            },
                                            onFailure = { error ->
                                                searchMessage = userSearchFailedText
                                                isSearchingUser = false
                                            },
                                        )
                                    }
                                }
                            },
                            enabled = !isSearchingUser && userSearchQuery.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isSearchingUser) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            ) else Text(stringResource(Res.string.find_user))
                        }

                        foundUser?.let { user ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(user.chatDisplayName, style = MaterialTheme.typography.titleMedium)
                                    if (user.username.isNotBlank()) {
                                        Text(stringResource(Res.string.username_at, user.username), color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (user.email.isNotBlank()) {
                                        Text(
                                            user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val currentUserId = state.currentUser?.id
                                            if (currentUserId != null && !isAddingUser &&
                                                user.id !in chat.memberIds && user.id != currentUserId
                                            ) {
                                                isAddingUser = true
                                                searchMessage = null
                                                coroutineScope.launch {
                                                    chatRepository.addMember(chat.id, currentUserId, user.id).fold(
                                                        onSuccess = { updatedChat ->
                                                            onChatUpdated(updatedChat)
                                                            searchMessage = userAddedText
                                                            isAddingUser = false
                                                        },
                                                        onFailure = { error ->
                                                            searchMessage = addUserFailedText
                                                            isAddingUser = false
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                        enabled = !isAddingUser && user.id !in chat.memberIds &&
                                            user.id != state.currentUser?.id,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (isAddingUser) CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        ) else Text(if (user.id in chat.memberIds) stringResource(Res.string.already_in_chat) else stringResource(Res.string.add_to_chat))
                                    }
                                }
                            }
                        }
                        searchMessage?.let { Text(it, color = if (it == userAddedText) {
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.error) }

                        OutlinedButton(
                            onClick = {
                                if (!isCreatingInvite) {
                                    isCreatingInvite = true
                                    inviteError = null
                                    onCreateInvite(chat.id) { invite, error ->
                                        isCreatingInvite = false
                                        generatedInvite = invite
                                        inviteError = error
                                    }
                                }
                            },
                            enabled = !isCreatingInvite,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isCreatingInvite) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            ) else Text(stringResource(Res.string.invite_create_code))
                        }
                        generatedInvite?.let { invite ->
                            Text(stringResource(Res.string.invite_code), style = MaterialTheme.typography.titleSmall)
                            SelectionContainer {
                                Text(invite.code, style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text(stringResource(Res.string.invite_code_expires), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInviteDialog = false }) { Text(stringResource(Res.string.done)) }
                },
            )
        }
    }
}

@Composable
private fun ChatLetterCard(
    letter: Letter,
    currentUserId: String?,
    authorDisplayName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val openDrawingFullscreenDescription = stringResource(Res.string.open_drawing_fullscreen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (letter.authorUserId == currentUserId) stringResource(Res.string.you_sent) else authorDisplayName ?: stringResource(Res.string.drawing_from_member),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (letter.authorUserId == currentUserId) {
                    ChatHeaderIconButton(onClick = onDelete, description = stringResource(Res.string.delete_own_drawing)) {
                        TrashCanIcon(color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                text = stringResource(Res.string.stroke_point_count, letter.drawing.strokes.size, letter.drawing.strokes.sumOf { it.points.size }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DrawingPreview(
                drawing = letter.drawing,
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = openDrawingFullscreenDescription },
            )
        }
    }
}

@Composable
private fun ChatHeaderIconButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BackArrowGlyph(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val arrow = Path().apply {
            moveTo(size.width * 0.78f, size.height * 0.50f)
            lineTo(size.width * 0.22f, size.height * 0.50f)
            moveTo(size.width * 0.45f, size.height * 0.23f)
            lineTo(size.width * 0.18f, size.height * 0.50f)
            lineTo(size.width * 0.45f, size.height * 0.77f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.095f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
