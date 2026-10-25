package com.jaroapps.funnymail.core.navigation

import androidx.compose.runtime.Composable

/**
 * Handles the platform's system Back action while the app is on a nested screen.
 * The home screen leaves Back to the operating system so the app can close normally.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
