package com.jaroapps.funnymail.core.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

actual object NativeSocialSignIn {
    private var activityReference: WeakReference<Activity>? = null

    actual val supportsGoogleSignIn: Boolean = true

    actual val supportsAppleSignIn: Boolean = false

    actual fun attachPlatformContext(context: Any?) {
        activityReference = (context as? Activity)?.let { WeakReference(it) }
    }

    actual suspend fun acquireGoogleTokens(): SocialAuthTokens = withContext(Dispatchers.Main.immediate) {
        val activity = activityReference?.get()
            ?: error("Не удалось открыть Google Sign-In. Перезапустите экран приложения.")

        // The Google Services plugin generates this resource from google-services.json.
        // Resolve it dynamically so the shared module can still compile without Firebase config.
        val resourceId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName,
        )
        check(resourceId != 0) {
            "Не найден Google Web Client ID. Проверьте google-services.json и Google provider в Firebase."
        }

        val serverClientId = activity.getString(resourceId).trim()
        check(serverClientId.isNotBlank()) {
            "Google Web Client ID пуст. Проверьте конфигурацию Firebase."
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request,
        )
        val credential = result.credential as? CustomCredential
            ?: error("Google Sign-In вернул неподдерживаемые учётные данные.")
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google Sign-In не вернул ID token."
        }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        check(googleToken.isNotBlank()) { "Google Sign-In вернул пустой ID token." }
        SocialAuthTokens(idToken = googleToken)
    }

    actual suspend fun acquireAppleTokens(): SocialAuthTokens =
        error("Вход через Apple ID доступен в iOS-версии приложения.")
}
