package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaroapps.funnymail.core.ui.DrawingListIcon
import com.jaroapps.funnymail.core.ui.PersonPlusIcon
import com.jaroapps.funnymail.core.ui.PaperPlusIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
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
    onCreateDrawing: () -> Unit,
    onOpenDrawings: () -> Unit,
    onViewDrawing: (DrawingDocument) -> Unit,
    onCreateInvite: (String, (ChatInvite?, String?) -> Unit) -> Unit,
) {
    val viewModel: ChatDetailsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val draft: DrawingDocument? = remember(draftJson) {
        draftJson?.let { DrawingCodec().decode(it).getOrNull() }
    }
    var handledSentLetterId by remember { mutableStateOf(state.lastSentLetterId) }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var isCreatingInvite by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<ChatInvite?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(chat.id) {
        viewModel.observeLetters(chat.id)
        onDispose { viewModel.stopObserving(chat.id) }
    }
    LaunchedEffect(state.lastSentLetterId) {
        val sentLetterId = state.lastSentLetterId
        if (sentLetterId != null && sentLetterId != handledSentLetterId) {
            handledSentLetterId = sentLetterId
            onDraftSent()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 104.dp),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chat.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
            )
            IconButton(
                onClick = {
                    generatedInvite = null
                    inviteError = null
                    showInviteDialog = true
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Пригласить друга" },
            ) {
                PersonPlusIcon(color = MaterialTheme.colorScheme.primary)
            }
        }
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
                                .height(220.dp)
                                .semantics {
                                    contentDescription = "Открыть рисунок на весь экран"
                                }
                                .clickable { onViewDrawing(letter.drawing) },
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

    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = actionsExpanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(
                    onClick = {
                        actionsExpanded = false
                        onCreateDrawing()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics { contentDescription = "Новый рисунок" },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    PaperPlusIcon(color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                FloatingActionButton(
                    onClick = {
                        actionsExpanded = false
                        onOpenDrawings()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics { contentDescription = "Мои рисунки" },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    DrawingListIcon(color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        FloatingActionButton(
            onClick = { actionsExpanded = !actionsExpanded },
            modifier = Modifier
                .size(58.dp)
                .semantics {
                    contentDescription = if (actionsExpanded) {
                        "Закрыть меню действий"
                    } else {
                        "Открыть меню действий"
                    }
                },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = "+",
                fontSize = 32.sp,
                lineHeight = 32.sp,
                modifier = Modifier.rotate(if (actionsExpanded) 45f else 0f),
            )
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingInvite) showInviteDialog = false
            },
            title = { Text("Пригласить друга") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Выберите способ приглашения в чат.")
                    OutlinedButton(
                        onClick = {
                            if (!isCreatingInvite) {
                                isCreatingInvite = true
                                inviteError = null
                                onCreateInvite(chat.id) { invite, error ->
                                    isCreatingInvite = false
                                    generatedInvite = invite
                                    inviteError = error
                                }
                            }
                        },
                        enabled = !isCreatingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCreatingInvite) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("По коду приглашения")
                        }
                    }
                    generatedInvite?.let { invite ->
                        Text("Код приглашения", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(
                                text = invite.code,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "Передайте код другу. Он действует 7 дней.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inviteError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Готово")
                }
            },
        )
    }
    }
}
