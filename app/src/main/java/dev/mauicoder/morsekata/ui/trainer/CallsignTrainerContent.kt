package dev.mauicoder.morsekata.ui.trainer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.mauicoder.morsekata.R
import dev.mauicoder.morsekata.trainer.CallsignDiffCalculator
import dev.mauicoder.morsekata.trainer.CallsignDrillMode
import dev.mauicoder.morsekata.trainer.CallsignSpeedMode
import dev.mauicoder.morsekata.trainer.CallsignTarget
import dev.mauicoder.morsekata.trainer.CallsignTrainerUiState
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsignTrainerContent(
    state: CallsignTrainerUiState,
    onTypeInput: (String) -> Unit,
    onSubmitInput: () -> Unit,
    onReplayAudio: () -> Unit,
    onNextCallsign: () -> Unit,
    onSelectDrillMode: (CallsignDrillMode) -> Unit,
    onSelectSpeedMode: (CallsignSpeedMode) -> Unit,
    onToggleCutNumbers: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Top Control Bar: Drill Mode & Modifier Chips ---
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.drillMode == CallsignDrillMode.CALLSIGNS_ONLY,
                onClick = {
                    if (state.drillMode != CallsignDrillMode.CALLSIGNS_ONLY) {
                        onSelectDrillMode(CallsignDrillMode.CALLSIGNS_ONLY)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Callsigns Only")
            }
            SegmentedButton(
                selected = state.drillMode == CallsignDrillMode.CONTEST_EXCHANGE,
                onClick = {
                    if (state.drillMode != CallsignDrillMode.CONTEST_EXCHANGE) {
                        onSelectDrillMode(CallsignDrillMode.CONTEST_EXCHANGE)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Contest Exchange")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.speedMode == CallsignSpeedMode.ADAPTIVE,
                onClick = { onSelectSpeedMode(CallsignSpeedMode.ADAPTIVE) },
                label = { Text("Adaptive Speed") }
            )
            FilterChip(
                selected = state.speedMode == CallsignSpeedMode.FIXED,
                onClick = { onSelectSpeedMode(CallsignSpeedMode.FIXED) },
                label = { Text("Fixed Speed") }
            )
            FilterChip(
                selected = state.useCutNumbers,
                onClick = { onToggleCutNumbers(!state.useCutNumbers) },
                label = { Text("Cut Numbers") }
            )
        }

        // --- Speed & Metrics Header Card ---
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.currentWpm} WPM",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Best: ${state.bestWpmReached} WPM",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val accuracyText = if (state.sessionAnsweredCount > 0) {
                        val pct = (state.sessionCorrectCount * 100) / state.sessionAnsweredCount
                        "Accuracy: $pct% (${state.sessionCorrectCount}/${state.sessionAnsweredCount})"
                    } else {
                        "Accuracy: 100%"
                    }
                    Text(
                        text = accuracyText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.comboStreak > 0) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (state.comboStreak > 0) "🔥 Streak: ${state.comboStreak}" else "Streak: 0",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.comboStreak > 0) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // --- Audio Replay Section ---
        FilledTonalButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onReplayAudio()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = if (state.isPlayingAudio) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = if (state.isPlayingAudio) "Stop audio" else "Replay audio",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val replayLabel = if (state.preAnswerReplayCount > 0) {
                "Replay Audio (${state.preAnswerReplayCount})"
            } else {
                "Replay Audio"
            }
            Text(text = replayLabel)
        }

        // --- Input Section ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.typedInput,
                onValueChange = onTypeInput,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitted,
                label = { Text("Callsign / Exchange") },
                placeholder = { Text("Type callsign or exchange...") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (state.typedInput.isNotBlank() && !state.isSubmitted) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSubmitInput()
                        }
                    }
                )
            )

            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSubmitInput()
                },
                enabled = state.typedInput.isNotBlank() && !state.isSubmitted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check")
            }
        }

        // --- Feedback Section ---
        if (state.isSubmitted) {
            if (state.isCorrect) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Correct!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "+${state.earnedXp} XP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        state.currentTarget?.let { target ->
                            Text(
                                text = target.fullTransmissionText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = target.morsePattern,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        if (state.autoAdvanceCountdownActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Next in 1.2s...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            } else {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Incorrect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        val diffSegments = state.diffResult?.diffSegments ?: emptyList()
                        if (diffSegments.isNotEmpty()) {
                            val annotatedDiff = buildAnnotatedString {
                                diffSegments.forEach { segment ->
                                    if (segment.isCorrect) {
                                        withStyle(
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(segment.char)
                                        }
                                    } else {
                                        withStyle(
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.ExtraBold,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        ) {
                                            append(segment.char)
                                        }
                                    }
                                }
                            }
                            Text(
                                text = annotatedDiff,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        state.currentTarget?.let { target ->
                            Text(
                                text = "Expected: ${target.fullTransmissionText}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = target.morsePattern,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onNextCallsign()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next Callsign")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CallsignTrainerIdlePreview() {
    val state = CallsignTrainerUiState(
        currentTarget = CallsignTarget("W1AW", null, "W1AW", ".-- .---- .- .--"),
        currentWpm = 20,
        bestWpmReached = 25,
        comboStreak = 3
    )
    MorseKataTheme {
        Surface {
            CallsignTrainerContent(
                state = state,
                onTypeInput = {},
                onSubmitInput = {},
                onReplayAudio = {},
                onNextCallsign = {},
                onSelectDrillMode = {},
                onSelectSpeedMode = {},
                onToggleCutNumbers = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CallsignTrainerCorrectPreview() {
    val state = CallsignTrainerUiState(
        currentTarget = CallsignTarget("DL1ABC", null, "DL1ABC", "-.. .-.. .---- .- -... -.-."),
        typedInput = "DL1ABC",
        isSubmitted = true,
        isCorrect = true,
        earnedXp = 15,
        currentWpm = 21,
        bestWpmReached = 25,
        comboStreak = 4,
        autoAdvanceCountdownActive = true
    )
    MorseKataTheme {
        Surface {
            CallsignTrainerContent(
                state = state,
                onTypeInput = {},
                onSubmitInput = {},
                onReplayAudio = {},
                onNextCallsign = {},
                onSelectDrillMode = {},
                onSelectSpeedMode = {},
                onToggleCutNumbers = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CallsignTrainerIncorrectPreview() {
    val diff = CallsignDiffCalculator.calculate("DL1ABX", "DL1ABC")
    val state = CallsignTrainerUiState(
        currentTarget = CallsignTarget("DL1ABC", null, "DL1ABC", "-.. .-.. .---- .- -... -.-."),
        typedInput = "DL1ABX",
        isSubmitted = true,
        isCorrect = false,
        diffResult = diff,
        currentWpm = 19,
        bestWpmReached = 25,
        comboStreak = 0
    )
    MorseKataTheme {
        Surface {
            CallsignTrainerContent(
                state = state,
                onTypeInput = {},
                onSubmitInput = {},
                onReplayAudio = {},
                onNextCallsign = {},
                onSelectDrillMode = {},
                onSelectSpeedMode = {},
                onToggleCutNumbers = {}
            )
        }
    }
}
