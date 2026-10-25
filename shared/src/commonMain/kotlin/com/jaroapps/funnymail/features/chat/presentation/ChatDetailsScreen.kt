package com.jaroapps.funnymail.features.chat.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaroapps.funnymail.core.ui.AccountPersonIcon
import com.jaroapps.funnymail.core.ui.DrawingListIcon
import com.jaroapps.funnymail.core.ui.EditPencilIcon
import com.jaroapps.funnymail.core.ui.PersonPlusIcon
import com.jaroapps.funnymail.core.ui.PaperPlusIcon
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import com.jaroapps.funnymail.features.letter.presentation.DrawingPreview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatDetailsScreen(
    chat: Chat,
    onBack: () -> Unit,
    onEditChat: () -> Unit,
    onCreateDrawing: () -> Unit,
    onOpenDrawings: () -> Unit,
    onViewDrawing: (DrawingDocument) -> Unit,
    onCreateInvite: (String, (ChatInvite?, String?) -> Unit) -> Unit,
) {
    val viewModel: ChatDetailsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val lettersListState = rememberLazyListState()
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var isCreatingInvite by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<ChatInvite?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(chat.id) {
        viewModel.observeLetters(chat.id)
        onDispose { viewModel.stopObserving(chat.id) }
    }

    LaunchedEffect(state.letters.size, state.isLoading) {
        if (state.letters.isNotEmpty()) {
            lettersListState.animateScrollToItem(state.letters.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatHeaderIconButton(onClick = onBack, description = "Назад") {
                    BackArrowGlyph(color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chat.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = chat.memberIds.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountPersonIcon(
                            modifier = Modifier.padding(start = 3.dp).size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ChatHeaderIconButton(onClick = onEditChat, description = "Редактировать чат") {
                    EditPencilIcon(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ChatHeaderIconButton(
                    onClick = {
                        generatedInvite = null
                        inviteError = null
                        showInviteDialog = true
                    },
                    description = "Добавить пользователя в чат",
                ) {
                    PersonPlusIcon(
                        modifier = Modifier.size(23.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoading && state.letters.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.letters.isEmpty() -> {
                        Text(
                            text = "Пока нет писем. Отправьте рисунок в чат.",
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = lettersListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 100.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.letters, key = { it.id }) { letter ->
                                ChatLetterCard(
                                    letter = letter,
                                    currentUserId = state.currentUser?.id,
                                    onClick = { onViewDrawing(letter.drawing) },
                                )
                            }
                        }
                    }
                }

                state.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .background(
                                color = if (state.messageIsError) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (state.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
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
                        modifier = Modifier.size(52.dp).semantics { contentDescription = "Новый рисунок" },
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
                        modifier = Modifier.size(52.dp).semantics { contentDescription = "Мои рисунки" },
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
                modifier = Modifier.size(58.dp).semantics {
                    contentDescription = if (actionsExpanded) "Закрыть меню действий" else "Открыть меню действий"
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
                onDismissRequest = { if (!isCreatingInvite) showInviteDialog = false },
                title = { Text("Пригласить пользователя") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Создайте код и передайте его человеку, которого хотите пригласить.")
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
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else Text("Создать код приглашения")
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
                                "Код действует 7 дней.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInviteDialog = false }) { Text("Готово") }
                },
            )
        }
    }
}

@Composable
private fun ChatLetterCard(
    letter: Letter,
    currentUserId: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (letter.authorUserId == currentUserId) "Вы отправили" else "Письмо от участника",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${letter.drawing.strokes.size} штрихов · ${letter.drawing.strokes.sumOf { it.points.size }} точек",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DrawingPreview(
                drawing = letter.drawing,
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = "Открыть рисунок на весь экран" },
            )
        }
    }
}

@Composable
private fun ChatHeaderIconButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BackArrowGlyph(color: Color) {
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
            style = Stroke(
                width = size.minDimension * 0.095f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
