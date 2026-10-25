package com.jaroapps.funnymail.features.letter.data

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Stores a drawing as a JSON string in chats/{chatId}/letters/{letterId}.
 * Letter creation is constrained to a seven-day expiresAt value by Firestore Security Rules.
 * Live queries filter expiresAt on the client and the Rules restrict list access to chat members;
 * direct document reads are denied after expiry. Configure Firestore TTL on collection group
 * "letters", field expiresAt, to automatically remove expired documents.
 */
class FirebaseLetterRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase не настроен. Добавьте файлы конфигурации платформы."
        }
        Firebase.firestore
    },
    private val drawingCodec: DrawingCodec = DrawingCodec(),
) : LetterRepository {

    override fun observeLetters(chatId: String): Flow<List<Letter>> {
        require(chatId.isNotBlank()) { "Не выбран чат." }

        // Query only unexpired documents. Security Rules independently compare expiresAt to
        // the server's request.time so clients cannot read an expired letter by changing local time.
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        // Firestore Rules use server request.time. Query slightly ahead of the device clock
        // so the query's expiry constraint is safely stricter than the server-side read rule.
        val queryLowerBoundTimestamp = (nowMillis + QUERY_CLOCK_SAFETY_MILLIS).toFirestoreTimestamp()

        return flow {
            emitAll(
                firestoreProvider()
                    .collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(LETTERS_COLLECTION)
                    .where("expiresAt", greaterThan = queryLowerBoundTimestamp)
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents
                            .mapNotNull { document ->
                                // A local pending write can briefly expose an unresolved server timestamp.
                                // Skip it until Firestore supplies the authoritative timestamp.
                                runCatching {
                                    document.data<FirestoreLetterDocument>()
                                        .toDomain(documentId = document.id, chatId = chatId)
                                }.getOrNull()
                            }
                            .filter { it.expiresAtEpochMillis > Clock.System.now().toEpochMilliseconds() }
                            .sortedBy { it.createdAtEpochMillis }
                    },
            )
        }
    }

    override suspend fun sendLetter(
        chatId: String,
        authorUserId: String,
        drawing: DrawingDocument,
    ): Result<Letter> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(authorUserId.isNotBlank()) { "Для отправки письма нужно войти в аккаунт." }
        require(drawing.strokes.isNotEmpty()) { "Сначала нарисуйте письмо." }

        val drawingJson = drawingCodec.encode(drawing).getOrThrow()
        require(drawingJson.encodeToByteArray().size <= MAX_DRAWING_JSON_BYTES) {
            "Рисунок слишком большой для отправки."
        }

        val expiresAtMillis = Clock.System.now().toEpochMilliseconds() + LETTER_TTL_MILLIS
        val reference = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(LETTERS_COLLECTION)
            .add(
                FirestoreLetterDocument(
                    authorUserId = authorUserId,
                    drawingJson = drawingJson,
                    expiresAt = expiresAtMillis.toFirestoreTimestamp(),
                ),
            )

        val savedLetter = reference.get()
            .data<FirestoreLetterDocument>()
            .toDomain(documentId = reference.id, chatId = chatId)

        // Keep the chat list ordered by its newest letter. Rules only allow members to
        // advance lastMessageAt using a server timestamp. The letter has already been saved,
        // so a metadata-update failure must not report a failed send and invite duplicate retries.
        try {
            firestoreProvider()
                .collection(CHATS_COLLECTION)
                .document(chatId)
                .update("lastMessageAt" to FieldValue.serverTimestamp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The chat list can still derive the latest timestamp from its letter subcollection.
        }

        savedLetter
    }

    override suspend fun deleteLetter(
        chatId: String,
        letterId: String,
        requestingUserId: String,
    ): Result<Unit> = asResult {
        require(chatId.isNotBlank()) { "Не выбран чат." }
        require(letterId.isNotBlank()) { "Не выбран рисунок." }
        require(requestingUserId.isNotBlank()) { "Для удаления рисунка нужно войти в аккаунт." }

        val reference = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(LETTERS_COLLECTION)
            .document(letterId)
        val letter = reference.get().data<FirestoreLetterDocument>()
        require(letter.authorUserId == requestingUserId) {
            "Удалить этот рисунок может только его автор."
        }
        reference.delete()
    }

    private fun FirestoreLetterDocument.toDomain(
        documentId: String,
        chatId: String,
    ): Letter {
        val createdAtMillis = createdAt.requireEpochMillis("createdAt")
        val expiresAtMillis = expiresAt.requireEpochMillis("expiresAt")
        val drawing = drawingCodec.decode(drawingJson).getOrThrow()
        return Letter(
            id = documentId,
            chatId = chatId,
            authorUserId = authorUserId,
            drawing = drawing,
            createdAtEpochMillis = createdAtMillis,
            expiresAtEpochMillis = expiresAtMillis,
        )
    }

    private fun BaseTimestamp.requireEpochMillis(field: String): Long =
        (this as? Timestamp)?.toMilliseconds()?.toLong()
            ?: error("Firestore не вернул корректную метку времени $field для письма.")

    private fun Long.toFirestoreTimestamp(): Timestamp = Timestamp(
        seconds = this / 1_000L,
        nanoseconds = ((this % 1_000L) * 1_000_000L).toInt(),
    )

    private suspend fun <T> asResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    @Serializable
    private data class FirestoreLetterDocument(
        val authorUserId: String,
        val drawingJson: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
        val expiresAt: BaseTimestamp,
    )

    private companion object {
        const val CHATS_COLLECTION = "chats"
        const val LETTERS_COLLECTION = "letters"
        const val LETTER_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val QUERY_CLOCK_SAFETY_MILLIS = 6L * 60L * 1_000L
        const val MAX_DRAWING_JSON_BYTES = 256 * 1_024
    }
}
