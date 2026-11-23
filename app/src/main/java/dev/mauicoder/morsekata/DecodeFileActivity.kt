package dev.mauicoder.morsekata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.mauicoder.morsekata.ui.decoder.DecodeFileScreen
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.viewmodel.DecodeFileViewModel

@AndroidEntryPoint
class DecodeFileActivity : ComponentActivity() {

    private val viewModel: DecodeFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get the URI from the intent that started this activity
        intent?.data?.let {
            viewModel.onFileSelected(it)
        }

        setContent {
            MorseKataTheme {
                DecodeFileScreen(
                    viewModel = viewModel,
                    onDecodeClicked = {
                        viewModel.startDecoding(applicationContext, playAudio = false)
                    }
                )
            }
        }
    }
}
