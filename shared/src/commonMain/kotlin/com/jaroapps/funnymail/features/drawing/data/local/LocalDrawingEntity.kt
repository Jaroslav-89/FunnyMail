package com.jaroapps.funnymail.features.drawing.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "local_drawings")
data class LocalDrawingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val drawingJson: String,
    val createdAtEpochMillis: Long,
)
