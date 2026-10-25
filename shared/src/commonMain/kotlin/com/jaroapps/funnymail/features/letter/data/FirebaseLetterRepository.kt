package com.jaroapps.funnymail.features.letter.data

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.drawing.domain.DrawingCodec
import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import com.jaroapps.funnymail.features.letter.domain.repository.LetterRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Stores a drawing as a JSON string in chats/{chatId}/letters/{letterId}.
 * Firestore rules restrict access to chat members and deny reading letters older than seven days.
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

        // Keep the query within the retention window so expired documents do not make
        // the whole Firestore query fail the server-side read rule.
        val oldestAllowedMillis = Clock.System.now().toEpochMilliseconds() - LETTER_TTL_MILLIS
        val oldestAllowedTimestamp = Timestamp(
            seconds = oldestAllowedMillis / 1_000L,
            nanoseconds = ((oldestAllowedMillis % 1_000L) * 1_000_000L).toInt(),
        )

        return firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(LETTERS_COLLECTION)
            .where("createdAt", greaterThan = oldestAllowedTimestamp)
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .map { document ->
                        document.data<FirestoreLetterDocument>()
                            .toDomain(documentId = document.id, chatId = chatId)
                    }
                    .filter { it.expiresAtEpochMillis > Clock.System.now().toEpochMilliseconds() }
                    .sortedByDescending { it.createdAtEpochMillis }
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

        val reference = firestoreProvider()
            .collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(LETTERS_COLLECTION)
            .add(
                FirestoreLetterDocument(
                    authorUserId = authorUserId,
                    drawingJson = drawingJson,
                ),
            )

        reference.get()
            .data<FirestoreLetterDocument>()
            .toDomain(documentId = reference.id, chatId = chatId)
    }

    private fun FirestoreLetterDocument.toDomain(
        documentId: String,
        chatId: String,
    ): Letter {
        val createdAtMillis = createdAt.requireEpochMillis()
        val drawing = drawingCodec.decode(drawingJson).getOrThrow()
        return Letter(
            id = documentId,
            chatId = chatId,
            authorUserId = authorUserId,
            drawing = drawing,
            createdAtEpochMillis = createdAtMillis,
            expiresAtEpochMillis = createdAtMillis + LETTER_TTL_MILLIS,
        )
    }

    private fun BaseTimestamp.requireEpochMillis(): Long =
        (this as? Timestamp)?.toMilliseconds()?.toLong()
            ?: error("Firestore не вернул серверное время для письма.")

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
    )

    private companion object {
        const val CHATS_COLLECTION = "chats"
        const val LETTERS_COLLECTION = "letters"
        const val LETTER_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val MAX_DRAWING_JSON_BYTES = 256 * 1_024
    }
}
