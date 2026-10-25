package com.jaroapps.funnymail.features.drawing.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Platform-specific code supplies the database file path; database access stays shared.
 */
expect object LocalDrawingDatabaseBuilder {
    fun attachPlatformContext(context: Any?)
    fun createBuilder(): RoomDatabase.Builder<LocalDrawingDatabase>
}

fun buildLocalDrawingDatabase(): LocalDrawingDatabase =
    LocalDrawingDatabaseBuilder.createBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
