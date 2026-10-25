package com.jaroapps.funnymail.features.home.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaroapps.funnymail.core.ui.AccountPersonIcon
import com.jaroapps.funnymail.core.ui.SearchMagnifierIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.presentation.ChatViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onAuthorize: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenChat: (Chat) -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

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

    if (state.currentUser == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "FunnyMail",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAuthorize,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Авторизоваться")
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
                    text = "FunnyMail",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Аккаунт" },
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AccountPersonIcon(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Text(
                text = "Ваши чаты",
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
                            "Пока нет чатов",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Нажмите «+», чтобы создать чат или вступить в существующий по коду.",
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
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = chat.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${chat.memberIds.size} участн.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
            title = { Text("Новый чат") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Придумайте название для чата.")
                    OutlinedTextField(
                        value = chatTitle,
                        onValueChange = { chatTitle = it },
                        label = { Text("Название чата") },
                        singleLine = true,
                        supportingText = { Text("До 80 символов") },
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
                    else Text("Создать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateChatDialog = false
                        viewModel.clearMessage()
                    },
                    enabled = !state.isSubmitting,
                ) { Text("Отмена") }
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
            title = { Text("Вступить в чат") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Введите код приглашения.")
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.trimStart() },
                        label = { Text("Код приглашения") },
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
                    else Text("Вступить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJoinChatDialog = false
                        viewModel.clearMessage()
                    },
                    enabled = !state.isSubmitting,
                ) { Text("Отмена") }
            },
        )
    }
}
