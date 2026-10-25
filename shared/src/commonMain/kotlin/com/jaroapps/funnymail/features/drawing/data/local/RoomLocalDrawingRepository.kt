package com.jaroapps.funnymail.features.drawing.data.local

import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.SavedDrawing
import com.jaroapps.funnymail.features.drawing.domain.repository.LocalDrawingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class RoomLocalDrawingRepository(
    private val dao: LocalDrawingDao,
    private val drawingCodec: DrawingCodec,
) : LocalDrawingRepository {
    override fun observeDrawings(): Flow<List<SavedDrawing>> =
        dao.observeAll().map { entities ->
            entities.map { entity ->
                SavedDrawing(
                    id = entity.id,
                    drawingJson = entity.drawingJson,
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                )
            }
        }

    override suspend fun saveDrawing(drawingJson: String): Long {
        val validation = drawingCodec.decode(drawingJson)
        require(validation.isSuccess) {
            validation.exceptionOrNull()?.message ?: "Не удалось проверить рисунок."
        }

        return dao.insert(
            LocalDrawingEntity(
                drawingJson = drawingJson,
                createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun deleteDrawing(id: Long) {
        dao.deleteById(id)
    }
}
