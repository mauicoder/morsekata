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
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.ui.trainer.QsoTrainerScreen
import dev.mauicoder.morsekata.viewmodel.QsoTrainerViewModel
import javax.inject.Inject

@AndroidEntryPoint
class QsoTrainerFragment @Inject constructor() : Fragment() {

    private val viewModel: QsoTrainerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MorseKataTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    QsoTrainerScreen(
                        state = state,
                        onSelectTab = viewModel::selectTab,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectOption = viewModel::selectOption,
                        onReplayAudio = viewModel::replayAudio,
                        onNextQuestion = viewModel::nextQuestion,
                        onPracticeCategory = viewModel::practiceWeakestCategory,
                        onPreviewAudio = viewModel::previewItemAudio,
                        onResetProgress = viewModel::resetProgress
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopAudio()
    }
}
