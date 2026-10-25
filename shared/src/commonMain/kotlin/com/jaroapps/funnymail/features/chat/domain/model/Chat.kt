package com.jaroapps.funnymail.features.chat.domain.model

data class Chat(
    val id: String,
    val title: String,
    val description: String = "",
    val memberIds: List<String>,
    /** Unix epoch milliseconds, assigned by the persistence/backend layer. */
    val createdAtEpochMillis: Long,
    /** Unix epoch milliseconds, updated when chat membership changes. */
    val updatedAtEpochMillis: Long,
    /** Unix epoch milliseconds of the newest sent letter, or null if the chat is empty. */
    val lastMessageAtEpochMillis: Long? = null,
    /** Creator of the chat; for legacy chats the first member is treated as creator. */
    val creatorUserId: String = memberIds.firstOrNull().orEmpty(),
)
