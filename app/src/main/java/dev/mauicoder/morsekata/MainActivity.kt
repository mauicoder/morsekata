package dev.mauicoder.morsekata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.mauicoder.morsekata.ui.navigation.MorseKataApp
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MorseKataTheme {
                MorseKataApp()
            }
        }
    }
}
