package com.jaroapps.funnymail.features.auth.data

import com.jaroapps.funnymail.core.auth.NativeSocialSignIn
import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Firebase implementation of auth and the user directory.
 * Email addresses are looked up through individual emailLookup documents; clients never list
 * that collection, and user profile documents with personal fields remain owner-readable.
 */
class FirebaseAuthRepository(
    private val authProvider: () -> FirebaseAuth = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase is not configured. Add the platform Firebase configuration files."
        }
        Firebase.auth
    },
    private val firestoreProvider: () -> FirebaseFirestore = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase is not configured. Add the platform Firebase configuration files."
        }
        Firebase.firestore
    },
) : AuthRepository {

    override val currentUser: Flow<UserProfile?> = flow {
        val auth = authProvider()
        val initial = auth.currentUser
        emit(initial?.let { ensureUserDirectory(it) })
        emitAll(
            auth.authStateChanged
                .distinctUntilChangedBy { it?.uid }
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(null)
                    } else {
                        flow {
                            val initialProfile = ensureUserDirectory(user)
                            emit(initialProfile)
                            val profileRef = firestoreProvider()
                                .collection(USERS_COLLECTION)
                                .document(user.uid)
                            emitAll(
                                profileRef.snapshots.map { snapshot ->
                                    if (snapshot.exists) {
                                        user.withDirectory(snapshot.data<FirestoreUserDirectory>())
                                    } else {
                                        initialProfile
                                    }
                                },
                            )
                        }
                    }
                },
        )
    }.catch {
        // An unconfigured local checkout should still render the signed-out screen.
        emit(null)
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<UserProfile> = authResult {
        require(email.isNotBlank()) { "Email is required." }
        require(password.isNotEmpty()) { "Password is required." }

        val user = authProvider()
            .signInWithEmailAndPassword(email.trim(), password)
            .user ?: error("Firebase returned no user after sign-in.")
        ensureUserDirectory(user)
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): Result<UserProfile> = authResult {
        require(email.isNotBlank()) { "Email is required." }
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Password should be at least $MIN_PASSWORD_LENGTH characters."
        }
        require(displayName.isNotBlank()) { "Display name is required." }

        val user = authProvider()
            .createUserWithEmailAndPassword(email.trim(), password)
            .user ?: error("Firebase returned no user after registration.")

        val normalizedName = displayName.trim()
        user.updateProfile(displayName = normalizedName)
        ensureUserDirectory(user, initialCustomName = normalizedName)
    }

    override suspend fun signInWithGoogle(): Result<UserProfile> = authResult {
        val auth = authProvider()
        val tokens = NativeSocialSignIn.acquireGoogleTokens()
        val user = auth.signInWithCredential(
            GoogleAuthProvider.credential(
                idToken = tokens.idToken,
                accessToken = null,
            ),
        ).user ?: error("Firebase returned no user after Google sign-in.")

        ensureUserDirectory(user, providerDisplayName = user.displayName.orEmpty())
    }

    override suspend fun signInWithApple(): Result<UserProfile> = authResult {
        val auth = authProvider()
        val tokens = NativeSocialSignIn.acquireAppleTokens()
        require(!tokens.rawNonce.isNullOrBlank()) {
            "Apple sign-in did not return the original nonce."
        }
        val user = auth.signInWithCredential(
            OAuthProvider.credential(
                providerId = APPLE_PROVIDER_ID,
                idToken = tokens.idToken,
                rawNonce = tokens.rawNonce,
            ),
        ).user ?: error("Firebase returned no user after Apple sign-in.")

        val providerName = tokens.displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (providerName != null) {
            // Apple usually supplies the person's name only on the first authorization.
            user.updateProfile(displayName = providerName)
        }
        ensureUserDirectory(
            user,
            providerDisplayName = providerName ?: user.displayName.orEmpty(),
        )
    }

    override suspend fun signOut(): Result<Unit> = authResult {
        authProvider().signOut()
    }

    override suspend fun checkUsernameAvailability(
        username: String,
        userId: String,
    ): Result<Boolean> = authResult {
        require(userId.isNotBlank()) { "Сначала войдите в аккаунт." }
        val normalized = normalizeUsername(username)
        require(USERNAME_REGEX.matches(normalized)) {
            "Ник должен содержать 3–20 символов: латинские буквы, цифры или _."
        }

        val reservation = firestoreProvider()
            .collection(USERNAMES_COLLECTION)
            .document(normalized)
            .get()
        if (!reservation.exists) {
            true
        } else {
            reservation.data<LookupRecord>().userId == userId
        }
    }

    override suspend fun updateUserProfile(
        userId: String,
        customName: String,
        username: String,
    ): Result<UserProfile> = authResult {
        val firebaseUser = authProvider().currentUser
            ?: error("Сначала войдите в аккаунт.")
        require(firebaseUser.uid == userId) { "Нельзя редактировать чужой профиль." }
        require(customName.length <= MAX_CUSTOM_NAME_LENGTH) {
            "Имя не должно превышать $MAX_CUSTOM_NAME_LENGTH символов."
        }
        val normalizedUsername = normalizeUsername(username)
        require(
            normalizedUsername.isBlank() || USERNAME_REGEX.matches(normalizedUsername),
        ) { "Ник должен содержать 3–20 символов: латинские буквы, цифры или _." }

        val oldProfile = ensureUserDirectory(firebaseUser)
        val firestore = firestoreProvider()
        val oldUsername = oldProfile.username.lowercase()
        val newUsernameRef = normalizedUsername.takeIf { it.isNotBlank() }?.let {
            firestore.collection(USERNAMES_COLLECTION).document(it)
        }

        if (newUsernameRef != null) {
            val reservation = newUsernameRef.get()
            if (reservation.exists) {
                val existingUserId = reservation.data<LookupRecord>().userId
                require(existingUserId == userId) { USERNAME_TAKEN }
            } else {
                // Rules allow creation only if this username has not already been reserved.
                newUsernameRef.set(LookupRecord(userId = userId))
            }
        }

        val directoryRef = firestore.collection(USERS_COLLECTION).document(userId)
        val existingDirectory = directoryRef.get().data<FirestoreUserDirectory>()
        val updated = existingDirectory.copy(
            customName = customName.trim(),
            username = normalizedUsername,
            usernameLower = normalizedUsername,
            email = firebaseUser.email.orEmpty(),
            emailLower = firebaseUser.email.orEmpty().trim().lowercase(),
        )
        directoryRef.set(updated)
        firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .document(userId)
            .set(updated)
        ensureEmailLookup(firestore, updated)

        if (oldUsername.isNotBlank() && oldUsername != normalizedUsername) {
            val oldReservationRef = firestore.collection(USERNAMES_COLLECTION).document(oldUsername)
            val oldReservation = oldReservationRef.get()
            if (oldReservation.exists && oldReservation.data<LookupRecord>().userId == userId) {
                oldReservationRef.delete()
            }
        }

        firebaseUser.withDirectory(updated)
    }

    override suspend fun findUser(query: String): Result<UserProfile?> = authResult {
        require(query.isNotBlank()) { "Введите email или ник." }
        val normalizedQuery = query.trim()
        val firestore = firestoreProvider()
        val userId = if ('@' in normalizedQuery && !normalizedQuery.startsWith("@")) {
            val emailKey = normalizedQuery.lowercase()
            val emailRecord = firestore.collection(EMAIL_LOOKUP_COLLECTION)
                .document(emailKey).get()
            if (emailRecord.exists) {
                emailRecord.data<LookupRecord>().userId
            } else {
                val handle = normalizeUsername(normalizedQuery)
                val handleRecord = if (handle.isNotBlank()) {
                    firestore.collection(USERNAMES_COLLECTION).document(handle).get()
                } else null
                if (handleRecord?.exists == true) handleRecord.data<LookupRecord>().userId else null
            }
        } else {
            val handle = normalizeUsername(normalizedQuery)
            if (!USERNAME_REGEX.matches(handle)) null else {
                val handleRecord = firestore.collection(USERNAMES_COLLECTION).document(handle).get()
                if (handleRecord.exists) handleRecord.data<LookupRecord>().userId else null
            }
        }
        userId?.let { getPublicProfile(it).getOrThrow() }
    }

    override suspend fun getPublicProfile(userId: String): Result<UserProfile?> = authResult {
        if (userId.isBlank()) return@authResult null
        val snapshot = firestoreProvider()
            .collection(PUBLIC_PROFILES_COLLECTION)
            .document(userId)
            .get()
        if (!snapshot.exists) return@authResult null
        val directory = snapshot.data<FirestoreUserDirectory>()
        directory.toUserProfile()
    }

    private suspend fun ensureUserDirectory(
        user: FirebaseUser,
        initialCustomName: String = "",
        providerDisplayName: String? = null,
    ): UserProfile {
        val firestore = firestoreProvider()
        val userRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val snapshot = userRef.get()
        var directory = if (snapshot.exists) {
            snapshot.data<FirestoreUserDirectory>()
        } else {
            FirestoreUserDirectory(
                uid = user.uid,
                email = user.email.orEmpty(),
                emailLower = user.email.orEmpty().trim().lowercase(),
                customName = initialCustomName.trim(),
                providerDisplayName = providerDisplayName.orEmpty(),
                generatedName = generatedName(),
                avatarUrl = user.photoURL.orEmpty(),
            )
        }

        if (snapshot.exists) {
            var changed = false
            if (directory.providerDisplayName.isBlank() && !providerDisplayName.isNullOrBlank()) {
                directory = directory.copy(providerDisplayName = providerDisplayName.trim())
                changed = true
            }
            if (directory.customName.isBlank() && initialCustomName.isNotBlank()) {
                directory = directory.copy(customName = initialCustomName.trim())
                changed = true
            }
            if (directory.email != user.email.orEmpty() ||
                directory.emailLower != user.email.orEmpty().trim().lowercase()
            ) {
                directory = directory.copy(
                    email = user.email.orEmpty(),
                    emailLower = user.email.orEmpty().trim().lowercase(),
                )
                changed = true
            }
            if (changed) userRef.set(directory)
        } else {
            userRef.set(directory)
        }

        val publicRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(user.uid)
        val publicSnapshot = publicRef.get()
        if (!publicSnapshot.exists || publicSnapshot.data<FirestoreUserDirectory>() != directory) {
            publicRef.set(directory)
        }
        ensureEmailLookup(firestore, directory)

        if (directory.username.isNotBlank()) {
            val handleRef = firestore.collection(USERNAMES_COLLECTION).document(directory.usernameLower)
            val handleSnapshot = handleRef.get()
            if (!handleSnapshot.exists) {
                handleRef.set(LookupRecord(userId = user.uid))
            }
        }
        user.withDirectory(directory)
    }

    private suspend fun ensureEmailLookup(
        firestore: FirebaseFirestore,
        directory: FirestoreUserDirectory,
    ) {
        if (directory.emailLower.isBlank()) return
        val reference = firestore.collection(EMAIL_LOOKUP_COLLECTION).document(directory.emailLower)
        val snapshot = reference.get()
        if (!snapshot.exists) {
            reference.set(LookupRecord(userId = directory.uid))
        }
    }

    private fun FirebaseUser.withDirectory(directory: FirestoreUserDirectory): UserProfile =
        UserProfile(
            id = uid,
            displayName = displayName.orEmpty(),
            email = email.orEmpty(),
            avatarUrl = photoURL ?: directory.avatarUrl.takeIf { it.isNotBlank() },
            customName = directory.customName,
            username = directory.username,
            providerDisplayName = directory.providerDisplayName,
            generatedName = directory.generatedName,
        )

    private fun FirestoreUserDirectory.toUserProfile(): UserProfile =
        UserProfile(
            id = uid,
            displayName = providerDisplayName,
            email = email,
            avatarUrl = avatarUrl.takeIf { it.isNotBlank() },
            customName = customName,
            username = username,
            providerDisplayName = providerDisplayName,
            generatedName = generatedName,
        )

    private fun normalizeUsername(value: String): String =
        value.trim().removePrefix("@").lowercase()

    private fun generatedName(): String {
        val number = Random.nextInt(1_000, 10_000)
        return "Пользователь#$number"
    }

    private suspend fun <T> authResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    @Serializable
    private data class FirestoreUserDirectory(
        val uid: String,
        val email: String = "",
        val emailLower: String = "",
        val customName: String = "",
        val username: String = "",
        val usernameLower: String = "",
        val providerDisplayName: String = "",
        val generatedName: String = "",
        val avatarUrl: String = "",
    )

    @Serializable
    private data class LookupRecord(
        val userId: String,
    )

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val APPLE_PROVIDER_ID = "apple.com"
        const val MAX_CUSTOM_NAME_LENGTH = 40
        const val USERS_COLLECTION = "users"
        const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        const val USERNAMES_COLLECTION = "usernames"
        const val EMAIL_LOOKUP_COLLECTION = "emailLookup"
        const val USERNAME_TAKEN = "Этот ник уже занят, выберите другой ник."
        val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")
    }
}
