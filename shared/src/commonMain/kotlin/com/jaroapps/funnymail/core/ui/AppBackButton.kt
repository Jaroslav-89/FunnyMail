package com.jaroapps.funnymail.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.jaroapps.funnymail.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Consistent back button used by the app's screen headers. */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    keepLightAppearance: Boolean = false,
) {
    val defaultDescription = stringResource(Res.string.back)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (keepLightAppearance) Color(0xFFF5F5F5) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                1.dp,
                if (keepLightAppearance) Color(0xFFDADCE0) else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description ?: defaultDescription },
        contentAlignment = Alignment.Center,
    ) {
        BackArrowGlyph(color = if (keepLightAppearance) Color(0xFF202124) else MaterialTheme.colorScheme.onSurface)
    }
}

