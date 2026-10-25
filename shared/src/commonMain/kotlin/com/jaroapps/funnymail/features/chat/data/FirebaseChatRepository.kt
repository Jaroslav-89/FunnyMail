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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Clock

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
                    description = "",
                    memberIds = listOf(creatorUserId),
                    creatorUserId = creatorUserId,
                ),
            )
        reference.get().data<FirestoreChatDocument>().toDomain(reference.id)
    }


    override suspend fun updateChatTitle(
        chatId: String,
        title: String,
        updatedByUserId: String,
    ): Result<Chat> = asResult {
        val normalizedTitle = title.trim()
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(updatedByUserId.isNotBlank()) { "Для изменения чата нужно войти в аккаунт." }
        require(normalizedTitle.isNotEmpty()) { "Введите название чата." }
        require(normalizedTitle.length <= MAX_CHAT_TITLE_LENGTH) {
            "Название чата не должно превышать $MAX_CHAT_TITLE_LENGTH символов."
        }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == updatedByUserId) {
            "Изменять название чата может только его создатель."
        }

        chatReference.update(
            "title" to normalizedTitle,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }


    override suspend fun updateChatDescription(
        chatId: String,
        description: String,
        updatedByUserId: String,
    ): Result<Chat> = asResult {
        val normalizedDescription = description.trim()
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(updatedByUserId.isNotBlank()) { "Для изменения чата нужно войти в аккаунт." }
        require(normalizedDescription.length <= MAX_CHAT_DESCRIPTION_LENGTH) {
            "Описание чата не должно превышать $MAX_CHAT_DESCRIPTION_LENGTH символов."
        }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == updatedByUserId) {
            "Изменять описание чата может только его создатель."
        }

        chatReference.update(
            "description" to normalizedDescription,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }

    override fun observeUnreadCount(chatId: String, userId: String): Flow<Int> {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(userId.isNotBlank()) { "Для подсчёта непрочитанных сообщений нужен аккаунт." }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val lettersFlow = chatReference.collection(LETTERS_COLLECTION).snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { document ->
                runCatching { document.data<FirestoreUnreadLetterDocument>() }.getOrNull()
            }
        }
        val readAtFlow = chatReference.collection(READS_COLLECTION).document(userId).snapshots.map { document ->
            if (!document.exists) {
                0L
            } else {
                runCatching {
                    document.data<FirestoreChatReadDocument>().readAt.toEpochMillisOrNull() ?: 0L
                }.getOrDefault(0L)
            }
        }

        return combine(lettersFlow, readAtFlow) { letters, readAt ->
            val now = Clock.System.now().toEpochMilliseconds()
            letters.count { letter ->
                val createdAt = letter.createdAt.toEpochMillisOrNull()
                val expiresAt = letter.expiresAt.toEpochMillisOrNull()
                letter.authorUserId != userId &&
                    createdAt != null && createdAt > readAt &&
                    expiresAt != null && expiresAt > now
            }
        }
    }

    override suspend fun markChatAsRead(chatId: String, userId: String): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(userId.isNotBlank()) { "Для отметки о прочтении нужен аккаунт." }

        firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(READS_COLLECTION)
            .document(userId)
            .set(FirestoreChatReadDocument())
    }

    override suspend fun addMember(
        chatId: String,
        requestingUserId: String,
        memberUserId: String,
    ): Result<Chat> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(requestingUserId.isNotBlank()) { "Сначала войдите в аккаунт." }
        require(memberUserId.isNotBlank()) { "Пользователь не найден." }
        require(memberUserId != requestingUserId) { "Вы уже состоите в этом чате." }

        val firestore = firestoreProvider()
        val chatReference = firestore.collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(requestingUserId in current.memberIds) { "Вы больше не состоите в этом чате." }
        require(memberUserId !in current.memberIds) { "Пользователь уже состоит в этом чате." }
        require(
            firestore.collection("publicProfiles").document(memberUserId).get().exists,
        ) { "Пользователь не найден." }

        chatReference.update(
            "memberIds" to FieldValue.arrayUnion(memberUserId),
            "updatedAt" to FieldValue.serverTimestamp,
        )
        chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }

    override suspend fun removeMember(
        chatId: String,
        requestingUserId: String,
        memberUserId: String,
    ): Result<Chat> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(requestingUserId.isNotBlank()) { "Для управления участниками нужно войти в аккаунт." }
        require(memberUserId.isNotBlank()) { "Не выбран участник." }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == requestingUserId) {
            "Удалять участников может только создатель чата."
        }
        require(memberUserId != current.creatorUserId) { "Создателя нельзя удалить из чата." }
        require(memberUserId in current.memberIds) { "Участник уже не состоит в чате." }

        chatReference.update(
            "memberIds" to FieldValue.arrayRemove(memberUserId),
            "updatedAt" to FieldValue.serverTimestamp,
        )
        chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }

    override suspend fun leaveChat(
        chatId: String,
        userId: String,
    ): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(userId.isNotBlank()) { "Для выхода из чата нужно войти в аккаунт." }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(userId in current.memberIds) { "Вы уже не состоите в этом чате." }
        require(userId != current.creatorUserId) {
            "Создатель не может выйти из чата. Вместо этого удалите чат."
        }

        chatReference.update(
            "memberIds" to FieldValue.arrayRemove(userId),
            "updatedAt" to FieldValue.serverTimestamp,
        )
    }

    override suspend fun deleteChat(
        chatId: String,
        requestingUserId: String,
    ): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(requestingUserId.isNotBlank()) { "Для удаления чата нужно войти в аккаунт." }

        val firestore = firestoreProvider()
        val chatReference = firestore.collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == requestingUserId) {
            "Удалять чат может только его создатель."
        }

        // Firestore does not cascade-delete subcollections, so remove each letter first.
        val letters = chatReference.collection(LETTERS_COLLECTION).get()
        letters.documents.forEach { document -> document.reference.delete() }
        chatReference.delete()
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
        description = description,
        memberIds = memberIds,
        creatorUserId = creatorUserId.ifBlank { memberIds.firstOrNull().orEmpty() },
        createdAtEpochMillis = createdAt.requireEpochMillis("createdAt"),
        updatedAtEpochMillis = updatedAt.requireEpochMillis("updatedAt"),
        lastMessageAtEpochMillis = (lastMessageAt as? Timestamp)?.toMilliseconds()?.toLong(),
    )

    private fun BaseTimestamp.toEpochMillisOrNull(): Long? =
        (this as? Timestamp)?.toMilliseconds()?.toLong()

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
        val description: String = "",
        val memberIds: List<String>,
        val creatorUserId: String = "",
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

    @Serializable
    private data class FirestoreChatReadDocument(
        val readAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    @Serializable
    private data class FirestoreUnreadLetterDocument(
        val authorUserId: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
        val expiresAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    private companion object {
        const val CHATS_COLLECTION = "chats"
        const val LETTERS_COLLECTION = "letters"
        const val READS_COLLECTION = "reads"
        const val INVITES_COLLECTION = "chatInvites"
        const val MAX_CHAT_TITLE_LENGTH = 80
        const val MAX_CHAT_DESCRIPTION_LENGTH = 120
        const val INVITE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
