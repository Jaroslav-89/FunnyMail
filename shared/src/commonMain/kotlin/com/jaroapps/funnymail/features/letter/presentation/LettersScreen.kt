package com.jaroapps.funnymail.features.letter.presentation

import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.core.ui.AppBackButton
import com.jaroapps.funnymail.core.ui.EditPencilIcon
import com.jaroapps.funnymail.core.ui.TrashCanIcon
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingStrokes
import com.jaroapps.funnymail.features.drawing.presentation.drawDrawingBackground
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.SavedDrawing
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.jaroapps.funnymail.resources.back_to_gallery
import com.jaroapps.funnymail.resources.delete_drawing_failed
import com.jaroapps.funnymail.resources.draft_add_failed
import com.jaroapps.funnymail.resources.draft_add_hint
import com.jaroapps.funnymail.resources.draft_added_gallery
import com.jaroapps.funnymail.resources.draft_not_in_gallery
import com.jaroapps.funnymail.resources.drawing_number
import com.jaroapps.funnymail.resources.drawing_removed_device
import com.jaroapps.funnymail.resources.drawing_sent_current_chat
import com.jaroapps.funnymail.resources.empty_drawings_hint
import com.jaroapps.funnymail.resources.empty_drawings_title
import com.jaroapps.funnymail.resources.open_my_drawings
import com.jaroapps.funnymail.resources.reading_drawing_failed
import com.jaroapps.funnymail.resources.reading_saved_drawing_failed
import com.jaroapps.funnymail.resources.save_to_gallery
import com.jaroapps.funnymail.resources.send_to_chat
import com.jaroapps.funnymail.resources.sending
import com.jaroapps.funnymail.resources.stroke_point_count
import com.jaroapps.funnymail.resources.view
import com.jaroapps.funnymail.resources.Res

@Composable
fun LettersScreen(
    draftJson: String?,
    onBack: () -> Unit,
    onClearDraft: () -> Unit,
    onEditDrawing: (Long, String) -> Unit,
    onSendDrawing: (String, (String?) -> Unit) -> Unit,
) {
    val repository = koinInject<LocalDrawingRepository>()
    val drawingsFlow = remember(repository) { repository.observeDrawings() }
    val drawings by drawingsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val drawingCodec = remember { DrawingCodec() }
    val drawingRemovedDeviceText = stringResource(Res.string.drawing_removed_device)
    val deleteDrawingFailedText = stringResource(Res.string.delete_drawing_failed)
    val drawingSentCurrentChatText = stringResource(Res.string.drawing_sent_current_chat)
    val readingSavedDrawingFailedText = stringResource(Res.string.reading_saved_drawing_failed)
    val draftAddedGalleryText = stringResource(Res.string.draft_added_gallery)
    val draftAddFailedText = stringResource(Res.string.draft_add_failed)
    val readingDrawingFailedText = stringResource(Res.string.reading_drawing_failed)
    var selectedDrawingId by remember { mutableStateOf<Long?>(null) }
    var sendingDrawingId by remember { mutableStateOf<Long?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val selectedDrawing = drawings.firstOrNull { it.id == selectedDrawingId }
    val selectedDocument = remember(selectedDrawing?.drawingJson) {
        selectedDrawing?.drawingJson?.let { drawingCodec.decode(it).getOrNull() }
    }
    val draft = remember(draftJson) {
        draftJson?.let { drawingCodec.decode(it).getOrNull() }
    }
    val draftNeedsImport = draftJson != null &&
        draft != null &&
        drawings.none { it.drawingJson == draftJson }

    fun deleteDrawing(savedDrawing: SavedDrawing) {
        coroutineScope.launch {
            try {
                repository.deleteDrawing(savedDrawing.id)
                if (draftJson == savedDrawing.drawingJson) onClearDraft()
                if (selectedDrawingId == savedDrawing.id) selectedDrawingId = null
                statusMessage = drawingRemovedDeviceText
            } catch (error: Exception) {
                statusMessage = deleteDrawingFailedText
            }
        }
    }

    fun sendDrawing(savedDrawing: SavedDrawing) {
        if (sendingDrawingId != null) return
        sendingDrawingId = savedDrawing.id
        onSendDrawing(savedDrawing.drawingJson) { error ->
            sendingDrawingId = null
            statusMessage = if (error == null) {
                drawingSentCurrentChatText
            } else {
                error
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedDrawing != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.drawing_number, selectedDrawing.id),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(
                    onClick = {
                        onEditDrawing(selectedDrawing.id, selectedDrawing.drawingJson)
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    EditPencilIcon(color = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { deleteDrawing(selectedDrawing) },
                    modifier = Modifier.size(40.dp),
                ) {
                    TrashCanIcon(color = MaterialTheme.colorScheme.error)
                }
            }
            if (selectedDocument != null) {
                Text(
                    stringResource(Res.string.stroke_point_count, selectedDocument.strokes.size, selectedDocument.strokes.sumOf { it.points.size }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                DrawingPreview(
                    drawing = selectedDocument,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Text(stringResource(Res.string.reading_saved_drawing_failed), color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = { selectedDrawingId = null },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.back_to_gallery))
            }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (drawings.isEmpty() && !draftNeedsImport) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(Res.string.empty_drawings_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(Res.string.empty_drawings_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                if (draftNeedsImport && draft != null && draftJson != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(Res.string.draft_not_in_gallery), style = MaterialTheme.typography.titleMedium)
                                DrawingPreview(draft, Modifier.fillMaxWidth().height(180.dp))
                                Text(
                                    stringResource(Res.string.draft_add_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                repository.saveDrawing(draftJson)
                                                statusMessage = draftAddedGalleryText
                                            } catch (error: Exception) {
                                                statusMessage = draftAddFailedText
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(Res.string.save_to_gallery)) }
                            }
                        }
                    }
                }

                items(drawings, key = { it.id }) { savedDrawing ->
                    val document = remember(savedDrawing.drawingJson) {
                        drawingCodec.decode(savedDrawing.drawingJson).getOrNull()
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(Res.string.drawing_number, savedDrawing.id),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                IconButton(
                                    onClick = {
                                        onEditDrawing(savedDrawing.id, savedDrawing.drawingJson)
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    EditPencilIcon(color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { deleteDrawing(savedDrawing) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    TrashCanIcon(color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (document != null) {
                                Text(
                                    stringResource(Res.string.stroke_point_count, document.strokes.size, document.strokes.sumOf { it.points.size }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                DrawingPreview(document, Modifier.fillMaxWidth().height(180.dp))
                            } else {
                                Text(stringResource(Res.string.reading_drawing_failed), color = MaterialTheme.colorScheme.error)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { selectedDrawingId = savedDrawing.id },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(Res.string.view)) }
                                Button(
                                    onClick = { sendDrawing(savedDrawing) },
                                    enabled = document != null && sendingDrawingId == null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (sendingDrawingId == savedDrawing.id) stringResource(Res.string.sending) else stringResource(Res.string.send_to_chat))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBackButton(onClick = onBack)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
            ) {
                Text(
                    text = stringResource(Res.string.open_my_drawings),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
        }

        statusMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun DrawingPreview(
    drawing: DrawingDocument,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Canvas(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        drawDrawingBackground(drawing.canvas)
        drawDrawingStrokes(
            strokes = drawing.strokes,
            canvasWidth = drawing.canvas.width,
            canvasHeight = drawing.canvas.height,
            contentInsetFraction = 0.06f,
        )
    }
}

@Composable
private fun DrawingsBackArrowGlyph(color: Color) {
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
