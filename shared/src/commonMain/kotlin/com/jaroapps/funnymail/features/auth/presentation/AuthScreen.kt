package com.jaroapps.funnymail.features.auth.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.core.auth.NativeSocialSignIn
import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    firebaseConfigured: Boolean,
    darkThemeEnabled: Boolean,
    onDarkThemeEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: AuthViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var registrationName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customName by rememberSaveable(state.currentUser?.id) {
        mutableStateOf(state.currentUser?.customName.orEmpty())
    }
    var username by rememberSaveable(state.currentUser?.id) {
        mutableStateOf(state.currentUser?.username.orEmpty())
    }

    val normalizedUsername = username.trim().removePrefix("@").lowercase()
    val savedUsername = state.currentUser?.username.orEmpty()
    val usernameUnchanged = normalizedUsername == savedUsername.lowercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Назад" },
                contentAlignment = Alignment.Center,
            ) {
                BackArrowGlyph()
            }
            Text(
                text = "Аккаунт",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Темная тема", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Более тёмное оформление приложения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = darkThemeEnabled,
                onCheckedChange = onDarkThemeEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFF5A800),
                    checkedTrackColor = Color(0xFFF5A800).copy(alpha = 0.38f),
                    checkedBorderColor = Color(0xFFF5A800),
                ),
            )
        }

        if (state.currentUser == null) {
            Text(
                text = "Войдите, чтобы пользоваться чатами и письмами на разных устройствах.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!firebaseConfigured) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = buildString {
                        append(
                            "Firebase пока не настроен. Пока конфигурация не обнаружена, " +
                                "вход через провайдеры авторизации недоступен.",
                        )
                        FirebaseRuntimeStatus.initializationMessage?.let { details ->
                            append("\n\n")
                            append(details)
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.currentUser != null) {
            Text("Вы вошли как", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.currentUser?.chatDisplayName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = state.currentUser?.email.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Имя в чате", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it.take(40) },
                label = { Text("Имя (необязательно)") },
                supportingText = { Text("Если оставить пустым, будет использован ник или имя аккаунта.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSavingProfile,
            )

            Text("Уникальный ник", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.removePrefix("@").filter { char ->
                        char in 'a'..'z' || char in 'A'..'Z' ||
                            char in '0'..'9' || char == '_'
                    }.take(20)
                    viewModel.resetUsernameCheck()
                },
                leadingIcon = { Text("@", style = MaterialTheme.typography.titleMedium) },
                label = { Text("Ник") },
                supportingText = { Text("3–20 символов: латинские буквы, цифры и _") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSavingProfile,
            )

            if (state.usernameAvailability == false) {
                Text(
                    "Этот ник уже занят, выберите другой ник.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.usernameAvailability == true && !usernameUnchanged) {
                Text(
                    "Этот ник свободен.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val canSave = normalizedUsername.isBlank() ||
                usernameUnchanged ||
                state.usernameAvailability == true
            Button(
                onClick = {
                    if (normalizedUsername.isNotBlank() && !usernameUnchanged &&
                        state.usernameAvailability != true
                    ) {
                        viewModel.checkUsername(normalizedUsername)
                    } else {
                        viewModel.saveUserProfile(customName, normalizedUsername)
                    }
                },
                enabled = !state.isCheckingUsername && !state.isSavingProfile,
                modifier = Modifier.fillMaxWidth(),
                colors = if (canSave) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF188038),
                        contentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                when {
                    state.isCheckingUsername -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    state.isSavingProfile -> Text("Сохраняем…")
                    normalizedUsername.isBlank() || usernameUnchanged ||
                        state.usernameAvailability == true -> Text("Сохранить")
                    else -> Text("Проверить на уникальность")
                }
            }

            Button(
                onClick = viewModel::signOut,
                enabled = !state.isSubmitting && !state.isSavingProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти")
            }
        } else {
            if (state.isRegistration) {
                OutlinedTextField(
                    value = registrationName,
                    onValueChange = { registrationName = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    if (state.isRegistration) viewModel.register(registrationName, email, password)
                    else viewModel.signIn(email, password)
                },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                ) else Text(if (state.isRegistration) "Создать аккаунт" else "Войти")
            }

            OutlinedButton(
                onClick = viewModel::toggleMode,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRegistration) "У меня уже есть аккаунт" else "Зарегистрироваться")
            }

            Text(
                text = "Или продолжить через",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (NativeSocialSignIn.supportsGoogleSignIn) {
                Button(
                    onClick = viewModel::signInWithGoogle,
                    enabled = !state.isSubmitting && firebaseConfigured,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF075B35),
                        contentColor = Color.White,
                    ),
                ) { Text("Продолжить с Google") }
            }
            if (NativeSocialSignIn.supportsAppleSignIn) {
                OutlinedButton(
                    onClick = viewModel::signInWithApple,
                    enabled = !state.isSubmitting && firebaseConfigured,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Продолжить с Apple") }
            }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                color = if (state.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BackArrowGlyph() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(23.dp)) {
        val arrow = Path().apply {
            moveTo(size.width * 0.78f, size.height * 0.50f)
            lineTo(size.width * 0.22f, size.height * 0.50f)
            moveTo(size.width * 0.45f, size.height * 0.23f)
            lineTo(size.width * 0.18f, size.height * 0.50f)
            lineTo(size.width * 0.45f, size.height * 0.77f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(width = size.minDimension * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
