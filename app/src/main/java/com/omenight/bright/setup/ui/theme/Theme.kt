package com.omenight.bright.setup.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Bright's tokens, kept identical so setup hands over to the launcher without a visual seam. */
object BrightColors {
    val Background = Color(0xFF060914)
    val Text = Color(0xFFF5F5F7)
    val TextMuted = Color(0xFFF5F5F7).copy(alpha = 0.62f)
    val OnLight = Color(0xFF101014)

    val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 2f), blurRadius = 18f)
}

@Composable
fun BrightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrightColors.Text,
            background = BrightColors.Background,
            onSurface = BrightColors.Text,
            onBackground = BrightColors.Text,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides BrightColors.Text, content = content)
    }
}
