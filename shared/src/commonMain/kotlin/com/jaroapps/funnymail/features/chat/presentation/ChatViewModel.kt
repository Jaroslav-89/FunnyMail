package com.jaroapps.funnymail.features.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.jaroapps.funnymail.resources.chat_created
import com.jaroapps.funnymail.resources.chat_enter_title
import com.jaroapps.funnymail.resources.chat_generic_operation_failed
import com.jaroapps.funnymail.resources.chat_joined
import com.jaroapps.funnymail.resources.chat_or_invite_not_found
import com.jaroapps.funnymail.resources.chat_permission_error
import com.jaroapps.funnymail.resources.enter_invite_code
import com.jaroapps.funnymail.resources.firebase_not_configured
import com.jaroapps.funnymail.resources.invite_code_ready
import com.jaroapps.funnymail.resources.letter_login_required
import com.jaroapps.funnymail.resources.network_connection_failed

data class ChatUiState(
    val currentUser: UserProfile? = null,
    val isAuthLoaded: Boolean = false,
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val activeInvite: ChatInvite? = null,
    val lastCreatedChatId: String? = null,
    val lastJoinedChatId: String? = null,
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                _state.update {
                    it.copy(
                        currentUser = user,
                        isAuthLoaded = true,
                        chats = emptyList(),
                        isLoading = user != null,
                        activeInvite = null,
                        lastCreatedChatId = null,
                        lastJoinedChatId = null,
                        message = null,
                        messageIsError = false,
                    )
                }
                if (user == null) return@collectLatest

                chatRepository.observeChats(user.id)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        _state.update {
                            it.copy(
                                isLoading = false,
                                message = error.toChatMessage(),
                                messageIsError = true,
                            )
                        }
                        emit(emptyList())
                    }
                    .collect { chats ->
                        _state.update { it.copy(chats = chats, isLoading = false) }
                    }
            }
        }
    }

    fun createChat(title: String) {
        val userId = _state.value.currentUser?.id ?: run {
            showError(letter_login_required)
            return
        }
        if (_state.value.isSubmitting) return
        if (title.isBlank()) {
            showError(chat_enter_title)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmitting = true,
                    message = null,
                    messageIsError = false,
                    lastCreatedChatId = null,
                )
            }
            chatRepository.createChat(title, userId).fold(
                onSuccess = { chat ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            lastCreatedChatId = chat.id,
                            message = getString(chat_created),
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error -> handleFailure(error) },
            )
        }
    }

    fun createInvite(chatId: String) {
        val userId = _state.value.currentUser?.id ?: run {
            showError(letter_login_required)
            return
        }
        if (_state.value.isSubmitting) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, message = null, messageIsError = false) }
            chatRepository.createInvite(chatId, userId).fold(
                onSuccess = { invite ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            activeInvite = invite,
                            message = getString(invite_code_ready),
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error -> handleFailure(error) },
            )
        }
    }

    fun joinWithInvite(code: String) {
        val userId = _state.value.currentUser?.id ?: run {
            showError(letter_login_required)
            return
        }
        if (_state.value.isSubmitting) return
        if (code.isBlank()) {
            showError(enter_invite_code)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmitting = true,
                    message = null,
                    messageIsError = false,
                    lastJoinedChatId = null,
                )
            }
            chatRepository.joinWithInvite(code, userId).fold(
                onSuccess = { chat ->
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            activeInvite = null,
                            lastJoinedChatId = chat.id,
                            message = getString(chat_joined, chat.title),
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error -> handleFailure(error) },
            )
        }
    }

    fun dismissInvite() {
        _state.update { it.copy(activeInvite = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageIsError = false) }
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) throw error
        viewModelScope.launch {
            val localizedMessage = error.toChatMessage()
            _state.update {
                it.copy(
                    isSubmitting = false,
                    message = localizedMessage,
                    messageIsError = true,
                )
            }
        }
    }

    private fun showError(resource: StringResource) {
        viewModelScope.launch {
            val message = getString(resource)
            _state.update { it.copy(message = message, messageIsError = true) }
        }
    }

    private suspend fun Throwable.toChatMessage(): String {
        val details = message.orEmpty().lowercase()
        return when {
            "permission-denied" in details || "permission_denied" in details || "permission denied" in details ->
                getString(chat_permission_error)
            "firebase не настроен" in details || "firebase is not configured" in details ->
                getString(firebase_not_configured)
            "не найдено" in details || "not found" in details ->
                getString(chat_or_invite_not_found)
            "network" in details || "unavailable" in details ->
                getString(network_connection_failed)
            else -> getString(chat_generic_operation_failed)
        }
    }
}
