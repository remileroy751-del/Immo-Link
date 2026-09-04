package com.immolink.appname

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF123D73)
val NavyDark = Color(0xFF0B2D55)
val Orange = Color(0xFFFF7A00)
val OrangeSoft = Color(0xFFFFF1E5)
val BlueSoft = Color(0xFFEAF2FB)
val Ink = Color(0xFF17212B)
val Muted = Color(0xFF667085)
val Light = Color(0xFFF6F8FC)
val Success = Color(0xFF2E7D32)

@Composable
fun ImmoLinkTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Navy,
        onPrimary = Color.White,
        primaryContainer = BlueSoft,
        onPrimaryContainer = NavyDark,
        secondary = Orange,
        onSecondary = Color.White,
        secondaryContainer = OrangeSoft,
        onSecondaryContainer = NavyDark,
        background = Light,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE9EDF3),
        onSurfaceVariant = Muted,
        outline = Color(0xFFD0D5DD),
        error = Color(0xFFB42318)
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        ),
        content = content
    )
}
