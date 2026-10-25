package com.jaroapps.funnymail.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val currentUser: UserProfile? = null,
    val isSubmitting: Boolean = false,
    val isRegistration: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val isCheckingUsername: Boolean = false,
    val isSavingProfile: Boolean = false,
    val usernameAvailability: Boolean? = null,
    val usernameCheckQuery: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { emit(null) }
                .collect { user ->
                    _state.update { current ->
                        if (current.currentUser?.id != user?.id) {
                            current.copy(
                                currentUser = user,
                                isCheckingUsername = false,
                                isSavingProfile = false,
                                usernameAvailability = null,
                                usernameCheckQuery = null,
                            )
                        } else {
                            current.copy(currentUser = user)
                        }
                    }
                }
        }
    }

    fun toggleMode() {
        _state.update {
            it.copy(
                isRegistration = !it.isRegistration,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            showError("Введите email и пароль.")
            return
        }
        submit { authRepository.signInWithEmail(email.trim(), password) }
    }

    fun signInWithGoogle() {
        submit { authRepository.signInWithGoogle() }
    }

    fun signInWithApple() {
        submit { authRepository.signInWithApple() }
    }

    fun register(displayName: String, email: String, password: String) {
        if (displayName.isBlank()) {
            showError("Введите имя для профиля.")
            return
        }
        if (email.isBlank()) {
            showError("Введите email.")
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            showError("Пароль должен содержать минимум $MIN_PASSWORD_LENGTH символов.")
            return
        }
        submit {
            authRepository.registerWithEmail(
                email = email.trim(),
                password = password,
                displayName = displayName.trim(),
            )
        }
    }

    fun resetUsernameCheck() {
        _state.update {
            it.copy(
                usernameAvailability = null,
                usernameCheckQuery = null,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun checkUsername(username: String) {
        val userId = _state.value.currentUser?.id ?: return
        if (_state.value.isCheckingUsername || _state.value.isSavingProfile) return
        if (!USERNAME_REGEX.matches(username)) {
            _state.update {
                it.copy(usernameAvailability = null, message = "Ник должен содержать 3–20 символов: латинские буквы, цифры или _.", messageIsError = true)
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingUsername = true,
                    usernameAvailability = null,
                    usernameCheckQuery = username,
                    message = null,
                    messageIsError = false,
                )
            }
            authRepository.checkUsernameAvailability(username, userId).fold(
                onSuccess = { available ->
                    _state.update { current ->
                        if (current.usernameCheckQuery != username) {
                            current.copy(
                                isCheckingUsername = false,
                                usernameAvailability = null,
                                usernameCheckQuery = null,
                            )
                        } else {
                            current.copy(
                                isCheckingUsername = false,
                                usernameAvailability = available,
                                usernameCheckQuery = null,
                                message = null,
                                messageIsError = false,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        if (current.usernameCheckQuery != username) {
                            current.copy(
                                isCheckingUsername = false,
                                usernameAvailability = null,
                                usernameCheckQuery = null,
                            )
                        } else {
                            current.copy(
                                isCheckingUsername = false,
                                usernameAvailability = null,
                                usernameCheckQuery = null,
                                message = error.message ?: "Не удалось проверить ник.",
                                messageIsError = true,
                            )
                        }
                    }
                },
            )
        }
    }

    fun saveUserProfile(customName: String, username: String) {
        val userId = _state.value.currentUser?.id ?: return
        if (_state.value.isSavingProfile || _state.value.isCheckingUsername) return
        if (username.isNotBlank() && !USERNAME_REGEX.matches(username)) {
            _state.update { it.copy(message = "Ник должен содержать 3–20 символов: латинские буквы, цифры или _.", messageIsError = true) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSavingProfile = true, message = null, messageIsError = false) }
            authRepository.updateUserProfile(userId, customName.trim(), username.trim().removePrefix("@").lowercase()).fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            currentUser = user,
                            isSavingProfile = false,
                            usernameAvailability = true,
                            message = "Профиль сохранён.",
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingProfile = false,
                            usernameAvailability = if (error.message?.contains("Этот ник уже занят") == true) false else null,
                            message = if (error.message?.contains("Этот ник уже занят") == true) null
                                else error.message ?: "Не удалось сохранить профиль.",
                            messageIsError = error.message?.contains("Этот ник уже занят") != true,
                        )
                    }
                },
            )
        }
    }

    fun signOut() {
        if (_state.value.isSubmitting) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, message = null) }
            authRepository.signOut().fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            currentUser = null,
                            isSubmitting = false,
                            message = "Вы вышли из аккаунта.",
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = error.toAuthMessage(),
                            messageIsError = true,
                        )
                    }
                },
            )
        }
    }

    private fun submit(operation: suspend () -> Result<UserProfile>) {
        if (_state.value.isSubmitting) return

        viewModelScope.launch {
            _state.update {
                it.copy(isSubmitting = true, message = null, messageIsError = false)
            }
            operation().fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            currentUser = user,
                            isSubmitting = false,
                            message = "Готово.",
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = error.toAuthMessage(),
                            messageIsError = true,
                        )
                    }
                },
            )
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(message = message, messageIsError = true) }
    }

    private fun Throwable.toAuthMessage(): String {
        val text = message.orEmpty().lowercase()
        return when {
            "google_no_credential" in text ->
                "Не удалось получить Google-аккаунт. Проверьте, что на устройстве добавлен " +
                    "аккаунт Google и обновлены Сервисы Google Play."
            "google_provider_configuration" in text || "google_credential_error" in text ->
                "Не удалось запустить вход Google. Проверьте Web Client ID, SHA-1/SHA-256 " +
                    "подписи Android-приложения и настройки Google в Firebase."
            "google_sign_in_cancelled" in text ->
                "Вход через Google отменён. Если ошибка появляется после выбора аккаунта, " +
                    "проверьте SHA-1/SHA-256 подписи и OAuth-клиенты в Firebase."
            "cancel" in text || "cancelled" in text || "canceled" in text ->
                "Вход отменён."
            "firebase is not configured" in text ->
                "Firebase не настроен. Добавьте google-services.json для Android и " +
                    "GoogleService-Info.plist для iOS."
            "email-already-in-use" in text || "already in use" in text ->
                "Аккаунт с таким email уже существует."
            "invalid-email" in text || "badly formatted" in text ->
                "Проверьте формат email."
            "invalid-credential" in text || "wrong-password" in text ||
                "user-not-found" in text || "invalid login credentials" in text ->
                "Неверный email или пароль."
            "weak-password" in text || "at least 6 characters" in text ->
                "Пароль слишком короткий. Нужно не менее 6 символов."
            "network" in text || "unable to resolve host" in text ->
                "Не удалось подключиться. Проверьте интернет."
            "email is required" in text || "password is required" in text ->
                "Введите email и пароль."
            else -> "Не удалось выполнить операцию. Проверьте данные и настройки Firebase."
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")
    }
}
