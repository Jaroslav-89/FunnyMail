package com.jaroapps.funnymail.features.chat.domain.repository

import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import kotlinx.coroutines.flow.Flow

/** Chat operations are exposed without Firestore-specific models or APIs. */
interface ChatRepository {
    fun observeChats(userId: String): Flow<List<Chat>>

    suspend fun createChat(
        title: String,
        creatorUserId: String,
    ): Result<Chat>

    suspend fun updateChatTitle(
        chatId: String,
        title: String,
        updatedByUserId: String,
    ): Result<Chat>

    suspend fun removeMember(
        chatId: String,
        requestingUserId: String,
        memberUserId: String,
    ): Result<Chat>

    suspend fun leaveChat(
        chatId: String,
        userId: String,
    ): Result<Unit>

    suspend fun deleteChat(
        chatId: String,
        requestingUserId: String,
    ): Result<Unit>

    suspend fun createInvite(
        chatId: String,
        createdByUserId: String,
    ): Result<ChatInvite>

    suspend fun joinWithInvite(
        inviteCode: String,
        userId: String,
    ): Result<Chat>
}
