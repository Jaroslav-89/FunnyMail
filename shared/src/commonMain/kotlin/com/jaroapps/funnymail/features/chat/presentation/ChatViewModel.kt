package com.jaroapps.funnymail.features.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
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

data class ChatUiState(
    val currentUser: UserProfile? = null,
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
            showError("Сначала войдите в аккаунт.")
            return
        }
        if (_state.value.isSubmitting) return
        if (title.isBlank()) {
            showError("Введите название чата.")
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
                            message = "Чат создан.",
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
            showError("Сначала войдите в аккаунт.")
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
                            message = "Код приглашения готов. Его можно передать другу.",
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
            showError("Сначала войдите в аккаунт.")
            return
        }
        if (_state.value.isSubmitting) return
        if (code.isBlank()) {
            showError("Введите код приглашения.")
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
                            message = "Вы присоединились к чату «${chat.title}».",
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
        _state.update {
            it.copy(
                isSubmitting = false,
                message = error.toChatMessage(),
                messageIsError = true,
            )
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(message = message, messageIsError = true) }
    }

    private fun Throwable.toChatMessage(): String {
        val details = message.orEmpty().lowercase()
        return when {
            "permission-denied" in details || "permission_denied" in details || "permission denied" in details ->
                "Firestore отклонил операцию. Проверьте, что опубликованы правила из firestore.rules и код приглашения не истёк (срок — 7 дней)."
            "firebase не настроен" in details || "firebase is not configured" in details ->
                "Firebase не настроен. Добавьте конфигурационные файлы платформы."
            "не найдено" in details || "not found" in details ->
                "Чат или приглашение не найдено либо недействительно."
            "network" in details || "unavailable" in details ->
                "Не удалось подключиться к серверу. Проверьте интернет."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "Не удалось выполнить операцию. Проверьте настройки Firebase."
        }
    }
}
