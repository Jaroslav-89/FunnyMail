package com.jaroapps.funnymail.features.home.presentation

import org.jetbrains.compose.resources.stringResource

import com.jaroapps.funnymail.resources.Res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.catch
import com.jaroapps.funnymail.core.ui.AccountPersonIcon
import com.jaroapps.funnymail.core.ui.SearchMagnifierIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.chat.presentation.ChatViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onAuthorize: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenChat: (Chat) -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()
    val chatRepository = koinInject<ChatRepository>()
    val state by viewModel.state.collectAsState()
    val accountDescription = stringResource(Res.string.account_label)
    val openChatMenuDescription = stringResource(Res.string.chat_menu_open)
    val closeChatMenuDescription = stringResource(Res.string.chat_menu_close)
    val currentUserId = state.currentUser?.id

    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showCreateChatDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinChatDialog by rememberSaveable { mutableStateOf(false) }
    var chatTitle by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.lastCreatedChatId) {
        if (state.lastCreatedChatId != null) {
            showCreateChatDialog = false
            chatTitle = ""
        }
    }
    LaunchedEffect(state.lastJoinedChatId) {
        if (state.lastJoinedChatId != null) {
            showJoinChatDialog = false
            inviteCode = ""
        }
    }

    if (!state.isAuthLoaded) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator()
        }
        return
    }

    if (state.currentUser == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAuthorize,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.sign_in))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFF5A800),
                )
                IconButton(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = accountDescription },
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AccountPersonIcon(color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Text(
                text = stringResource(Res.string.chat_list_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                state.isLoading && state.chats.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.chats.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(Res.string.no_chats_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(Res.string.no_chats_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.chats, key = { it.id }) { chat ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChat(chat) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = chat.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(Res.string.chat_member_count, chat.memberIds.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    currentUserId?.let { userId ->
                                        UnreadMessageBadge(
                                            chatRepository = chatRepository,
                                            chatId = chat.id,
                                            userId = userId,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.messageIsError && state.message != null) {
                Text(
                    text = state.message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
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
                            viewModel.clearMessage()
                            showCreateChatDialog = true
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .semantics { contentDescription = "Новый чат" },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text("+", fontSize = 28.sp, lineHeight = 28.sp)
                    }
                    FloatingActionButton(
                        onClick = {
                            actionsExpanded = false
                            viewModel.clearMessage()
                            showJoinChatDialog = true
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .semantics { contentDescription = "Вступить в чат" },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        SearchMagnifierIcon(color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            FloatingActionButton(
                onClick = { actionsExpanded = !actionsExpanded },
                modifier = Modifier
                    .size(58.dp)
                    .semantics {
                        contentDescription = if (actionsExpanded) "Закрыть меню чатов" else "Открыть меню чатов"
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
    }

    if (showCreateChatDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isSubmitting) {
                    showCreateChatDialog = false
                    viewModel.clearMessage()
                }
            },
            title = { Text(stringResource(Res.string.new_chat)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.create_chat_prompt))
                    OutlinedTextField(
                        value = chatTitle,
                        onValueChange = { chatTitle = it },
                        label = { Text(stringResource(Res.string.chat_title)) },
                        singleLine = true,
                        supportingText = { Text(stringResource(Res.string.max_title_length)) },
                        enabled = !state.isSubmitting,
                    )
                    if (state.messageIsError && state.message != null) {
                        Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createChat(chatTitle) },
                    enabled = !state.isSubmitting && chatTitle.isNotBlank(),
                ) {
                    if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(Res.string.create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateChatDialog = false
                        viewModel.clearMessage()
                    },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showJoinChatDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isSubmitting) {
                    showJoinChatDialog = false
                    viewModel.clearMessage()
                }
            },
            title = { Text(stringResource(Res.string.join_chat)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.enter_invite_code))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.trimStart() },
                        label = { Text(stringResource(Res.string.invite_code)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                    )
                    if (state.messageIsError && state.message != null) {
                        Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.joinWithInvite(inviteCode) },
                    enabled = !state.isSubmitting && inviteCode.isNotBlank(),
                ) {
                    if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(Res.string.join))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJoinChatDialog = false
                        viewModel.clearMessage()
                    },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}


@Composable
private fun UnreadMessageBadge(
    chatRepository: ChatRepository,
    chatId: String,
    userId: String,
) {
    val unreadCountFlow = remember(chatRepository, chatId, userId) {
        chatRepository.observeUnreadCount(chatId, userId).catch { emit(0) }
    }
    val unreadCount by unreadCountFlow.collectAsState(initial = 0)
    if (unreadCount > 0) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
        ) {
            Text(
                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
