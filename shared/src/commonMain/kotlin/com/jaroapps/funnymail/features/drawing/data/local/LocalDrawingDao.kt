package com.jaroapps.funnymail.features.drawing.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDrawingDao {
    @Query("SELECT * FROM local_drawings ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<LocalDrawingEntity>>

    @Insert
    suspend fun insert(drawing: LocalDrawingEntity): Long

    @Query("DELETE FROM local_drawings WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
