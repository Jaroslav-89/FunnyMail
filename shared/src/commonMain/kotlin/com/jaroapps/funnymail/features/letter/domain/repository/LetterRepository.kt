package com.jaroapps.funnymail.features.letter.domain.repository

import com.jaroapps.funnymail.features.drawing.domain.model.DrawingDocument
import com.jaroapps.funnymail.features.letter.domain.model.Letter
import kotlinx.coroutines.flow.Flow

/**
 * A letter contains structured drawing data. The Firestore adapter must serialize it
 * into a JSON field in the letter document; PNG and Firebase Storage are not used.
 */
interface LetterRepository {
    fun observeLetters(chatId: String): Flow<List<Letter>>

    /** The returned timestamps must come from the backend, not the device clock. */
    suspend fun sendLetter(
        chatId: String,
        authorUserId: String,
        drawing: DrawingDocument,
    ): Result<Letter>

    suspend fun deleteLetter(chatId: String, letterId: String, requestingUserId: String): Result<Unit>
}
