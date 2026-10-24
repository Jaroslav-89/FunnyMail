package com.jaroapps.funnymail.core.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.CommonCrypto.CC_SHA256
import kotlinx.cinterop.convert
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual object NativeSocialSignIn {
    private var appleController: ASAuthorizationController? = null
    private var appleDelegate: AppleAuthorizationDelegate? = null

    actual val supportsGoogleSignIn: Boolean = false

    actual val supportsAppleSignIn: Boolean = true

    actual fun attachPlatformContext(context: Any?) = Unit

    actual suspend fun acquireGoogleTokens(): SocialAuthTokens =
        error("Вход через Google доступен в Android-версии приложения.")

    actual suspend fun acquireAppleTokens(): SocialAuthTokens =
        suspendCancellableCoroutine { continuation ->
            val rawNonce = secureRandomNonce()
            val request = ASAuthorizationAppleIDProvider().createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeEmail, ASAuthorizationScopeFullName)
                nonce = sha256(rawNonce)
            }
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            val delegate = AppleAuthorizationDelegate(rawNonce) { result ->
                appleController = null
                appleDelegate = null
                result.fold(
                    onSuccess = { tokens ->
                        if (continuation.isActive) continuation.resume(tokens)
                    },
                    onFailure = { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    },
                )
            }

            // ASAuthorizationController keeps weak delegate references; retain both until callback.
            appleController = controller
            appleDelegate = delegate
            controller.delegate = delegate
            controller.presentationContextProvider = delegate

            continuation.invokeOnCancellation {
                if (appleController === controller) {
                    appleController = null
                    appleDelegate = null
                }
            }
            controller.performRequests()
        }

    private fun secureRandomNonce(): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
        val bytes = ByteArray(32)
        bytes.usePinned { buffer ->
            val status = SecRandomCopyBytes(
                kSecRandomDefault,
                bytes.size.convert(),
                buffer.addressOf(0),
            )
            check(status == 0) { "Не удалось создать защищённый Apple nonce." }
        }
        return bytes.joinToString("") { byte ->
            alphabet[(byte.toInt() and 0xff) % alphabet.length].toString()
        }
    }

    private fun sha256(value: String): String {
        val input = value.encodeToByteArray()
        val digest = ByteArray(32)
        input.usePinned { inputBuffer ->
            digest.usePinned { digestBuffer ->
                CC_SHA256(
                    inputBuffer.addressOf(0),
                    input.size.convert(),
                    digestBuffer.addressOf(0),
                )
            }
        }
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AppleAuthorizationDelegate(
    private val rawNonce: String,
    private val onComplete: (Result<SocialAuthTokens>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential == null) {
            onComplete(Result.failure(IllegalStateException("Apple ID не вернул корректные учётные данные.")))
            return
        }

        val token = credential.identityToken?.toUtf8String()
        if (token.isNullOrBlank()) {
            onComplete(Result.failure(IllegalStateException("Apple ID не вернул identity token.")))
            return
        }

        val displayName = listOfNotNull(
            credential.fullName?.givenName,
            credential.fullName?.familyName,
        ).joinToString(" ").trim().takeIf { it.isNotBlank() }

        onComplete(
            Result.success(
                SocialAuthTokens(
                    idToken = token,
                    rawNonce = rawNonce,
                    displayName = displayName,
                ),
            ),
        )
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        onComplete(
            Result.failure(
                IllegalStateException("Apple ID sign-in failed: ${didCompleteWithError.localizedDescription}"),
            ),
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow
            ?: error("Не найдено окно для показа диалога Apple ID.")
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String? =
    NSString.create(data = this, encoding = NSUTF8StringEncoding) as? String
