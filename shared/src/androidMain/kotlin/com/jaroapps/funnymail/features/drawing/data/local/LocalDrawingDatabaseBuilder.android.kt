package com.jaroapps.funnymail.features.drawing.data.local

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

actual object LocalDrawingDatabaseBuilder {
    private var applicationContext: Context? = null

    actual fun attachPlatformContext(context: Any?) {
        applicationContext = (context as? Context)?.applicationContext
    }

    actual fun createBuilder(): RoomDatabase.Builder<LocalDrawingDatabase> {
        val context = checkNotNull(applicationContext) {
            "Локальная база рисунков ещё не подключена к Android Activity."
        }
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        return Room.databaseBuilder<LocalDrawingDatabase>(
            context = context,
            name = databaseFile.absolutePath,
        )
    }

    private const val DATABASE_NAME = "funnymail_drawings.db"
}
