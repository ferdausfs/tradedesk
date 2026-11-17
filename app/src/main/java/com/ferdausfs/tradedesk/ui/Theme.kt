package com.ferdausfs.tradedesk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0E11)
val PanelC = Color(0xFF121820)
val TextC = Color(0xFFD1D4DC)
val MutedC = Color(0xFF787B86)
val AccentC = Color(0xFF4A90E2)
val UpC = Color(0xFF26A69A)
val DownC = Color(0xFFEF5350)

@Composable
fun TradeDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = PanelC,
            primary = AccentC,
            onBackground = TextC,
            onSurface = TextC,
            onPrimary = Color.White,
            outline = Color(0xFF2A2E39)
        ),
        content = content
    )
}
