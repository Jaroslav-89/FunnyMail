package com.jaroapps.funnymail.features.drawing.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual object LocalDrawingDatabaseBuilder {
    actual fun attachPlatformContext(context: Any?) = Unit

    actual fun createBuilder(): RoomDatabase.Builder<LocalDrawingDatabase> {
        val applicationSupport = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val directoryPath = requireNotNull(applicationSupport?.path) {
            "Не удалось найти каталог для локальной базы рисунков."
        }
        return Room.databaseBuilder<LocalDrawingDatabase>(
            name = "$directoryPath/funnymail_drawings.db",
        )
    }
}
