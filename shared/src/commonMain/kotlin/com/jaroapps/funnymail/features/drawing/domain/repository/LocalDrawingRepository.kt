package com.jaroapps.funnymail.features.drawing.domain.repository

import com.jaroapps.funnymail.features.drawing.domain.model.SavedDrawing
import kotlinx.coroutines.flow.Flow

/** Local-only storage for drawings saved on the current device. */
interface LocalDrawingRepository {
    fun observeDrawings(): Flow<List<SavedDrawing>>
    suspend fun saveDrawing(drawingJson: String): Long
    suspend fun updateDrawing(id: Long, drawingJson: String)
    suspend fun deleteDrawing(id: Long)
}
