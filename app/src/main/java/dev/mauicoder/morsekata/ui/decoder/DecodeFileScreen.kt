package dev.mauicoder.morsekata.ui.decoder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dev.mauicoder.morsekata.viewmodel.DecodeFileViewModel

object DecodeFileDefaults {
    const val TITLE = "Decode Audio File"
    fun buttonText(isDecoding: Boolean): String = if (isDecoding) "Decoding..." else "Decode File"
    fun isButtonEnabled(isDecoding: Boolean): Boolean = !isDecoding
}

/**
 * Stateful entry point for the Decode Audio File screen.
 */
@Composable
fun DecodeFileScreen(
    viewModel: DecodeFileViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onDecodeClicked: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val decodedText by viewModel.decodedText.collectAsStateWithLifecycle()
    val isDecoding by viewModel.isDecoding.collectAsStateWithLifecycle()

    DecodeFileScreen(
        decodedText = decodedText,
        isDecoding = isDecoding,
        onDecodeClicked = onDecodeClicked ?: { viewModel.startDecoding(context, playAudio = false) },
        modifier = modifier
    )
}

/**
 * Stateless presentation of the Decode Audio File screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecodeFileScreen(
    decodedText: String,
    isDecoding: Boolean,
    onDecodeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(DecodeFileDefaults.TITLE) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isDecoding) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Decoded Output",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (decodedText.isNotEmpty()) {
                            Text(
                                text = decodedText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Decoded text will appear here once processing begins.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDecodeClicked,
                enabled = DecodeFileDefaults.isButtonEnabled(isDecoding),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = DecodeFileDefaults.buttonText(isDecoding),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
