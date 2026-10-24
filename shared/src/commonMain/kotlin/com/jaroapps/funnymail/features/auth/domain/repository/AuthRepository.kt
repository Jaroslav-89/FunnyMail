package com.jaroapps.funnymail.features.auth.domain.repository

import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Auth abstraction used by shared presentation/domain code.
 * Firebase SDK types must not leak through this interface.
 */
interface AuthRepository {
    val currentUser: Flow<UserProfile?>

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<UserProfile>

    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): Result<UserProfile>

    suspend fun signInWithGoogle(): Result<UserProfile>

    suspend fun signInWithApple(): Result<UserProfile>

    suspend fun signOut(): Result<Unit>
}
