package net.maui.morselab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext

@HiltViewModel
class UserPreferencesViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val morseAudioPlayer: MorseAudioPlayer
) : ViewModel() {

    private var coroutineDispatcher: CoroutineDispatcher? = null

    internal constructor(
        userPreferencesRepository: UserPreferencesRepository,
        morseAudioPlayer: MorseAudioPlayer,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        morseAudioPlayer = morseAudioPlayer
    ) {
        this.coroutineDispatcher = dispatcher
    }

    // Use the StateFlow directly from the repository
    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    fun increaseFrequency() {
        setFrequency(userPreferences.value.frequency + FREQUENCY_STEP)
    }

    fun decreaseFrequency() {
        setFrequency(userPreferences.value.frequency - FREQUENCY_STEP)
    }

    fun setFrequency(frequency: Int) {
        val snapped = (((frequency + 25) / FREQUENCY_STEP) * FREQUENCY_STEP)
            .coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
        updateFrequency(snapped)
    }

    fun increaseWpm() {
        setWpm(userPreferences.value.wpm + WPM_STEP)
    }

    fun decreaseWpm() {
        setWpm(userPreferences.value.wpm - WPM_STEP)
    }

    fun setWpm(wpm: Int) {
        val newValue = wpm.coerceIn(MIN_WPM, MAX_WPM)
        val shouldClampFarnsworth = userPreferences.value.farnsworthWpm > newValue
        updateWpm(newValue)
        if (shouldClampFarnsworth) {
            updateFarnsworthWpm(newValue)
        }
    }

    fun increaseFarnsworthWpm() {
        setFarnsworthWpm(userPreferences.value.farnsworthWpm + WPM_STEP)
    }

    fun decreaseFarnsworthWpm() {
        setFarnsworthWpm(userPreferences.value.farnsworthWpm - WPM_STEP)
    }

    fun setFarnsworthWpm(farnsworthWpm: Int) {
        val newValue = farnsworthWpm.coerceIn(MIN_WPM, MAX_WPM)
        val shouldRaiseWpm = newValue > userPreferences.value.wpm
        updateFarnsworthWpm(newValue)
        if (shouldRaiseWpm) {
            updateWpm(newValue)
        }
    }

    fun playTestAudio() {
        morseAudioPlayer.play(TEST_AUDIO_SAMPLE, userPreferences.value)
    }

    fun stopTestAudio() {
        morseAudioPlayer.stop()
    }

    fun resetToDefaults() {
        morseAudioPlayer.stop()
        launchUpdate {
            userPreferencesRepository.updateFrequency(DEFAULT_FREQUENCY)
            userPreferencesRepository.updateWpm(DEFAULT_WPM)
            userPreferencesRepository.updateFarnsworthWpm(DEFAULT_WPM)
        }
    }

    private fun updateFrequency(frequency: Int) {
        launchUpdate {
            userPreferencesRepository.updateFrequency(frequency)
        }
    }

    private fun updateWpm(wpm: Int) {
        launchUpdate {
            userPreferencesRepository.updateWpm(wpm)
        }
    }

    private fun updateFarnsworthWpm(farnsworthWpm: Int) {
        launchUpdate {
            userPreferencesRepository.updateFarnsworthWpm(farnsworthWpm)
        }
    }

    private fun launchUpdate(block: suspend CoroutineScope.() -> Unit) {
        val dispatcher = coroutineDispatcher
        if (dispatcher != null) {
            viewModelScope.launch(dispatcher, block = block)
        } else {
            try {
                viewModelScope.launch(EmptyCoroutineContext, block = block)
            } catch (_: IllegalStateException) {
                coroutineDispatcher = Dispatchers.Unconfined
                viewModelScope.launch(Dispatchers.Unconfined, block = block)
            }
        }
    }

    companion object {
        const val DEFAULT_FREQUENCY = UserPreferences.DEFAULT_FREQUENCY
        const val MIN_FREQUENCY = UserPreferences.MIN_FREQUENCY
        const val MAX_FREQUENCY = UserPreferences.MAX_FREQUENCY
        const val FREQUENCY_STEP = UserPreferences.FREQUENCY_STEP

        const val DEFAULT_WPM = UserPreferences.DEFAULT_WPM
        const val MIN_WPM = UserPreferences.MIN_WPM
        const val MAX_WPM = UserPreferences.MAX_WPM
        const val WPM_STEP = UserPreferences.WPM_STEP

        const val TEST_AUDIO_SAMPLE = "CQ"
    }
}