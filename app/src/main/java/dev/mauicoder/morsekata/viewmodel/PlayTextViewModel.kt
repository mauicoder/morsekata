package dev.mauicoder.morsekata.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.mauicoder.morsekata.ShareFile
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.generator.MorseAudioPlayer
import dev.mauicoder.morsekata.generator.MorseAudioPlayerImpl
import dev.mauicoder.morsekata.generator.MorseSoundGenerator
import dev.mauicoder.morsekata.utils.MorseCodeMaps
import javax.inject.Inject

@HiltViewModel
class PlayTextViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val morseAudioPlayer: MorseAudioPlayer
) : ViewModel() {

    private var coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default

    internal constructor(
        userPreferencesRepository: UserPreferencesRepository,
        morseAudioPlayer: MorseAudioPlayer,
        dispatcher: CoroutineDispatcher
    ) : this(userPreferencesRepository, morseAudioPlayer) {
        this.coroutineDispatcher = dispatcher
    }

    constructor(userPreferencesRepository: UserPreferencesRepository) : this(
        userPreferencesRepository,
        MorseAudioPlayerImpl()
    )

    private val SAMPLE_RATE = 44100
    private val TAG = "PlayTextViewModel"

    private val morseSoundGenerator = MorseSoundGenerator()
    private val shareFile = ShareFile()

    private val _textState = MutableStateFlow("Hello")
    val textState: StateFlow<String> = _textState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null

    val textLiveData: MutableLiveData<String> = MutableLiveData("Hello")

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    val isReady: LiveData<Boolean> = userPreferences.map { true }.asLiveData()

    fun updateText(newText: String) {
        _textState.value = newText
        textLiveData.value = newText
    }

    fun playMorseCallback() {
        val prefs = userPreferences.value
        val text = _textState.value
        if (text.isBlank()) return

        playbackJob?.cancel()
        _isPlaying.value = true
        morseAudioPlayer.play(text, prefs)

        val durationMs = calculateDurationMs(text, prefs)
        playbackJob = viewModelScope.launch(coroutineDispatcher) {
            delay(durationMs)
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        morseAudioPlayer.stop()
    }

    private fun calculateDurationMs(text: String, prefs: UserPreferences): Long {
        val validWords = text.uppercase()
            .trim()
            .split(Regex("\\s+"))
            .map { word -> word.mapNotNull { char -> MorseCodeMaps.asciiToMorse[char] } }
            .filter { it.isNotEmpty() }

        if (validWords.isEmpty()) return 0L

        val dotMs = 1200L / prefs.wpm
        val dashMs = 3 * dotMs
        val intraCharGapMs = dotMs
        val farnsworthDotMs = 1200L / prefs.farnsworthWpm
        val interCharGapMs = 3 * farnsworthDotMs
        val interWordGapMs = 7 * farnsworthDotMs

        var totalMs = 100L

        for (wIndex in validWords.indices) {
            val word = validWords[wIndex]
            for (cIndex in word.indices) {
                val morseChar = word[cIndex]
                for (eIndex in morseChar.indices) {
                    val elem = morseChar[eIndex]
                    totalMs += if (elem == '-') dashMs else dotMs
                    if (eIndex < morseChar.length - 1) {
                        totalMs += intraCharGapMs
                    }
                }
                if (cIndex < word.size - 1) {
                    totalMs += interCharGapMs
                }
            }
            if (wIndex < validWords.size - 1) {
                totalMs += interWordGapMs
            }
        }
        return totalMs
    }

    fun shareMorseAsWaveFile(context: Context) {
        val prefs = userPreferences.value
        val text = _textState.value
        if (text.isBlank()) return

        val waveStream = morseSoundGenerator.generate(
            text,
            prefs.wpm,
            prefs.farnsworthWpm,
            prefs.frequency,
            SAMPLE_RATE
        )
        // Convert ShortArray to WAV ByteArray
        val wavBytes = morseSoundGenerator.generateWave(
            text,
            prefs.wpm,
            prefs.farnsworthWpm,
            prefs.frequency,
            SAMPLE_RATE
        )
        shareFile.shareAsFile(context, wavBytes)
    }

    fun saveMorseAsWaveFile(context: Context) {
        val prefs = userPreferences.value
        val text = _textState.value
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val waveStream = morseSoundGenerator.generateWave(
                text,
                prefs.wpm,
                prefs.farnsworthWpm,
                prefs.frequency,
                SAMPLE_RATE
            )

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "morse_output_${System.currentTimeMillis()}.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/MorseKata")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, contentValues)

            uri?.let {
                try {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(waveStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "File saved to Music/MorseKata", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving file", e)
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Error saving file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
