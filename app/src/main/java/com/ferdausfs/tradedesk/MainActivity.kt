package com.ferdausfs.tradedesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ferdausfs.tradedesk.ui.MainScreen
import com.ferdausfs.tradedesk.ui.TradeDeskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TradeDeskTheme { MainScreen() }
        }
    }
}
