package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.core.ui.AccountPersonIcon
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.core.ui.PersonPlusIcon
import com.jaroapps.funnymail.core.ui.TrashCanIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ChatEditScreen(
    chat: Chat,
    currentUserId: String?,
    onBack: () -> Unit,
    onChatUpdated: (Chat) -> Unit,
    onChatRemoved: () -> Unit,
    onCreateInvite: (String, (ChatInvite?, String?) -> Unit) -> Unit,
) {
    val repository = koinInject<ChatRepository>()
    val authRepository = koinInject<AuthRepository>()
    val scope = rememberCoroutineScope()
    var memberProfiles by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    LaunchedEffect(chat.memberIds) {
        val profiles = mutableMapOf<String, UserProfile>()
        chat.memberIds.forEach { id ->
            authRepository.getPublicProfile(id).getOrNull()?.let { profiles[id] = it }
        }
        memberProfiles = profiles
    }
    val creatorId = chat.creatorUserId.ifBlank { chat.memberIds.firstOrNull().orEmpty() }
    val isCreator = currentUserId != null && currentUserId == creatorId

    var title by rememberSaveable(chat.id) { mutableStateOf(chat.title) }
    var busy by remember { mutableStateOf(false) }
    var creatingInvite by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf<ChatInvite?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.width(12.dp))
            Text("Настройки чата", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Название чата", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isCreator && !busy,
                singleLine = true,
                label = { Text("Название") },
                supportingText = {
                    Text(if (isCreator) "До 80 символов" else "Изменять название может только создатель")
                },
            )
            Button(
                onClick = {
                    val userId = currentUserId
                    val normalized = title.trim()
                    if (userId != null && normalized.isNotEmpty() && !busy) {
                        busy = true
                        message = null
                        scope.launch {
                            repository.updateChatTitle(chat.id, normalized, userId).fold(
                                onSuccess = {
                                    busy = false
                                    onChatUpdated(it)
                                    message = "Название чата сохранено."
                                    messageIsError = false
                                },
                                onFailure = {
                                    busy = false
                                    message = it.message ?: "Не удалось изменить название чата."
                                    messageIsError = true
                                },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isCreator && !busy && title.trim().isNotEmpty() && title.trim() != chat.title,
            ) { Text(if (busy) "Сохраняем…" else "Сохранить название") }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Участники", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        Text(chat.memberIds.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AccountPersonIcon(
                            modifier = Modifier.padding(start = 4.dp).size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    chat.memberIds.forEach { memberId ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccountPersonIcon(Modifier.size(26.dp), color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (memberId == currentUserId) "Вы" else {
                                            memberProfiles[memberId]?.chatDisplayName
                                                ?: "Участник чата"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = when {
                                            memberId == creatorId -> "Создатель чата"
                                            else -> "Участник"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isCreator && memberId != creatorId) {
                                    IconButton(onClick = { memberToRemove = memberId }, enabled = !busy) {
                                        TrashCanIcon(color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    if (!creatingInvite) {
                        creatingInvite = true
                        inviteError = null
                        onCreateInvite(chat.id) { result, error ->
                            creatingInvite = false
                            invite = result
                            inviteError = error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !creatingInvite && !busy,
            ) {
                if (creatingInvite) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    PersonPlusIcon(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Пригласить пользователей")
                }
            }
            invite?.let { currentInvite ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Код приглашения", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(currentInvite.code, style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Передайте код новому участнику. Он действует 7 дней.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let {
                Text(it, color = if (messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            }

            if (isCreator) {
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Удалить чат") }
            } else {
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) { Text("Выйти из чата") }
            }
        }
    }

    memberToRemove?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (!busy) memberToRemove = null },
            title = { Text("Удалить участника?") },
            text = { Text("Пользователь с ID ${targetId.take(12)} будет удалён из чата.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val userId = currentUserId
                        if (userId != null && !busy) {
                            busy = true
                            scope.launch {
                                repository.removeMember(chat.id, userId, targetId).fold(
                                    onSuccess = {
                                        busy = false
                                        memberToRemove = null
                                        onChatUpdated(it)
                                        message = "Участник удалён из чата."
                                        messageIsError = false
                                    },
                                    onFailure = {
                                        busy = false
                                        message = it.message ?: "Не удалось удалить участника."
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }, enabled = !busy) { Text("Отмена") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmLeave = false },
            title = { Text("Выйти из чата?") },
            text = { Text("После выхода письма этого чата больше не будут доступны.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val userId = currentUserId
                        if (userId != null && !busy) {
                            busy = true
                            scope.launch {
                                repository.leaveChat(chat.id, userId).fold(
                                    onSuccess = { onChatRemoved() },
                                    onFailure = {
                                        busy = false
                                        message = it.message ?: "Не удалось выйти из чата."
                                        messageIsError = true
                                        confirmLeave = false
                                    },
                                )
                            }
                        }
                    },
                ) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }, enabled = !busy) { Text("Отмена") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Удалить чат?") },
            text = { Text("Чат и все письма в нём будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val userId = currentUserId
                        if (userId != null && !busy) {
                            busy = true
                            scope.launch {
                                repository.deleteChat(chat.id, userId).fold(
                                    onSuccess = { onChatRemoved() },
                                    onFailure = {
                                        busy = false
                                        message = it.message ?: "Не удалось удалить чат."
                                        messageIsError = true
                                        confirmDelete = false
                                    },
                                )
                            }
                        }
                    },
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Отмена") } },
        )
    }
}
