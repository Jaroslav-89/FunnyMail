package com.jaroapps.funnymail.core.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

        // This method is called from the explicit "Continue with Google" button.
        // Google recommends GetSignInWithGoogleOption for this button flow, including
        // devices where no account is already authorized or the account needs re-authentication.
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val result = try {
            CredentialManager.create(activity).getCredential(
                context = activity,
                request = request,
            )
        } catch (exception: GetCredentialException) {
            val exceptionName = exception.javaClass.simpleName
            Log.e(
                LOG_TAG,
                "Google Credential Manager failed: $exceptionName: ${exception.message}",
                exception,
            )
            val reason = when (exceptionName) {
                "NoCredentialException" -> "GOOGLE_NO_CREDENTIAL"
                "GetCredentialCancellationException" -> "GOOGLE_SIGN_IN_CANCELLED"
                "GetCredentialProviderConfigurationException" -> "GOOGLE_PROVIDER_CONFIGURATION"
                else -> "GOOGLE_CREDENTIAL_ERROR"
            }
            throw IllegalStateException(
                "$reason: ${exception.message.orEmpty()}",
                exception,
            )
        }
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

    private const val LOG_TAG = "FunnyMailGoogleAuth"
}
