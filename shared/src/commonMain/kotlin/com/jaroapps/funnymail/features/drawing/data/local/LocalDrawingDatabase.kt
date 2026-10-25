package com.jaroapps.funnymail.features.drawing.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [LocalDrawingEntity::class],
    version = 1,
)
@ConstructedBy(LocalDrawingDatabaseConstructor::class)
abstract class LocalDrawingDatabase : RoomDatabase() {
    abstract fun localDrawingDao(): LocalDrawingDao
}

@Suppress("KotlinNoActualForExpect")
expect object LocalDrawingDatabaseConstructor : RoomDatabaseConstructor<LocalDrawingDatabase> {
    override fun initialize(): LocalDrawingDatabase
}
