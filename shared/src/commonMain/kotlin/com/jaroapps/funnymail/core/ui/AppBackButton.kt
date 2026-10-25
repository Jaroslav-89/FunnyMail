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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** Consistent back button used by the app's screen headers. */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Назад",
    keepLightAppearance: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (keepLightAppearance) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                1.dp,
                if (keepLightAppearance) Color(0xFFE8EAED) else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CanvasBackArrow(color = if (keepLightAppearance) Color(0xFF202124) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CanvasBackArrow(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(23.dp)) {
        val arrow = Path().apply {
            moveTo(size.width * 0.78f, size.height * 0.50f)
            lineTo(size.width * 0.22f, size.height * 0.50f)
            moveTo(size.width * 0.45f, size.height * 0.23f)
            lineTo(size.width * 0.18f, size.height * 0.50f)
            lineTo(size.width * 0.45f, size.height * 0.77f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.095f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
