package com.jaroapps.funnymail.features.letter.domain.model

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument

data class Letter(
    val id: String,
    val chatId: String,
    val authorUserId: String,
    val drawing: DrawingDocument,
    /** Unix epoch milliseconds, returned by the backend. */
    val createdAtEpochMillis: Long,
    /** Unix epoch milliseconds; the retention policy is enforced server-side. */
    val expiresAtEpochMillis: Long,
)
