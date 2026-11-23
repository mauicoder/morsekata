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
import dev.mauicoder.morsekata.ui.play.PlayTextScreen
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.viewmodel.PlayTextViewModel
import javax.inject.Inject

@AndroidEntryPoint
class PlayTextFragment @Inject constructor(): Fragment() {

    private val viewModel: PlayTextViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MorseKataTheme {
                    val text by viewModel.textState.collectAsStateWithLifecycle()
                    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
                    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()

                    PlayTextScreen(
                        text = text,
                        isPlaying = isPlaying,
                        preferences = preferences,
                        onTextChange = viewModel::updateText,
                        onPlay = viewModel::playMorseCallback,
                        onStop = viewModel::stopPlayback,
                        onSaveWav = { viewModel.saveMorseAsWaveFile(requireContext()) },
                        onShareAudio = { viewModel.shareMorseAsWaveFile(requireActivity()) }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopPlayback()
    }
}
