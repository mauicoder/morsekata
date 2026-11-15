package net.maui.morselab.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPreferencesViewModelTest {

    private lateinit var fakeRepository: FakeUserPreferencesRepository
    private lateinit var fakeAudioPlayer: FakeMorseAudioPlayer
    private lateinit var viewModel: UserPreferencesViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeUserPreferencesRepository()
        fakeAudioPlayer = FakeMorseAudioPlayer()
        viewModel = UserPreferencesViewModel(
            userPreferencesRepository = fakeRepository,
            morseAudioPlayer = fakeAudioPlayer,
            dispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `frequency steppers and setFrequency snap to 50 Hz increments and clamp within 100 to 1000 Hz`() {
        assertEquals(700, viewModel.userPreferences.value.frequency)

        viewModel.increaseFrequency()
        assertEquals(750, viewModel.userPreferences.value.frequency)

        viewModel.decreaseFrequency()
        assertEquals(700, viewModel.userPreferences.value.frequency)

        // Direct slider setter snaps to nearest 50 Hz
        viewModel.setFrequency(624)
        assertEquals(600, viewModel.userPreferences.value.frequency)

        viewModel.setFrequency(625)
        assertEquals(650, viewModel.userPreferences.value.frequency)

        // Upper bound clamping via setter and stepper
        viewModel.setFrequency(1250)
        assertEquals(1000, viewModel.userPreferences.value.frequency)

        viewModel.increaseFrequency()
        assertEquals(1000, viewModel.userPreferences.value.frequency)

        // Lower bound clamping via setter and stepper
        viewModel.setFrequency(40)
        assertEquals(100, viewModel.userPreferences.value.frequency)

        viewModel.decreaseFrequency()
        assertEquals(100, viewModel.userPreferences.value.frequency)
    }

    @Test
    fun `wpm steppers and setWpm clamp within 5 to 50 WPM and pull farnsworthWpm down when wpm drops below it`() {
        assertEquals(20, viewModel.userPreferences.value.wpm)
        assertEquals(20, viewModel.userPreferences.value.farnsworthWpm)

        viewModel.increaseWpm()
        assertEquals(21, viewModel.userPreferences.value.wpm)
        assertEquals(20, viewModel.userPreferences.value.farnsworthWpm)

        viewModel.decreaseWpm()
        assertEquals(20, viewModel.userPreferences.value.wpm)
        assertEquals(20, viewModel.userPreferences.value.farnsworthWpm)

        // Stepping wpm below farnsworthWpm pulls farnsworthWpm down
        viewModel.decreaseWpm()
        assertEquals(19, viewModel.userPreferences.value.wpm)
        assertEquals(19, viewModel.userPreferences.value.farnsworthWpm)

        // Direct slider setter below farnsworthWpm clamps farnsworthWpm down
        viewModel.setWpm(12)
        assertEquals(12, viewModel.userPreferences.value.wpm)
        assertEquals(12, viewModel.userPreferences.value.farnsworthWpm)

        // Direct slider setter above farnsworthWpm leaves farnsworthWpm unchanged
        viewModel.setWpm(30)
        assertEquals(30, viewModel.userPreferences.value.wpm)
        assertEquals(12, viewModel.userPreferences.value.farnsworthWpm)

        // Upper bound clamping via setter and stepper
        viewModel.setWpm(85)
        assertEquals(50, viewModel.userPreferences.value.wpm)
        assertEquals(12, viewModel.userPreferences.value.farnsworthWpm)

        viewModel.increaseWpm()
        assertEquals(50, viewModel.userPreferences.value.wpm)

        // Lower bound clamping via setter and stepper (also pulls farnsworthWpm to 5)
        viewModel.setWpm(2)
        assertEquals(5, viewModel.userPreferences.value.wpm)
        assertEquals(5, viewModel.userPreferences.value.farnsworthWpm)

        viewModel.decreaseWpm()
        assertEquals(5, viewModel.userPreferences.value.wpm)
        assertEquals(5, viewModel.userPreferences.value.farnsworthWpm)
    }

    @Test
    fun `farnsworthWpm steppers and setFarnsworthWpm clamp within 5 to 50 WPM and push wpm up when farnsworthWpm exceeds it`() {
        assertEquals(20, viewModel.userPreferences.value.wpm)
        assertEquals(20, viewModel.userPreferences.value.farnsworthWpm)

        viewModel.decreaseFarnsworthWpm()
        assertEquals(19, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(20, viewModel.userPreferences.value.wpm)

        viewModel.increaseFarnsworthWpm()
        assertEquals(20, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(20, viewModel.userPreferences.value.wpm)

        // Stepping farnsworthWpm above wpm pushes wpm up
        viewModel.increaseFarnsworthWpm()
        assertEquals(21, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(21, viewModel.userPreferences.value.wpm)

        // Direct slider setter above wpm pushes wpm up
        viewModel.setFarnsworthWpm(35)
        assertEquals(35, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(35, viewModel.userPreferences.value.wpm)

        // Direct slider setter below wpm leaves wpm unchanged
        viewModel.setFarnsworthWpm(15)
        assertEquals(15, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(35, viewModel.userPreferences.value.wpm)

        // Upper bound clamping via setter and stepper (also pushes wpm to 50)
        viewModel.setFarnsworthWpm(70)
        assertEquals(50, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(50, viewModel.userPreferences.value.wpm)

        viewModel.increaseFarnsworthWpm()
        assertEquals(50, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(50, viewModel.userPreferences.value.wpm)

        // Lower bound clamping via setter and stepper
        viewModel.setFarnsworthWpm(1)
        assertEquals(5, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(50, viewModel.userPreferences.value.wpm)

        viewModel.decreaseFarnsworthWpm()
        assertEquals(5, viewModel.userPreferences.value.farnsworthWpm)
        assertEquals(50, viewModel.userPreferences.value.wpm)
    }

    @Test
    fun `playTestAudio plays CQ sample with current UserPreferences and stopTestAudio stops playback`() {
        viewModel.playTestAudio()
        assertEquals(1, fakeAudioPlayer.playCalls.size)
        assertEquals("CQ" to UserPreferences(frequency = 700, wpm = 20, farnsworthWpm = 20), fakeAudioPlayer.playCalls.last())

        viewModel.setFrequency(550)
        viewModel.setWpm(28)
        viewModel.setFarnsworthWpm(15)

        viewModel.playTestAudio()
        assertEquals(2, fakeAudioPlayer.playCalls.size)
        assertEquals("CQ" to UserPreferences(frequency = 550, wpm = 28, farnsworthWpm = 15), fakeAudioPlayer.playCalls.last())

        assertEquals(0, fakeAudioPlayer.stopCallCount)
        viewModel.stopTestAudio()
        assertEquals(1, fakeAudioPlayer.stopCallCount)
    }

    @Test
    fun `resetToDefaults stops audio playback and restores default frequency wpm and farnsworthWpm`() {
        viewModel.setFrequency(850)
        viewModel.setWpm(32)
        viewModel.setFarnsworthWpm(14)
        assertEquals(UserPreferences(frequency = 850, wpm = 32, farnsworthWpm = 14), viewModel.userPreferences.value)

        val stopCountBefore = fakeAudioPlayer.stopCallCount
        viewModel.resetToDefaults()

        assertEquals(stopCountBefore + 1, fakeAudioPlayer.stopCallCount)
        assertEquals(UserPreferences(frequency = 700, wpm = 20, farnsworthWpm = 20), viewModel.userPreferences.value)
    }

    @Test
    fun `legacy LiveData properties frequencyFlow wpmFlow and farnsworthWpmFlow are removed`() {
        val methodNames = UserPreferencesViewModel::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("Expected getFrequencyFlow to be removed", "getFrequencyFlow" !in methodNames)
        assertTrue("Expected getWpmFlow to be removed", "getWpmFlow" !in methodNames)
        assertTrue("Expected getFarnsworthWpmFlow to be removed", "getFarnsworthWpmFlow" !in methodNames)
    }

    private class FakeUserPreferencesRepository(
        initialPreferences: UserPreferences = UserPreferences()
    ) : UserPreferencesRepository {
        private val _userPreferences = MutableStateFlow(initialPreferences)
        override val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

        override suspend fun updateFrequency(frequency: Int) {
            _userPreferences.value = _userPreferences.value.copy(frequency = frequency)
        }

        override suspend fun updateWpm(wpm: Int) {
            _userPreferences.value = _userPreferences.value.copy(wpm = wpm)
        }

        override suspend fun updateFarnsworthWpm(farnsworthWpm: Int) {
            _userPreferences.value = _userPreferences.value.copy(farnsworthWpm = farnsworthWpm)
        }
    }

    private class FakeMorseAudioPlayer : MorseAudioPlayer {
        val playCalls = mutableListOf<Pair<String, UserPreferences>>()
        var stopCallCount = 0

        override fun play(text: String, preferences: UserPreferences) {
            playCalls.add(text to preferences)
        }

        override fun stop() {
            stopCallCount++
        }
    }
}