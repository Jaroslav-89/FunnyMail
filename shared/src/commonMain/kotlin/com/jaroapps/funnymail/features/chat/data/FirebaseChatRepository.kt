package com.jaroapps.funnymail.features.chat.data

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import com.jaroapps.funnymail.features.chat.domain.model.ChatInvite
import com.jaroapps.funnymail.features.chat.domain.repository.ChatRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Firestore adapter for chat and invite contracts.
 *
 * Invite expiration is based on the server-created timestamp and is enforced again by
 * firestore.rules; the client-side clock is not the authority for accepting an invite.
 */
class FirebaseChatRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase не настроен. Добавьте файлы конфигурации платформы."
        }
        Firebase.firestore
    },
) : ChatRepository {

    override fun observeChats(userId: String): Flow<List<Chat>> {
        require(userId.isNotBlank()) { "Для загрузки чатов нужен аккаунт." }
        return firestoreProvider()
            .collection(CHATS_COLLECTION)
            .where("memberIds", arrayContains = userId)
            .snapshots
            .map { snapshot ->
                snapshot.documents.mapNotNull { document ->
                    runCatching {
                        document.data<FirestoreChatDocument>().toDomain(document.id)
                    }.getOrNull()
                }.map { chat ->
                    if (chat.lastMessageAtEpochMillis != null) {
                        chat
                    } else {
                        // Backfill sort order in memory for chats created before lastMessageAt
                        // was introduced. Once a new letter is sent, the server timestamp is
                        // persisted on the chat document and no extra query is required.
                        val lastLetterAt = try {
                            findLatestLetterTimestamp(chat.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        chat.copy(lastMessageAtEpochMillis = lastLetterAt)
                    }
                }.sortedByDescending { it.lastMessageAtEpochMillis ?: it.updatedAtEpochMillis }
            }
    }

    private suspend fun findLatestLetterTimestamp(chatId: String): Long? {
        val snapshot = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(LETTERS_COLLECTION)
            .orderBy("createdAt", Direction.DESCENDING)
            .limit(1)
            .get()

        return snapshot.documents.firstOrNull()
            ?.data<FirestoreLatestLetterTimestamp>()
            ?.createdAt
            ?.let { (it as? Timestamp)?.toMilliseconds()?.toLong() }
    }

    override suspend fun createChat(
        title: String,
        creatorUserId: String,
    ): Result<Chat> = asResult {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Введите название чата." }
        require(normalizedTitle.length <= MAX_CHAT_TITLE_LENGTH) {
            "Название чата не должно превышать $MAX_CHAT_TITLE_LENGTH символов."
        }
        require(creatorUserId.isNotBlank()) { "Для создания чата нужно войти в аккаунт." }

        val reference = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .add(
                FirestoreChatDocument(
                    title = normalizedTitle,
                    memberIds = listOf(creatorUserId),
                ),
            )
        reference.get().data<FirestoreChatDocument>().toDomain(reference.id)
    }

    override suspend fun createInvite(
        chatId: String,
        createdByUserId: String,
    ): Result<ChatInvite> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(createdByUserId.isNotBlank()) { "Для создания приглашения нужно войти в аккаунт." }

        val reference = firestoreProvider()
            .collection(INVITES_COLLECTION)
            .add(
                FirestoreInviteDocument(
                    chatId = chatId,
                    createdByUserId = createdByUserId,
                ),
            )
        val created = reference.get().data<FirestoreInviteDocument>()
        val createdAt = created.createdAt.requireEpochMillis("createdAt")

        ChatInvite(
            code = reference.id,
            chatId = created.chatId,
            createdByUserId = created.createdByUserId,
            createdAtEpochMillis = createdAt,
            expiresAtEpochMillis = createdAt + INVITE_TTL_MILLIS,
        )
    }

    override suspend fun joinWithInvite(
        inviteCode: String,
        userId: String,
    ): Result<Chat> = asResult {
        val normalizedCode = inviteCode.trim()
        require(normalizedCode.isNotEmpty()) { "Введите код приглашения." }
        require(userId.isNotBlank()) { "Для вступления в чат нужно войти в аккаунт." }

        val firestore = firestoreProvider()
        val inviteSnapshot = firestore
            .collection(INVITES_COLLECTION)
            .document(normalizedCode)
            .get()
        require(inviteSnapshot.exists) {
            "Приглашение не найдено или уже недействительно."
        }

        val invite = inviteSnapshot.data<FirestoreInviteDocument>()
        val chatReference = firestore
            .collection(CHATS_COLLECTION)
            .document(invite.chatId)

        // arrayUnion is atomic. Firestore rules verify that this code belongs to the
        // target chat, is still valid according to request.time, and was created by a member.
        chatReference.update(
            "memberIds" to FieldValue.arrayUnion(userId),
            "updatedAt" to FieldValue.serverTimestamp,
            "lastInviteCode" to normalizedCode,
        )

        chatReference.get().data<FirestoreChatDocument>().toDomain(chatReference.id)
    }

    private fun FirestoreChatDocument.toDomain(id: String): Chat = Chat(
        id = id,
        title = title,
        memberIds = memberIds,
        createdAtEpochMillis = createdAt.requireEpochMillis("createdAt"),
        updatedAtEpochMillis = updatedAt.requireEpochMillis("updatedAt"),
        lastMessageAtEpochMillis = (lastMessageAt as? Timestamp)?.toMilliseconds()?.toLong(),
    )

    private fun BaseTimestamp.requireEpochMillis(field: String): Long =
        (this as? Timestamp)?.toMilliseconds()?.toLong()
            ?: error("Firestore не вернул серверное время для поля $field.")

    private suspend fun <T> asResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    @Serializable
    private data class FirestoreChatDocument(
        val title: String,
        val memberIds: List<String>,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
        val updatedAt: BaseTimestamp = Timestamp.ServerTimestamp,
        val lastInviteCode: String = "",
        val lastMessageAt: BaseTimestamp? = null,
    )

    @Serializable
    private data class FirestoreInviteDocument(
        val chatId: String,
        val createdByUserId: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    @Serializable
    private data class FirestoreLatestLetterTimestamp(
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    private companion object {
        const val CHATS_COLLECTION = "chats"
        const val INVITES_COLLECTION = "chatInvites"
        const val MAX_CHAT_TITLE_LENGTH = 80
        const val INVITE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
