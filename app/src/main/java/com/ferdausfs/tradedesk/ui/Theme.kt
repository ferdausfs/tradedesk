package com.ferdausfs.tradedesk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// light "cream" palette (own implementation, charting-app feel)
val Bg = Color(0xFFF1E6CF)      // cream background
val PanelC = Color(0xFFFFFFFF)  // white panels
val SheetC = Color(0xFFFFFFFF)  // bottom sheets
val ChipBg = Color(0xFFE0E3EB)  // unselected chips / small buttons
val DarkC = Color(0xFF131722)   // nav bar + left toolbar
val TextC = Color(0xFF131722)
val MutedC = Color(0xFF787B86)
val AccentC = Color(0xFF2962FF)
val UpC = Color(0xFF089981)
val DownC = Color(0xFFF23645)

@Composable
fun TradeDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Bg,
            surface = PanelC,
            primary = AccentC,
            onBackground = TextC,
            onSurface = TextC,
            onPrimary = Color.White,
            outline = MutedC
        ),
        content = content
    )
}
