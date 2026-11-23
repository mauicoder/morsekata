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
import dev.mauicoder.morsekata.ui.settings.SettingsScreen
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.viewmodel.UserPreferencesViewModel
import javax.inject.Inject

@AndroidEntryPoint
class ConfigFragment @Inject constructor() : Fragment() {

    private val viewModel: UserPreferencesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MorseKataTheme {
                    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
                    SettingsScreen(
                        preferences = preferences,
                        onFrequencyChange = viewModel::setFrequency,
                        onIncreaseFrequency = viewModel::increaseFrequency,
                        onDecreaseFrequency = viewModel::decreaseFrequency,
                        onWpmChange = viewModel::setWpm,
                        onIncreaseWpm = viewModel::increaseWpm,
                        onDecreaseWpm = viewModel::decreaseWpm,
                        onFarnsworthWpmChange = viewModel::setFarnsworthWpm,
                        onIncreaseFarnsworthWpm = viewModel::increaseFarnsworthWpm,
                        onDecreaseFarnsworthWpm = viewModel::decreaseFarnsworthWpm,
                        onShowExperimentalFeaturesChange = viewModel::setShowExperimentalFeatures,
                        onPlayTestAudio = viewModel::playTestAudio,
                        onResetDefaults = viewModel::resetToDefaults
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopTestAudio()
    }
}
