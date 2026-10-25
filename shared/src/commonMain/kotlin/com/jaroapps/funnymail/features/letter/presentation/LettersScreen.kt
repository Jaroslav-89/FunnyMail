package com.jaroapps.funnymail.features.letter.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingStroke
import com.jaroapps.funnymail.features.drawing.domain.model.SavedDrawing
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun LettersScreen(
    draftJson: String?,
    onBack: () -> Unit,
    onClearDraft: () -> Unit,
) {
    val repository = koinInject<LocalDrawingRepository>()
    val drawings by repository.observeDrawings().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val drawingCodec = remember { DrawingCodec() }
    var selectedDrawingId by remember { mutableStateOf<Long?>(null) }
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
                statusMessage = "Рисунок удалён с устройства."
            } catch (error: Exception) {
                statusMessage = error.message ?: "Не удалось удалить рисунок."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Мои рисунки",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (selectedDrawing != null) {
            Text("Рисунок #${selectedDrawing.id}", style = MaterialTheme.typography.headlineSmall)
            if (selectedDocument != null) {
                Text(
                    "${selectedDocument.strokes.size} штрихов · ${selectedDocument.strokes.sumOf { it.points.size }} точек",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                DrawingPreview(
                    drawing = selectedDocument,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Text("Не удалось прочитать сохранённый рисунок.", color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { selectedDrawingId = null }, modifier = Modifier.weight(1f)) {
                    Text("К галерее")
                }
                TextButton(onClick = { deleteDrawing(selectedDrawing) }, modifier = Modifier.weight(1f)) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
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
                                Text("Пока нет рисунков", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Создай рисунок и нажми «Сохранить рисунок». Он появится здесь и останется на этом устройстве.",
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
                                Text("Черновик не в галерее", style = MaterialTheme.typography.titleMedium)
                                DrawingPreview(draft, Modifier.fillMaxWidth().height(180.dp))
                                Text(
                                    "Можно добавить ранее сохранённый черновик в локальную библиотеку.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                repository.saveDrawing(draftJson)
                                                statusMessage = "Черновик добавлен в галерею."
                                            } catch (error: Exception) {
                                                statusMessage = error.message ?: "Не удалось добавить черновик."
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Сохранить в галерею") }
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
                            Text("Рисунок #${savedDrawing.id}", style = MaterialTheme.typography.titleMedium)
                            if (document != null) {
                                Text(
                                    "${document.strokes.size} штрихов · ${document.strokes.sumOf { it.points.size }} точек",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                DrawingPreview(document, Modifier.fillMaxWidth().height(180.dp))
                            } else {
                                Text("Не удалось прочитать рисунок. Его можно удалить.", color = MaterialTheme.colorScheme.error)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { selectedDrawingId = savedDrawing.id },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Просмотреть") }
                                TextButton(
                                    onClick = { deleteDrawing(savedDrawing) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }

        statusMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        drawing.strokes.forEach { stroke ->
            drawPreviewStroke(stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewStroke(
    stroke: DrawingStroke,
) {
    if (stroke.points.isEmpty()) return

    val lineColor = stroke.color.toPreviewColor()
    val lineWidth = (stroke.width * size.minDimension / 1000f).coerceAtLeast(1f)

    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = lineColor,
            radius = lineWidth / 2f,
            center = androidx.compose.ui.geometry.Offset(
                x = (point[0] * size.width).toFloat(),
                y = (point[1] * size.height).toFloat(),
            ),
        )
        return
    }

    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = (point[0] * size.width).toFloat()
        val y = (point[1] * size.height).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun String.toPreviewColor(): Color {
    val rgb = removePrefix("#").toLongOrNull(16) ?: 0x202124L
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}
