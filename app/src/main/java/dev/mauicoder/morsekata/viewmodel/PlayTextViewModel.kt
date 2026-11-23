package dev.mauicoder.morsekata.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.mauicoder.morsekata.ShareFile
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.generator.MorseAudioPlayer
import dev.mauicoder.morsekata.generator.MorseAudioPlayerImpl
import dev.mauicoder.morsekata.generator.MorseSoundGenerator
import javax.inject.Inject

@HiltViewModel
class PlayTextViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val morseAudioPlayer: MorseAudioPlayer
) : ViewModel() {

    constructor(userPreferencesRepository: UserPreferencesRepository) : this(
        userPreferencesRepository,
        MorseAudioPlayerImpl()
    )

    private val SAMPLE_RATE = 44100
    private val TAG = "PlayTextViewModel"

    private val morseSoundGenerator = MorseSoundGenerator()
    private val shareFile = ShareFile()

    val textLiveData: MutableLiveData<String> = MutableLiveData("Hello")

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    val isReady: LiveData<Boolean> = userPreferences.map { true }.asLiveData()

    fun playMorseCallback() {
        val prefs = userPreferences.value
        val text = textLiveData.value ?: return
        morseAudioPlayer.play(text, prefs)
    }

    fun stopPlayback() {
        morseAudioPlayer.stop()
    }

    fun shareMorseAsWaveFile(activity: FragmentActivity) {
        val prefs = userPreferences.value
        val text = textLiveData.value ?: return

        val waveStream = morseSoundGenerator.generateWave(
            text,
            prefs.wpm,
            prefs.farnsworthWpm,
            prefs.frequency,
            SAMPLE_RATE
        )
        shareFile.shareAsFile(activity, waveStream)
    }

    fun saveMorseAsWaveFile(context: Context) {
        val prefs = userPreferences.value
        val text = textLiveData.value ?: return

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
}
