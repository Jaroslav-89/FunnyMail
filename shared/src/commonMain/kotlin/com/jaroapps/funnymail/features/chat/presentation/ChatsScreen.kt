package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenChat: (Chat) -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var chatTitle by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Чаты",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "Письма для друзей",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Создайте общий чат или вступите в уже существующий по коду приглашения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.currentUser == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Нужен аккаунт", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Войдите или зарегистрируйтесь, чтобы создавать чаты и присоединяться к ним.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onOpenAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Войти / зарегистрироваться")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Новый чат", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = chatTitle,
                        onValueChange = { chatTitle = it },
                        label = { Text("Название чата") },
                        singleLine = true,
                        supportingText = { Text("До 80 символов") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.createChat(chatTitle)
                            chatTitle = ""
                        },
                        enabled = !state.isSubmitting && chatTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Создать чат")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Вступить по коду", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.trimStart() },
                        label = { Text("Код приглашения") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.joinWithInvite(inviteCode)
                            inviteCode = ""
                        },
                        enabled = !state.isSubmitting && inviteCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Присоединиться")
                    }
                }
            }

            state.activeInvite?.let { invite ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Код приглашения", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer {
                            Text(
                                invite.code,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "Выделите код, чтобы скопировать его. Приглашение действует 7 дней с момента создания.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = viewModel::dismissInvite) {
                            Text("Скрыть код")
                        }
                    }
                }
            }

            Text("Ваши чаты", style = MaterialTheme.typography.titleLarge)
            when {
                state.isLoading -> CircularProgressIndicator()
                state.chats.isEmpty() -> Text(
                    "Чатов пока нет. Создайте первый чат выше или вступите по приглашению.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> state.chats.forEach { chat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(chat.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${chat.memberIds.size} участн. · код чата ${chat.id.take(6)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { onOpenChat(chat) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Открыть чат")
                            }
                            OutlinedButton(
                                onClick = { viewModel.createInvite(chat.id) },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Пригласить друга")
                            }
                        }
                    }
                }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.messageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isSubmitting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.width(12.dp))
                Text("Выполняем операцию…")
            }
        }
    }
}
