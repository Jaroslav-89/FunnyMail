package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
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
    var description by rememberSaveable(chat.id) { mutableStateOf(chat.description) }
    var maxLettersText by rememberSaveable(chat.id) { mutableStateOf(chat.maxLettersCount?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var creatingInvite by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf<ChatInvite?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var showInviteDialog by rememberSaveable(chat.id) { mutableStateOf(false) }
    var userSearchQuery by rememberSaveable(chat.id) { mutableStateOf("") }
    var foundUser by remember { mutableStateOf<UserProfile?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var isSearchingUser by remember { mutableStateOf(false) }
    var isAddingUser by remember { mutableStateOf(false) }
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).background(Color.Transparent, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

            Text("Описание чата", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                readOnly = !isCreator,
                singleLine = false,
                minLines = 3,
                maxLines = 10,
                label = { Text("Описание") },
                supportingText = {
                    Text(if (isCreator) "${description.length}/120 символов" else "Изменять описание может только создатель")
                },
            )
            if (isCreator) {
                Button(
                    onClick = {
                        val userId = currentUserId
                        val normalized = description.trim()
                        if (userId != null && !busy && normalized.length <= 120 && normalized != chat.description) {
                            busy = true
                            message = null
                            scope.launch {
                                repository.updateChatDescription(chat.id, normalized, userId).fold(
                                    onSuccess = { updatedChat ->
                                        busy = false
                                        description = updatedChat.description
                                        onChatUpdated(updatedChat)
                                        message = "Описание чата сохранено."
                                        messageIsError = false
                                    },
                                    onFailure = { error ->
                                        busy = false
                                        message = error.message ?: "Не удалось изменить описание чата."
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && description.trim().length <= 120 && description.trim() != chat.description,
                ) { Text(if (busy) "Сохраняем…" else "Сохранить описание") }
            }

            Text("Максимум рисунков в чате", style = MaterialTheme.typography.titleMedium)
            val parsedLimit = maxLettersText.toIntOrNull()
            val normalizedLimit = if (maxLettersText.isBlank()) null else parsedLimit
            OutlinedTextField(
                value = if (isCreator) maxLettersText else chat.maxLettersCount?.toString() ?: "Без лимита",
                onValueChange = { value -> maxLettersText = value.filter { it.isDigit() }.take(3) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                readOnly = !isCreator,
                singleLine = true,
                label = { Text("Лимит рисунков") },
                supportingText = {
                    Text(
                        if (isCreator) "Пусто — без лимита. Допустимо от 1 до 500 рисунков."
                        else "Настраивает создатель чата. Пусто — без лимита."
                    )
                },
            )
            if (isCreator) {
                Button(
                    onClick = {
                        val userId = currentUserId
                        if (userId != null && !busy && (maxLettersText.isBlank() || parsedLimit in 1..500)) {
                            busy = true
                            message = null
                            scope.launch {
                                repository.updateChatMaxLettersCount(chat.id, normalizedLimit, userId).fold(
                                    onSuccess = { updatedChat ->
                                        busy = false
                                        maxLettersText = updatedChat.maxLettersCount?.toString().orEmpty()
                                        onChatUpdated(updatedChat)
                                        message = if (updatedChat.maxLettersCount == null) {
                                            "Лимит рисунков отключён."
                                        } else {
                                            "Лимит рисунков сохранён. Старые рисунки будут удаляться автоматически при превышении."
                                        }
                                        messageIsError = false
                                    },
                                    onFailure = { error ->
                                        busy = false
                                        message = error.message ?: "Не удалось изменить лимит рисунков."
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && (maxLettersText.isBlank() || parsedLimit in 1..500) &&
                        normalizedLimit != chat.maxLettersCount,
                ) { Text(if (busy) "Сохраняем…" else "Сохранить лимит") }
            }

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
                    userSearchQuery = ""
                    foundUser = null
                    searchMessage = null
                    invite = null
                    inviteError = null
                    showInviteDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !creatingInvite,
            ) {
                PersonPlusIcon(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Пригласить пользователей")
            }
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

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creatingInvite && !isSearchingUser && !isAddingUser) showInviteDialog = false
            },
            title = { Text("Добавить пользователя в чат") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Найдите пользователя по точному email или уникальному нику.")
                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = {
                            userSearchQuery = it
                            foundUser = null
                            searchMessage = null
                        },
                        label = { Text("Email или @ник") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (!isSearchingUser && userSearchQuery.isNotBlank()) {
                                isSearchingUser = true
                                foundUser = null
                                searchMessage = null
                                scope.launch {
                                    authRepository.findUser(userSearchQuery).fold(
                                        onSuccess = { result ->
                                            foundUser = result
                                            searchMessage = when {
                                                result == null -> "Пользователь не найден."
                                                result.id in chat.memberIds -> "Этот пользователь уже состоит в чате."
                                                result.id == currentUserId -> "Вы уже состоите в этом чате."
                                                else -> null
                                            }
                                            isSearchingUser = false
                                        },
                                        onFailure = { error ->
                                            searchMessage = error.message ?: "Не удалось выполнить поиск."
                                            isSearchingUser = false
                                        },
                                    )
                                }
                            }
                        },
                        enabled = !isSearchingUser && userSearchQuery.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSearchingUser) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Найти пользователя")
                    }

                    foundUser?.let { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(user.chatDisplayName, style = MaterialTheme.typography.titleMedium)
                                if (user.username.isNotBlank()) Text("@" + user.username, color = MaterialTheme.colorScheme.primary)
                                if (user.email.isNotBlank()) {
                                    Text(user.email, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        val userId = currentUserId
                                        if (userId != null && !isAddingUser &&
                                            user.id !in chat.memberIds && user.id != userId
                                        ) {
                                            isAddingUser = true
                                            searchMessage = null
                                            scope.launch {
                                                repository.addMember(chat.id, userId, user.id).fold(
                                                    onSuccess = { updatedChat ->
                                                        onChatUpdated(updatedChat)
                                                        searchMessage = "Пользователь добавлен в чат."
                                                        isAddingUser = false
                                                    },
                                                    onFailure = { error ->
                                                        searchMessage = error.message ?: "Не удалось добавить пользователя."
                                                        isAddingUser = false
                                                    },
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isAddingUser && user.id !in chat.memberIds && user.id != currentUserId,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isAddingUser) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text(if (user.id in chat.memberIds) "Уже в чате" else "Добавить в чат")
                                }
                            }
                        }
                    }
                    searchMessage?.let {
                        Text(it, color = if (it == "Пользователь добавлен в чат.")
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
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
                        enabled = !creatingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (creatingInvite) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Создать код приглашения")
                    }
                    invite?.let { currentInvite ->
                        Text("Код приглашения", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(currentInvite.code, style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Код действует 7 дней.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInviteDialog = false },
                    enabled = !creatingInvite && !isSearchingUser && !isAddingUser) { Text("Готово") }
            },
        )
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
