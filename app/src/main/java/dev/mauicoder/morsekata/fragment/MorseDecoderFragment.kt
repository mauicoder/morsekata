package dev.mauicoder.morsekata.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.mauicoder.morsekata.ui.decoder.MorseDecoderScreen
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.viewmodel.MorseDecoderViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MorseDecoderFragment @Inject constructor(): Fragment() {

    private val viewModel: MorseDecoderViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MorseKataTheme {
                    val decodedText by viewModel.decodedText.collectAsStateWithLifecycle()
                    val isListening by viewModel.isListening.collectAsStateWithLifecycle()

                    MorseDecoderScreen(
                        decodedText = decodedText,
                        isListening = isListening,
                        onStartListening = { viewModel.startListening(requireContext()) },
                        onStopListening = viewModel::stopListening,
                        onClear = viewModel::clearDecodedText
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }
}
