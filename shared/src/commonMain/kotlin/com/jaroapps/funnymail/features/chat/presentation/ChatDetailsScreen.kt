package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.presentation.DrawingPreview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatDetailsScreen(
    chat: Chat,
    draftJson: String?,
    onBack: () -> Unit,
    onDraftSent: () -> Unit,
) {
    val viewModel: ChatDetailsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val draft: DrawingDocument? = remember(draftJson) {
        draftJson?.let { DrawingCodec().decode(it).getOrNull() }
    }

    DisposableEffect(chat.id) {
        viewModel.observeLetters(chat.id)
        onDispose { viewModel.stopObserving(chat.id) }
    }
    LaunchedEffect(state.draftSent) {
        if (state.draftSent) onDraftSent()
    }

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
            TextButton(onClick = onBack) { Text("← К чатам") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Письма",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(chat.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${chat.memberIds.size} участн. · письма доступны 7 дней",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (draftJson != null && draft == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    "Локальный черновик не удалось прочитать. Вернитесь на главный экран и создайте рисунок заново.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        } else if (draft != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Черновик готов к отправке", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${draft.strokes.size} штрихов · ${draft.strokes.sumOf { it.points.size }} точек",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DrawingPreview(
                        drawing = draft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                    Button(
                        onClick = { viewModel.sendDraft(chat.id, draft) },
                        enabled = !state.isSending && state.currentUser != null && draft.strokes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isSending) "Отправляем…" else "Отправить письмо")
                    }
                    if (state.currentUser == null) {
                        Text(
                            "Войдите в аккаунт, чтобы отправлять письма.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                Text(
                    "Здесь можно отправлять рисунки из локального черновика. Сначала создайте письмо на главном экране.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Письма в чате", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp).width(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        when {
            state.isLoading && state.letters.isEmpty() -> {
                Text("Загружаем письма…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.letters.isEmpty() -> {
                Text(
                    "Пока нет писем. Отправьте первый рисунок из черновика выше.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> state.letters.forEach { letter ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (letter.authorUserId == state.currentUser?.id) "Вы отправили" else "Письмо от участника",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${letter.drawing.strokes.size} штрихов · ${letter.drawing.strokes.sumOf { it.points.size }} точек",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DrawingPreview(
                            drawing = letter.drawing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
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
    }
}
