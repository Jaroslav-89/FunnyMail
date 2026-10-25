package com.jaroapps.funnymail.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroapps.funnymail.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
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
            showError(Res.string.auth_enter_email_password)
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
            showError(Res.string.auth_enter_name)
            return
        }
        if (email.isBlank()) {
            showError(Res.string.auth_enter_email)
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            showError(Res.string.auth_password_min_length, MIN_PASSWORD_LENGTH)
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
            showError(Res.string.auth_username_format_error)
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
                                message = error.message ?: getString(Res.string.auth_username_check_failed),
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
            showError(Res.string.auth_username_format_error)
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
                            message = getString(Res.string.auth_profile_saved),
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    val usernameAlreadyTaken =
                        error.message?.contains("USERNAME_TAKEN") == true
                    _state.update {
                        it.copy(
                            isSavingProfile = false,
                            usernameAvailability = if (usernameAlreadyTaken) false else null,
                            message = if (usernameAlreadyTaken) null
                                else getString(Res.string.auth_profile_save_failed),
                            messageIsError = !usernameAlreadyTaken,
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
                            message = getString(Res.string.auth_signed_out),
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
                            message = getString(Res.string.auth_done),
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

    private fun showError(resource: StringResource, vararg arguments: Any) {
        viewModelScope.launch {
            val message = getString(resource, *arguments)
            _state.update { it.copy(message = message, messageIsError = true) }
        }
    }

    private suspend fun Throwable.toAuthMessage(): String {
        val text = message.orEmpty().lowercase()
        return when {
            "google_no_credential" in text ->
                getString(Res.string.auth_google_no_credential)
            "google_provider_configuration" in text || "google_credential_error" in text ->
                getString(Res.string.auth_google_configuration_error)
            "google_sign_in_cancelled" in text ->
                getString(Res.string.auth_google_cancelled_hint)
            "cancel" in text || "cancelled" in text || "canceled" in text ->
                getString(Res.string.auth_signin_cancelled)
            "firebase is not configured" in text ->
                getString(Res.string.auth_firebase_config_files)
            "email-already-in-use" in text || "already in use" in text ->
                getString(Res.string.auth_email_already_exists)
            "invalid-email" in text || "badly formatted" in text ->
                getString(Res.string.auth_invalid_email)
            "invalid-credential" in text || "wrong-password" in text ||
                "user-not-found" in text || "invalid login credentials" in text ->
                getString(Res.string.auth_invalid_credentials)
            "weak-password" in text || "at least 6 characters" in text ->
                getString(Res.string.auth_password_too_short)
            "network" in text || "unable to resolve host" in text ->
                getString(Res.string.network_connection_failed)
            "email is required" in text || "password is required" in text ->
                getString(Res.string.auth_enter_email_password)
            else -> getString(Res.string.auth_operation_failed)
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")
    }
}
