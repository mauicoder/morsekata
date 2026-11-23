package dev.mauicoder.morsekata.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.viewmodel.UserPreferencesViewModel
import kotlin.math.roundToInt

private const val FREQUENCY_SLIDER_STEPS =
    (UserPreferences.MAX_FREQUENCY - UserPreferences.MIN_FREQUENCY) / UserPreferences.FREQUENCY_STEP - 1
private const val WPM_SLIDER_STEPS =
    (UserPreferences.MAX_WPM - UserPreferences.MIN_WPM) / UserPreferences.WPM_STEP - 1

@Composable
fun SettingsScreen(
    viewModel: UserPreferencesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTestAudio()
        }
    }

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
        onResetDefaults = viewModel::resetToDefaults,
        modifier = modifier
    )
}


@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onFrequencyChange: (Int) -> Unit,
    onIncreaseFrequency: () -> Unit,
    onDecreaseFrequency: () -> Unit,
    onWpmChange: (Int) -> Unit,
    onIncreaseWpm: () -> Unit,
    onDecreaseWpm: () -> Unit,
    onFarnsworthWpmChange: (Int) -> Unit,
    onIncreaseFarnsworthWpm: () -> Unit,
    onDecreaseFarnsworthWpm: () -> Unit,
    onShowExperimentalFeaturesChange: (Boolean) -> Unit,
    onPlayTestAudio: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Audio & CW Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            SettingSliderCard(
                title = "Tone Frequency (Hz)",
                subtitle = "Sidetone audio pitch (100 – 1000 Hz, 50 Hz steps)",
                valueLabel = "${preferences.frequency} Hz",
                value = preferences.frequency.toFloat(),
                valueRange = UserPreferences.MIN_FREQUENCY.toFloat()..UserPreferences.MAX_FREQUENCY.toFloat(),
                steps = FREQUENCY_SLIDER_STEPS,
                canDecrease = preferences.frequency > UserPreferences.MIN_FREQUENCY,
                canIncrease = preferences.frequency < UserPreferences.MAX_FREQUENCY,
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                onDecrease = onDecreaseFrequency,
                onIncrease = onIncreaseFrequency
            )

            SettingSliderCard(
                title = "Character Speed (WPM)",
                subtitle = "Individual Morse character speed (5 – 50 WPM)",
                valueLabel = "${preferences.wpm} WPM",
                value = preferences.wpm.toFloat(),
                valueRange = UserPreferences.MIN_WPM.toFloat()..UserPreferences.MAX_WPM.toFloat(),
                steps = WPM_SLIDER_STEPS,
                canDecrease = preferences.wpm > UserPreferences.MIN_WPM,
                canIncrease = preferences.wpm < UserPreferences.MAX_WPM,
                onValueChange = { onWpmChange(it.roundToInt()) },
                onDecrease = onDecreaseWpm,
                onIncrease = onIncreaseWpm
            )

            SettingSliderCard(
                title = "Farnsworth Effective Speed (WPM)",
                subtitle = "Inter-character & word spacing speed (≤ Character WPM)",
                valueLabel = "${preferences.farnsworthWpm} WPM",
                value = preferences.farnsworthWpm.toFloat(),
                valueRange = UserPreferences.MIN_WPM.toFloat()..UserPreferences.MAX_WPM.toFloat(),
                steps = WPM_SLIDER_STEPS,
                canDecrease = preferences.farnsworthWpm > UserPreferences.MIN_WPM,
                canIncrease = preferences.farnsworthWpm < UserPreferences.MAX_WPM,
                onValueChange = { onFarnsworthWpmChange(it.roundToInt()) },
                onDecrease = onDecreaseFarnsworthWpm,
                onIncrease = onIncreaseFarnsworthWpm
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Experimental Features",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enable experimental tools (e.g. Morse Audio Decoder)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = preferences.showExperimentalFeatures,
                        onCheckedChange = onShowExperimentalFeaturesChange
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayTestAudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Test Audio (CQ)")
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    enabled = preferences != UserPreferences(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Reset to Defaults")
                }
            }
        }
    }
}

@Composable
fun SettingSliderCard(
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onValueChange: (Float) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = valueLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onDecrease,
                    enabled = canDecrease
                ) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = onIncrease,
                    enabled = canIncrease
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDefaultPreview() {
    MorseKataTheme {
        SettingsScreen(
            preferences = UserPreferences(),
            onFrequencyChange = {},
            onIncreaseFrequency = {},
            onDecreaseFrequency = {},
            onWpmChange = {},
            onIncreaseWpm = {},
            onDecreaseWpm = {},
            onFarnsworthWpmChange = {},
            onIncreaseFarnsworthWpm = {},
            onDecreaseFarnsworthWpm = {},
            onShowExperimentalFeaturesChange = {},
            onPlayTestAudio = {},
            onResetDefaults = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenBoundsPreview() {
    MorseKataTheme(darkTheme = true) {
        SettingsScreen(
            preferences = UserPreferences(
                frequency = UserPreferences.MIN_FREQUENCY,
                wpm = UserPreferences.MAX_WPM,
                farnsworthWpm = UserPreferences.MIN_WPM,
                showExperimentalFeatures = true
            ),
            onFrequencyChange = {},
            onIncreaseFrequency = {},
            onDecreaseFrequency = {},
            onWpmChange = {},
            onIncreaseWpm = {},
            onDecreaseWpm = {},
            onFarnsworthWpmChange = {},
            onIncreaseFarnsworthWpm = {},
            onDecreaseFarnsworthWpm = {},
            onShowExperimentalFeaturesChange = {},
            onPlayTestAudio = {},
            onResetDefaults = {}
        )
    }
}
