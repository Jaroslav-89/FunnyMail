package com.jaroapps.funnymail.core.notifications

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.getPlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.messaging.messaging
import kotlinx.serialization.Serializable

/**
 * Registers the installation's FCM token with the signed-in user's private token directory.
 * Cloud Functions uses this directory to send notifications without exposing tokens to members.
 */
suspend fun registerPushToken(userId: String) {
    require(userId.isNotBlank()) { "Для регистрации уведомлений нужен аккаунт." }
    check(FirebaseRuntimeStatus.isConfigured) { "Firebase не настроен." }

    val token = Firebase.messaging.getToken()
    if (token.isBlank()) return

    Firebase.firestore
        .collection("users")
        .document(userId)
        .collection("pushTokens")
        .document(token.documentKey())
        .set(PushTokenDocument(token = token, platform = getPlatform().name))
}

/** Removes this installation from the prior account when the user signs out or switches users. */
suspend fun unregisterPushToken(userId: String) {
    if (userId.isBlank() || !FirebaseRuntimeStatus.isConfigured) return
    val token = Firebase.messaging.getToken()
    if (token.isBlank()) return

    Firebase.firestore
        .collection("users")
        .document(userId)
        .collection("pushTokens")
        .document(token.documentKey())
        .delete()
}

private fun String.documentKey(): String = hashCode().toUInt().toString(16)

@Serializable
private data class PushTokenDocument(
    val token: String,
    val platform: String,
)
