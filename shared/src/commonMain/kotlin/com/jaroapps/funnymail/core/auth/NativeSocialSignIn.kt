package com.jaroapps.funnymail.core.auth

/**
 * Tokens returned by a native identity-provider flow.
 *
 * The raw Apple nonce must be passed to Firebase unchanged; the platform provider
 * sends its SHA-256 hash to Apple as part of the authorization request.
 */
data class SocialAuthTokens(
    val idToken: String,
    val rawNonce: String? = null,
    val displayName: String? = null,
)

expect object NativeSocialSignIn {
    /** Google sign-in is exposed on Android only. */
    val supportsGoogleSignIn: Boolean

    /** Apple ID uses the native Sign in with Apple sheet on iOS only. */
    val supportsAppleSignIn: Boolean

    /** Android stores the current Activity here to present Credential Manager UI. */
    fun attachPlatformContext(context: Any?)

    suspend fun acquireGoogleTokens(): SocialAuthTokens

    suspend fun acquireAppleTokens(): SocialAuthTokens
}
