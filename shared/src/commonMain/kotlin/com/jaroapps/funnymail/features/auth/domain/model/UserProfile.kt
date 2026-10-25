package com.jaroapps.funnymail.features.auth.domain.model

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    /** User-editable, non-unique display name. */
    val customName: String = "",
    /** Unique handle without the leading @. */
    val username: String = "",
    /** Name supplied by Google or Apple, when available. */
    val providerDisplayName: String = "",
    /** Stable generated fallback for email/password accounts. */
    val generatedName: String = "",
) {
    val chatDisplayName: String
        get() = customName.trim().takeIf { it.isNotEmpty() }
            ?: username.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: providerDisplayName.takeIf { it.isNotBlank() }
            ?: generatedName.takeIf { it.isNotBlank() }
            ?: displayName.takeIf { it.isNotBlank() }
            ?: "Пользователь"
}
