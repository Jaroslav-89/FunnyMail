package com.jaroapps.funnymail.features.chat.data

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.chat.domain.model.Chat
import org.jetbrains.compose.resources.getString
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import com.jaroapps.funnymail.resources.chat_already_left
import com.jaroapps.funnymail.resources.chat_already_member
import com.jaroapps.funnymail.resources.chat_cannot_remove_creator
import com.jaroapps.funnymail.resources.chat_cleanup_login_required
import com.jaroapps.funnymail.resources.chat_cleanup_members_only
import com.jaroapps.funnymail.resources.chat_create_login_required
import com.jaroapps.funnymail.resources.chat_creator_cannot_leave
import com.jaroapps.funnymail.resources.chat_delete_creator_only
import com.jaroapps.funnymail.resources.chat_delete_login_required
import com.jaroapps.funnymail.resources.chat_description_creator_only
import com.jaroapps.funnymail.resources.chat_description_too_long
import com.jaroapps.funnymail.resources.chat_drawing_limit_range
import com.jaroapps.funnymail.resources.chat_enter_title
import com.jaroapps.funnymail.resources.chat_invite_code_required
import com.jaroapps.funnymail.resources.chat_invite_invalid
import com.jaroapps.funnymail.resources.chat_invite_login_required
import com.jaroapps.funnymail.resources.chat_join_login_required
import com.jaroapps.funnymail.resources.chat_leave_login_required
import com.jaroapps.funnymail.resources.chat_limit_creator_only
import com.jaroapps.funnymail.resources.chat_mark_read_login_required
import com.jaroapps.funnymail.resources.chat_member_already_left
import com.jaroapps.funnymail.resources.chat_member_not_selected
import com.jaroapps.funnymail.resources.chat_members_manage_login_required
import com.jaroapps.funnymail.resources.chat_not_member_anymore
import com.jaroapps.funnymail.resources.chat_not_selected_error
import com.jaroapps.funnymail.resources.chat_remove_member_creator_only
import com.jaroapps.funnymail.resources.chat_settings_login_required
import com.jaroapps.funnymail.resources.chat_target_already_member
import com.jaroapps.funnymail.resources.chat_title_creator_only
import com.jaroapps.funnymail.resources.chat_title_too_long
import com.jaroapps.funnymail.resources.chat_update_login_required
import com.jaroapps.funnymail.resources.chat_user_not_found
import com.jaroapps.funnymail.resources.Res
import com.jaroapps.funnymail.resources.letter_login_required

/**
 * Firestore adapter for chat and invite contracts.
 *
 * Invite expiration is based on the server-created timestamp and is enforced again by
 * firestore.rules; the client-side clock is not the authority for accepting an invite.
 */
class FirebaseChatRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase is not configured. Add platform configuration files."
        }
        Firebase.firestore
    },
) : ChatRepository {

    override fun observeChats(userId: String): Flow<List<Chat>> {
        require(userId.isNotBlank()) { "Sign in to load chats." }
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
        require(normalizedTitle.isNotEmpty()) { getString(Res.string.chat_enter_title) }
        require(normalizedTitle.length <= MAX_CHAT_TITLE_LENGTH) {
            getString(Res.string.chat_title_too_long, MAX_CHAT_TITLE_LENGTH)
        }
        require(creatorUserId.isNotBlank()) { getString(Res.string.chat_create_login_required) }

        val reference = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .add(
                FirestoreChatDocument(
                    title = normalizedTitle,
                    description = "",
                    maxLettersCount = null,
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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(updatedByUserId.isNotBlank()) { getString(Res.string.chat_update_login_required) }
        require(normalizedTitle.isNotEmpty()) { getString(Res.string.chat_enter_title) }
        require(normalizedTitle.length <= MAX_CHAT_TITLE_LENGTH) {
            getString(Res.string.chat_title_too_long, MAX_CHAT_TITLE_LENGTH)
        }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == updatedByUserId) {
            getString(Res.string.chat_title_creator_only)
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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(updatedByUserId.isNotBlank()) { getString(Res.string.chat_update_login_required) }
        require(normalizedDescription.length <= MAX_CHAT_DESCRIPTION_LENGTH) {
            getString(Res.string.chat_description_too_long, MAX_CHAT_DESCRIPTION_LENGTH)
        }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == updatedByUserId) {
            getString(Res.string.chat_description_creator_only)
        }

        chatReference.update(
            "description" to normalizedDescription,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }

    override suspend fun updateChatMaxLettersCount(
        chatId: String,
        maxLettersCount: Int?,
        updatedByUserId: String,
    ): Result<Chat> = asResult {
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(updatedByUserId.isNotBlank()) { getString(Res.string.chat_settings_login_required) }
        require(maxLettersCount == null || maxLettersCount in 1..MAX_CHAT_LETTERS_LIMIT) {
            getString(Res.string.chat_drawing_limit_range, MAX_CHAT_LETTERS_LIMIT)
        }

        val reference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = reference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == updatedByUserId) {
            getString(Res.string.chat_limit_creator_only)
        }
        reference.update(
            "maxLettersCount" to maxLettersCount,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        reference.get().data<FirestoreChatDocument>().toDomain(chatId)
    }

    override suspend fun requestAutomaticCleanup(
        chatId: String,
        requestingUserId: String,
    ): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(requestingUserId.isNotBlank()) { getString(Res.string.chat_cleanup_login_required) }

        val firestore = firestoreProvider()
        val chatReference = firestore.collection(CHATS_COLLECTION).document(chatId)
        val chat = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(requestingUserId in chat.memberIds) { getString(Res.string.chat_cleanup_members_only) }
        if (chat.maxLettersCount != null) {
            chatReference.collection(CLEANUP_REQUESTS_COLLECTION).add(
                FirestoreCleanupRequestDocument(requestedByUserId = requestingUserId),
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadCount(chatId: String, userId: String): Flow<Int> {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val readAtFlow = chatReference.collection(READS_COLLECTION).document(userId).snapshots.map { document ->
            if (!document.exists) {
                0L
            } else {
                runCatching {
                    document.data<FirestoreChatReadDocument>().readAt.toEpochMillisOrNull() ?: 0L
                }.getOrDefault(0L)
            }
        }

        // Query only messages newer than the user's read marker and within the seven-day
        // retention window, so chat cards do not repeatedly download every drawing.
        return readAtFlow.flatMapLatest { readAt ->
            val now = Clock.System.now().toEpochMilliseconds()
            val earliestRetainedMessage = now - UNREAD_WINDOW_MILLIS
            val queryCutoff = (maxOf(readAt, earliestRetainedMessage) + 1L).coerceAtLeast(1L)
                .toFirestoreTimestamp()

            chatReference.collection(LETTERS_COLLECTION)
                .where("createdAt", greaterThan = queryCutoff)
                .snapshots
                .map { snapshot ->
                    val observedAt = Clock.System.now().toEpochMilliseconds()
                    snapshot.documents.mapNotNull { document ->
                        runCatching { document.data<FirestoreUnreadLetterDocument>() }.getOrNull()
                    }.count { letter ->
                        val createdAt = letter.createdAt.toEpochMillisOrNull()
                        val expiresAt = letter.expiresAt.toEpochMillisOrNull()
                        letter.authorUserId != userId &&
                            createdAt != null && createdAt > readAt &&
                            expiresAt != null && expiresAt > observedAt
                    }
                }
        }
    }

    override suspend fun markChatAsRead(chatId: String, userId: String): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(userId.isNotBlank()) { getString(Res.string.chat_mark_read_login_required) }

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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(requestingUserId.isNotBlank()) { getString(Res.string.letter_login_required) }
        require(memberUserId.isNotBlank()) { getString(Res.string.chat_user_not_found) }
        require(memberUserId != requestingUserId) { getString(Res.string.chat_already_member) }

        val firestore = firestoreProvider()
        val chatReference = firestore.collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(requestingUserId in current.memberIds) { getString(Res.string.chat_not_member_anymore) }
        require(memberUserId !in current.memberIds) { getString(Res.string.chat_target_already_member) }
        require(
            firestore.collection("publicProfiles").document(memberUserId).get().exists,
        ) { getString(Res.string.chat_user_not_found) }

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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(requestingUserId.isNotBlank()) { getString(Res.string.chat_members_manage_login_required) }
        require(memberUserId.isNotBlank()) { getString(Res.string.chat_member_not_selected) }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == requestingUserId) {
            getString(Res.string.chat_remove_member_creator_only)
        }
        require(memberUserId != current.creatorUserId) { getString(Res.string.chat_cannot_remove_creator) }
        require(memberUserId in current.memberIds) { getString(Res.string.chat_member_already_left) }

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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(userId.isNotBlank()) { getString(Res.string.chat_leave_login_required) }

        val chatReference = firestoreProvider().collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(userId in current.memberIds) { getString(Res.string.chat_already_left) }
        require(userId != current.creatorUserId) {
            getString(Res.string.chat_creator_cannot_leave)
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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(requestingUserId.isNotBlank()) { getString(Res.string.chat_delete_login_required) }

        val firestore = firestoreProvider()
        val chatReference = firestore.collection(CHATS_COLLECTION).document(chatId)
        val current = chatReference.get().data<FirestoreChatDocument>().toDomain(chatId)
        require(current.creatorUserId == requestingUserId) {
            getString(Res.string.chat_delete_creator_only)
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
        require(chatId.isNotBlank()) { getString(Res.string.chat_not_selected_error) }
        require(createdByUserId.isNotBlank()) { getString(Res.string.chat_invite_login_required) }

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
        require(normalizedCode.isNotEmpty()) { getString(Res.string.chat_invite_code_required) }
        require(userId.isNotBlank()) { getString(Res.string.chat_join_login_required) }

        val firestore = firestoreProvider()
        val inviteSnapshot = firestore
            .collection(INVITES_COLLECTION)
            .document(normalizedCode)
            .get()
        require(inviteSnapshot.exists) {
            getString(Res.string.chat_invite_invalid)
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
        maxLettersCount = maxLettersCount,
        createdAtEpochMillis = createdAt.requireEpochMillis("createdAt"),
        updatedAtEpochMillis = updatedAt.requireEpochMillis("updatedAt"),
        lastMessageAtEpochMillis = (lastMessageAt as? Timestamp)?.toMilliseconds()?.toLong(),
    )

    private fun BaseTimestamp.toEpochMillisOrNull(): Long? =
        (this as? Timestamp)?.toMilliseconds()?.toLong()

    private fun Long.toFirestoreTimestamp(): Timestamp = Timestamp(
        seconds = this / 1_000L,
        nanoseconds = ((this % 1_000L) * 1_000_000L).toInt(),
    )

    private fun BaseTimestamp.requireEpochMillis(field: String): Long =
        (this as? Timestamp)?.toMilliseconds()?.toLong()
            ?: error("Firestore did not return a server timestamp for field $field.")

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
        val maxLettersCount: Int? = null,
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
    private data class FirestoreCleanupRequestDocument(
        val requestedByUserId: String,
        val requestedAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    @Serializable
    private data class FirestoreUnreadLetterDocument(
        val authorUserId: String,
        val drawingJson: String = "",
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
        val expiresAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    private companion object {
        const val CHATS_COLLECTION = "chats"
        const val LETTERS_COLLECTION = "letters"
        const val READS_COLLECTION = "reads"
        const val CLEANUP_REQUESTS_COLLECTION = "cleanupRequests"
        const val INVITES_COLLECTION = "chatInvites"
        const val MAX_CHAT_TITLE_LENGTH = 80
        const val MAX_CHAT_DESCRIPTION_LENGTH = 120
        const val MAX_CHAT_LETTERS_LIMIT = 500
        const val UNREAD_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val INVITE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
