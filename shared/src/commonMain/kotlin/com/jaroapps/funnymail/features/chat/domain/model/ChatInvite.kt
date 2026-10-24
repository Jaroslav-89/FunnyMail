package com.jaroapps.funnymail.features.chat.domain.model

data class ChatInvite(
    /** Opaque invite code; it should not contain personal information. */
    val code: String,
    val chatId: String,
    val createdByUserId: String,
    /** Unix epoch milliseconds, assigned by the persistence/backend layer. */
    val createdAtEpochMillis: Long,
    /** Invite expiry is enforced by the backend, not only by the client. */
    val expiresAtEpochMillis: Long,
)
