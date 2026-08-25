package com.hack.kidhack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.navigation.KidhackNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidhackTheme {
                KidhackNavHost()
            }
        }
    }
}
