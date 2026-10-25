package com.jaroapps.funnymail.features.auth.domain.repository

import com.jaroapps.funnymail.features.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Auth abstraction used by shared presentation/domain code.
 * Firebase SDK types must not leak through this interface.
 */
interface AuthRepository {
    val currentUser: Flow<UserProfile?>

    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile>

    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): Result<UserProfile>

    suspend fun signInWithGoogle(): Result<UserProfile>

    suspend fun signInWithApple(): Result<UserProfile>

    suspend fun signOut(): Result<Unit>

    suspend fun checkUsernameAvailability(username: String, userId: String): Result<Boolean>

    suspend fun updateUserProfile(
        userId: String,
        customName: String,
        username: String,
    ): Result<UserProfile>

    /** Exact lookup by email or @handle. The result contains only the one matching profile. */
    suspend fun findUser(query: String): Result<UserProfile?>

    /** Retrieves the display fields for one known user ID, without listing the directory. */
    suspend fun getPublicProfile(userId: String): Result<UserProfile?>
}
