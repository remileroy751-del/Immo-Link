package com.immolink.appname

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF123D73)
val Orange = Color(0xFFFF7A00)
val Ink = Color(0xFF16202A)
val Light = Color(0xFFF5F7FA)

@Composable fun ImmoLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Orange, background = Light, surface = Color.White, onBackground = Ink, onSurface = Ink), content = content)
}
