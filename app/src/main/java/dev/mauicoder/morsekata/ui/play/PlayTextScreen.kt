package dev.mauicoder.morsekata.ui.play

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mauicoder.morsekata.R
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.ui.theme.MorseKataTheme
import dev.mauicoder.morsekata.utils.MorseCodeMaps
import dev.mauicoder.morsekata.viewmodel.PlayTextViewModel

@Composable
fun PlayTextScreen(
    viewModel: PlayTextViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val text by viewModel.textState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }

    PlayTextScreen(
        text = text,
        isPlaying = isPlaying,
        preferences = preferences,
        onTextChange = viewModel::updateText,
        onPlay = viewModel::playMorseCallback,
        onStop = viewModel::stopPlayback,
        onSaveWav = { viewModel.saveMorseAsWaveFile(context) },
        onShareAudio = { viewModel.shareMorseAsWaveFile(context) },
        modifier = modifier
    )
}



/**
 * Converts a text string into formatted Morse code dots and dashes,
 * with standard 1-space character separation and 3-space word separation.
 */
fun formatTextToMorse(text: String): String {
    val trimmed = text.uppercase().trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.split(Regex("\\s+")).joinToString("   ") { word ->
        word.mapNotNull { char -> MorseCodeMaps.asciiToMorse[char] }.joinToString(" ")
    }
}

@Composable
fun PlayTextScreen(
    text: String,
    isPlaying: Boolean,
    preferences: UserPreferences,
    onTextChange: (String) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSaveWav: () -> Unit,
    onShareAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val morseCode = remember(text) { formatTextToMorse(text) }
    val isReady = text.isNotBlank()

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
            // Header
            Text(
                text = "Morse Audio Player",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Current Settings Pill Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingBadge(label = "Pitch", value = "${preferences.frequency} Hz")
                    SettingBadge(label = "Speed", value = "${preferences.wpm} WPM")
                    SettingBadge(label = "Farnsworth", value = "${preferences.farnsworthWpm} WPM")
                }
            }

            // Input Text Field
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Text to Transmit") },
                placeholder = { Text("Enter text (letters, numbers, punctuation)...") },
                trailingIcon = {
                    AnimatedVisibility(visible = text.isNotEmpty()) {
                        IconButton(onClick = { onTextChange("") }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_clear),
                                contentDescription = "Clear input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${text.length} chars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 5
            )

            // Live Morse Code Translation Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
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
                        Text(
                            text = "Morse Code Preview",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (morseCode.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(morseCode))
                                    Toast.makeText(context, "Morse code copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_copy),
                                    contentDescription = "Copy Morse code",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (morseCode.isNotEmpty()) morseCode else "Enter text above to see the Morse code translation...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (morseCode.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 1.sp
                        ),
                        color = if (morseCode.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
            }

            // Primary Play / Stop Action Button
            if (isPlaying) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.96f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stop),
                        contentDescription = "Stop",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Stop Playback",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onPlay,
                    enabled = isReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "Play",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Play Morse Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Export & Share Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveWav,
                    enabled = isReady && !isPlaying,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_save),
                        contentDescription = "Save WAV",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Save WAV")
                }

                OutlinedButton(
                    onClick = onShareAudio,
                    enabled = isReady && !isPlaying,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Share")
                }
            }
        }
    }
}

@Composable
private fun SettingBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayTextScreenDefaultPreview() {
    MorseKataTheme {
        PlayTextScreen(
            text = "PARIS CQ DE",
            isPlaying = false,
            preferences = UserPreferences(),
            onTextChange = {},
            onPlay = {},
            onStop = {},
            onSaveWav = {},
            onShareAudio = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayTextScreenPlayingPreview() {
    MorseKataTheme(darkTheme = true) {
        PlayTextScreen(
            text = "SOS",
            isPlaying = true,
            preferences = UserPreferences(frequency = 600, wpm = 25),
            onTextChange = {},
            onPlay = {},
            onStop = {},
            onSaveWav = {},
            onShareAudio = {}
        )
    }
}
