package com.jaroapps.funnymail.core.navigation

import androidx.compose.runtime.Composable

/**
 * iOS does not provide an Android-style system Back button.
 * Screen navigation is still available through the app's own controls.
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
