package com.jaroapps.funnymail.features.chat.domain.model

data class Chat(
    val id: String,
    val title: String,
    val memberIds: List<String>,
    /** Unix epoch milliseconds, assigned by the persistence/backend layer. */
    val createdAtEpochMillis: Long,
    /** Unix epoch milliseconds, updated when chat activity changes. */
    val updatedAtEpochMillis: Long,
)
