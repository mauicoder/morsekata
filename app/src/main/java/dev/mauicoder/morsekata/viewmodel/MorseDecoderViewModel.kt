package dev.mauicoder.morsekata.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.mauicoder.morsekata.decoder.MorseDecoder
import dev.mauicoder.morsekata.recorder.AudioRecorder
import javax.inject.Inject

@HiltViewModel
class MorseDecoderViewModel @Inject constructor() : ViewModel() {

    private val _decodedText = MutableStateFlow("")
    val decodedText: StateFlow<String> = _decodedText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var audioRecorder: AudioRecorder? = null
    private var morseDecoder: MorseDecoder? = null

    init {
        morseDecoder = MorseDecoder(
            onDecoded = { text ->
                _decodedText.value += text
            }
        )
    }

    fun startListening(context: Context) {
        if (_isListening.value) return

        val decoder = morseDecoder ?: MorseDecoder(
            onDecoded = { text ->
                _decodedText.value += text
            }
        ).also { morseDecoder = it }
        decoder.reset()

        try {
            audioRecorder = AudioRecorder(
                context = context,
                sampleRate = 16000,
                blockSize = 512,
                onBufferReady = decoder::processBuffer
            ).also {
                it.start()
            }
            _isListening.value = true
        } catch (e: SecurityException) {
            _isListening.value = false
            audioRecorder = null
        }
    }

    fun stopListening() {
        audioRecorder?.stop()
        audioRecorder = null
        _isListening.value = false
    }

    fun clearDecodedText() {
        _decodedText.value = ""
        morseDecoder?.reset()
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
