package com.jaroapps.funnymail.features.auth.data

import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import com.jaroapps.funnymail.features.auth.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Firebase implementation of the domain auth contract.
 *
 * Firebase types stay inside the data layer; callers only receive domain models.
 */
class FirebaseAuthRepository(
    private val authProvider: () -> FirebaseAuth = {
        check(FirebaseRuntimeStatus.isConfigured) {
            "Firebase is not configured. Add the platform Firebase configuration files."
        }
        Firebase.auth
    },
) : AuthRepository {

    override val currentUser: Flow<UserProfile?> = flow {
        val auth = authProvider()
        emit(auth.currentUser.toUserProfile())
        emitAll(auth.authStateChanged.map { it.toUserProfile() })
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

        authProvider()
            .signInWithEmailAndPassword(email.trim(), password)
            .user
            ?.toUserProfile()
            ?: error("Firebase returned no user after sign-in.")
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
            .user
            ?: error("Firebase returned no user after registration.")

        val normalizedName = displayName.trim()
        user.updateProfile(displayName = normalizedName)
        user.toUserProfile().copy(displayName = normalizedName)
    }

    override suspend fun signOut(): Result<Unit> = authResult {
        authProvider().signOut()
    }

    private suspend fun <T> authResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun FirebaseUser?.toUserProfile(): UserProfile? = this?.let { user ->
        UserProfile(
            id = user.uid,
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            avatarUrl = user.photoURL,
        )
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
