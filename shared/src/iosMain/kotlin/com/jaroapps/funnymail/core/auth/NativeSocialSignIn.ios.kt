package com.jaroapps.funnymail.core.auth

import GoogleSignIn.GIDConfiguration
import GoogleSignIn.GIDSignIn
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
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSDictionary
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

    actual val supportsAppleSignIn: Boolean = true

    actual fun attachPlatformContext(context: Any?) = Unit

    actual suspend fun acquireGoogleTokens(): SocialAuthTokens {
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: error("Не удалось открыть Google Sign-In: окно приложения недоступно.")

        val plistPath = NSBundle.mainBundle.pathForResource("GoogleService-Info", "plist")
            ?: error("Не найден GoogleService-Info.plist в приложении.")
        val plist = NSDictionary.dictionaryWithContentsOfFile(plistPath)
            ?: error("Не удалось прочитать GoogleService-Info.plist.")
        val clientId = plist.objectForKey("CLIENT_ID") as? String
            ?: error("В GoogleService-Info.plist отсутствует CLIENT_ID.")

        return suspendCancellableCoroutine { continuation ->
            val googleSignIn = GIDSignIn.sharedInstance
            googleSignIn.configuration = GIDConfiguration(clientID = clientId)

            // Google Sign-In 10.x exposes an async configuration step before login.
            googleSignIn.configureWithCompletion { configurationError ->
                if (configurationError != null) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(configurationError.localizedDescription),
                        )
                    }
                } else {
                    googleSignIn.signInWithPresentingViewController(rootViewController) { result, error ->
                        if (error != null) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(error.localizedDescription),
                                )
                            }
                        } else {
                            val token = result?.user?.idToken?.tokenString
                            if (token.isNullOrBlank()) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IllegalStateException("Google Sign-In не вернул ID token."),
                                    )
                                }
                            } else if (continuation.isActive) {
                                continuation.resume(SocialAuthTokens(idToken = token))
                            }
                        }
                    }
                }
            }
        }
    }

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
