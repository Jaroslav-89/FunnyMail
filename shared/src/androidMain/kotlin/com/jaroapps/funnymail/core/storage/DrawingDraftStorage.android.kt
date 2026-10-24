package com.jaroapps.funnymail.core.storage

import android.content.Context

actual object DrawingDraftStorage {
    private var applicationContext: Context? = null

    actual fun attachPlatformContext(context: Any?) {
        applicationContext = (context as? Context)?.applicationContext
    }

    actual fun load(): String? =
        applicationContext
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.getString(DRAFT_KEY, null)

    actual fun save(serializedDrawing: String) {
        val context = checkNotNull(applicationContext) {
            "Локальное хранилище черновиков ещё не подключено к Android Activity."
        }
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(DRAFT_KEY, serializedDrawing)
            .apply()
    }

    actual fun clear() {
        applicationContext
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(DRAFT_KEY)
            ?.apply()
    }

    private const val PREFERENCES_NAME = "funnymail_local_drafts"
    private const val DRAFT_KEY = "latest_drawing_json"
}
