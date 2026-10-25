package com.jaroapps.funnymail.features.chat.presentation

import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.jaroapps.funnymail.core.ui.AppBackButton
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.core.ui.PersonPlusIcon
import com.jaroapps.funnymail.core.ui.TrashCanIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import com.jaroapps.funnymail.resources.add_to_chat
import com.jaroapps.funnymail.resources.add_user_to_chat
import com.jaroapps.funnymail.resources.already_in_chat
import com.jaroapps.funnymail.resources.cancel
import com.jaroapps.funnymail.resources.chat_creator
import com.jaroapps.funnymail.resources.chat_description_missing
import com.jaroapps.funnymail.resources.chat_description_save_failed
import com.jaroapps.funnymail.resources.chat_description_saved
import com.jaroapps.funnymail.resources.chat_description_title
import com.jaroapps.funnymail.resources.chat_member
import com.jaroapps.funnymail.resources.chat_settings
import com.jaroapps.funnymail.resources.chat_title
import com.jaroapps.funnymail.resources.chat_title_label
import com.jaroapps.funnymail.resources.chat_title_save_failed
import com.jaroapps.funnymail.resources.chat_title_saved
import com.jaroapps.funnymail.resources.delete_chat
import com.jaroapps.funnymail.resources.delete_chat_description
import com.jaroapps.funnymail.resources.delete_chat_failed
import com.jaroapps.funnymail.resources.delete_chat_question
import com.jaroapps.funnymail.resources.description_label
import com.jaroapps.funnymail.resources.description_length
import com.jaroapps.funnymail.resources.done
import com.jaroapps.funnymail.resources.drawing_limit_cleanup_failed
import com.jaroapps.funnymail.resources.drawing_limit_disable_success
import com.jaroapps.funnymail.resources.drawing_limit_hint
import com.jaroapps.funnymail.resources.drawing_limit_label
import com.jaroapps.funnymail.resources.drawing_limit_readonly
import com.jaroapps.funnymail.resources.drawing_limit_save_failed
import com.jaroapps.funnymail.resources.drawing_limit_save_success
import com.jaroapps.funnymail.resources.email_or_username
import com.jaroapps.funnymail.resources.find_user
import com.jaroapps.funnymail.resources.find_user_hint
import com.jaroapps.funnymail.resources.invite_code
import com.jaroapps.funnymail.resources.invite_code_expires
import com.jaroapps.funnymail.resources.invite_create_code
import com.jaroapps.funnymail.resources.invite_users
import com.jaroapps.funnymail.resources.leave_chat
import com.jaroapps.funnymail.resources.leave_chat_description
import com.jaroapps.funnymail.resources.leave_chat_failed
import com.jaroapps.funnymail.resources.leave_chat_question
import com.jaroapps.funnymail.resources.max_drawings_title
import com.jaroapps.funnymail.resources.max_title_length
import com.jaroapps.funnymail.resources.member
import com.jaroapps.funnymail.resources.member_remove_failed
import com.jaroapps.funnymail.resources.member_removed
import com.jaroapps.funnymail.resources.members
import com.jaroapps.funnymail.resources.remove
import com.jaroapps.funnymail.resources.remove_member_description
import com.jaroapps.funnymail.resources.remove_member_question
import com.jaroapps.funnymail.resources.save_chat_description
import com.jaroapps.funnymail.resources.save_chat_title
import com.jaroapps.funnymail.resources.save_drawing_limit
import com.jaroapps.funnymail.resources.saving
import com.jaroapps.funnymail.resources.sign_out
import com.jaroapps.funnymail.resources.user_add_failed
import com.jaroapps.funnymail.resources.user_added
import com.jaroapps.funnymail.resources.user_already_member
import com.jaroapps.funnymail.resources.user_not_found
import com.jaroapps.funnymail.resources.user_search_failed
import com.jaroapps.funnymail.resources.username_at
import com.jaroapps.funnymail.resources.you
import com.jaroapps.funnymail.resources.you_already_member

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
    val letterRepository = koinInject<LetterRepository>()
    val authRepository = koinInject<AuthRepository>()
    val scope = rememberCoroutineScope()
    val chatTitleSavedMessage = stringResource(chat_title_saved)
    val chatTitleSaveFailedMessage = stringResource(chat_title_save_failed)
    val chatDescriptionSavedMessage = stringResource(chat_description_saved)
    val chatDescriptionSaveFailedMessage = stringResource(chat_description_save_failed)
    val chatDescriptionMissingText = stringResource(chat_description_missing)
    val drawingLimitCleanupFailedMessage = stringResource(drawing_limit_cleanup_failed)
    val drawingLimitDisableSuccessMessage = stringResource(drawing_limit_disable_success)
    val drawingLimitSaveSuccessMessage = stringResource(drawing_limit_save_success)
    val drawingLimitSaveFailedMessage = stringResource(drawing_limit_save_failed)
    val userNotFoundText = stringResource(user_not_found)
    val userAlreadyMemberText = stringResource(user_already_member)
    val youAlreadyMemberText = stringResource(you_already_member)
    val userSearchFailedText = stringResource(user_search_failed)
    val userAddedText = stringResource(user_added)
    val addUserFailedText = stringResource(user_add_failed)
    val memberRemovedMessage = stringResource(member_removed)
    val memberRemoveFailedMessage = stringResource(member_remove_failed)
    val leaveChatFailedMessage = stringResource(leave_chat_failed)
    val deleteChatFailedMessage = stringResource(delete_chat_failed)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 76.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(chat_title), style = MaterialTheme.typography.titleMedium)
            if (isCreator) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(stringResource(chat_title_label)) },
                    supportingText = { Text(stringResource(max_title_length)) },
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
                                        message = chatTitleSavedMessage
                                        messageIsError = false
                                    },
                                    onFailure = {
                                        busy = false
                                        message = it.message ?: chatTitleSaveFailedMessage
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && title.trim().isNotEmpty() && title.trim() != chat.title,
                ) { Text(if (busy) stringResource(saving) else stringResource(save_chat_title)) }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = chat.title,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(stringResource(chat_description_title), style = MaterialTheme.typography.titleMedium)
            if (isCreator) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 10,
                    label = { Text(stringResource(description_label)) },
                    supportingText = { Text(stringResource(description_length, description.length)) },
                )
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
                                        message = chatDescriptionSavedMessage
                                        messageIsError = false
                                    },
                                    onFailure = { error ->
                                        busy = false
                                        message = chatDescriptionSaveFailedMessage
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && description.trim().length <= 120 && description.trim() != chat.description,
                ) { Text(if (busy) stringResource(saving) else stringResource(save_chat_description)) }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = chat.description.ifBlank { chatDescriptionMissingText },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (isCreator || chat.maxLettersCount != null) {
                Text(stringResource(max_drawings_title), style = MaterialTheme.typography.titleMedium)
                val parsedLimit = maxLettersText.toIntOrNull()
                val normalizedLimit = if (maxLettersText.isBlank()) null else parsedLimit
                if (isCreator) {
                    OutlinedTextField(
                        value = maxLettersText,
                        onValueChange = { value -> maxLettersText = value.filter { it.isDigit() }.take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        label = { Text(stringResource(drawing_limit_label)) },
                        supportingText = {
                            Text(stringResource(drawing_limit_hint))
                        },
                    )
                    Button(
                        onClick = {
                            val userId = currentUserId
                            if (userId != null && !busy && (maxLettersText.isBlank() || parsedLimit in 1..500)) {
                                busy = true
                                message = null
                                scope.launch {
                                    val updateResult = repository.updateChatMaxLettersCount(
                                        chat.id,
                                        normalizedLimit,
                                        userId,
                                    )
                                    if (updateResult.isSuccess) {
                                        val updatedChat = updateResult.getOrThrow()
                                        onChatUpdated(updatedChat)
                                        maxLettersText = updatedChat.maxLettersCount?.toString().orEmpty()
                                        var cleanupFailed = false
                                        val newLimit = updatedChat.maxLettersCount
                                        if (newLimit != null) {
                                            try {
                                                val currentLetters = letterRepository.observeLetters(chat.id).first()
                                                val excessLetters = (currentLetters.size - newLimit).coerceAtLeast(0)
                                                currentLetters.take(excessLetters).forEach { letter ->
                                                    if (letterRepository.deleteLetter(chat.id, letter.id, userId).isFailure) {
                                                        cleanupFailed = true
                                                    }
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                cleanupFailed = true
                                            }
                                        }
                                        busy = false
                                        message = when {
                                            cleanupFailed -> drawingLimitCleanupFailedMessage
                                            newLimit == null -> drawingLimitDisableSuccessMessage
                                            else -> drawingLimitSaveSuccessMessage
                                        }
                                        messageIsError = cleanupFailed
                                    } else {
                                        busy = false
                                        message = drawingLimitSaveFailedMessage
                                        messageIsError = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && (maxLettersText.isBlank() || parsedLimit in 1..500) &&
                            normalizedLimit != chat.maxLettersCount,
                    ) { Text(if (busy) stringResource(saving) else stringResource(save_drawing_limit)) }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = chat.maxLettersCount.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(drawing_limit_readonly),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(members), modifier = Modifier.weight(1f),
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
                                        text = if (memberId == currentUserId) stringResource(you) else {
                                            memberProfiles[memberId]?.chatDisplayName
                                                ?: stringResource(chat_member)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = when {
                                            memberId == creatorId -> stringResource(chat_creator)
                                            else -> stringResource(member)
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
                Text(stringResource(invite_users))
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
                ) { Text(stringResource(delete_chat)) }
            } else {
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) { Text(stringResource(leave_chat)) }
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
                Text(
                    text = stringResource(chat_settings),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creatingInvite && !isSearchingUser && !isAddingUser) showInviteDialog = false
            },
            title = { Text(stringResource(add_user_to_chat)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(find_user_hint))
                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = {
                            userSearchQuery = it
                            foundUser = null
                            searchMessage = null
                        },
                        label = { Text(stringResource(email_or_username)) },
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
                                                result == null -> userNotFoundText
                                                result.id in chat.memberIds -> userAlreadyMemberText
                                                result.id == currentUserId -> youAlreadyMemberText
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
                        if (isSearchingUser) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(find_user))
                    }

                    foundUser?.let { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(user.chatDisplayName, style = MaterialTheme.typography.titleMedium)
                                if (user.username.isNotBlank()) Text(stringResource(username_at, user.username), color = MaterialTheme.colorScheme.primary)
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
                                    enabled = !isAddingUser && user.id !in chat.memberIds && user.id != currentUserId,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isAddingUser) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text(if (user.id in chat.memberIds) stringResource(already_in_chat) else stringResource(add_to_chat))
                                }
                            }
                        }
                    }
                    searchMessage?.let {
                        Text(it, color = if (it == userAddedText)
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
                        else Text(stringResource(invite_create_code))
                    }
                    invite?.let { currentInvite ->
                        Text(stringResource(invite_code), style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(currentInvite.code, style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text(stringResource(invite_code_expires), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInviteDialog = false },
                    enabled = !creatingInvite && !isSearchingUser && !isAddingUser) { Text(stringResource(done)) }
            },
        )
    }

    memberToRemove?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (!busy) memberToRemove = null },
            title = { Text(stringResource(remove_member_question)) },
            text = { Text(stringResource(remove_member_description, targetId.take(12))) },
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
                                        message = memberRemovedMessage
                                        messageIsError = false
                                    },
                                    onFailure = {
                                        busy = false
                                        message = it.message ?: memberRemoveFailedMessage
                                        messageIsError = true
                                    },
                                )
                            }
                        }
                    },
                ) { Text(stringResource(remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }, enabled = !busy) { Text(stringResource(cancel)) } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmLeave = false },
            title = { Text(stringResource(leave_chat_question)) },
            text = { Text(stringResource(leave_chat_description)) },
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
                                        message = it.message ?: leaveChatFailedMessage
                                        messageIsError = true
                                        confirmLeave = false
                                    },
                                )
                            }
                        }
                    },
                ) { Text(stringResource(sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }, enabled = !busy) { Text(stringResource(cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text(stringResource(delete_chat_question)) },
            text = { Text(stringResource(delete_chat_description)) },
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
                                        message = it.message ?: deleteChatFailedMessage
                                        messageIsError = true
                                        confirmDelete = false
                                    },
                                )
                            }
                        }
                    },
                ) { Text(stringResource(remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text(stringResource(cancel)) } },
        )
    }
}
