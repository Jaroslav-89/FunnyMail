package com.jaroapps.funnymail.features.chat.presentation

import com.jaroapps.funnymail.resources.firebase_not_configured
import com.jaroapps.funnymail.resources.letter_draw_something
import com.jaroapps.funnymail.resources.letter_generic_error
import com.jaroapps.funnymail.resources.letter_login_required
import com.jaroapps.funnymail.resources.letter_permission_error
import com.jaroapps.funnymail.resources.letter_sent
import com.jaroapps.funnymail.resources.letter_too_large
import com.jaroapps.funnymail.resources.network_connection_failed
import com.jaroapps.funnymail.resources.Res

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatDetailsUiState(
    val currentUser: UserProfile? = null,
    val letters: List<Letter> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val lastSentLetterId: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class ChatDetailsViewModel(
    private val letterRepository: LetterRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatDetailsUiState())
    val state: StateFlow<ChatDetailsUiState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var observedChatId: String? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { emit(null) }
                .collect { user ->
                    _state.update { it.copy(currentUser = user) }
                }
        }
    }

    fun observeLetters(chatId: String) {
        if (chatId.isBlank()) return
        if (observedChatId == chatId && observationJob?.isActive == true) return

        observationJob?.cancel()
        observedChatId = chatId
        _state.update {
            it.copy(
                letters = emptyList(),
                isLoading = true,
                message = null,
                messageIsError = false,
            )
        }
        observationJob = viewModelScope.launch {
            letterRepository.observeLetters(chatId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = error.toLetterMessage(),
                            messageIsError = true,
                        )
                    }
                    emit(emptyList())
                }
                .collect { letters ->
                    _state.update { it.copy(letters = letters, isLoading = false) }
                }
        }
    }

    fun stopObserving(chatId: String) {
        if (observedChatId != chatId) return
        observationJob?.cancel()
        observationJob = null
        observedChatId = null
    }

    fun sendDraft(chatId: String, drawing: DrawingDocument) {
        val userId = _state.value.currentUser?.id ?: run {
            showError(Res.string.letter_login_required)
            return
        }
        if (_state.value.isSending) return
        if (drawing.strokes.isEmpty() && drawing.texts.isEmpty() && drawing.stickers.isEmpty()) {
            showError(Res.string.letter_draw_something)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSending = true,
                    message = null,
                    messageIsError = false,
                )
            }
            letterRepository.sendLetter(
                chatId = chatId,
                authorUserId = userId,
                drawing = drawing,
            ).fold(
                onSuccess = { letter ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            lastSentLetterId = letter.id,
                            message = getString(Res.string.letter_sent),
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isSending = false,
                            message = error.toLetterMessage(),
                            messageIsError = true,
                        )
                    }
                },
            )
        }
    }

    private fun showError(resource: StringResource) {
        viewModelScope.launch {
            val message = getString(resource)
            _state.update { it.copy(message = message, messageIsError = true) }
        }
    }

    private suspend fun Throwable.toLetterMessage(): String {
        val details = message.orEmpty().lowercase()
        return when {
            "permission-denied" in details || "permission_denied" in details || "permission denied" in details ->
                getString(Res.string.letter_permission_error)
            "firebase не настроен" in details || "firebase is not configured" in details ->
                getString(Res.string.firebase_not_configured)
            "рисунок слишком большой" in details || "too large" in details ->
                getString(Res.string.letter_too_large)
            "network" in details || "unavailable" in details ->
                getString(Res.string.network_connection_failed)
            else -> getString(Res.string.letter_generic_error)
        }
    }
}
