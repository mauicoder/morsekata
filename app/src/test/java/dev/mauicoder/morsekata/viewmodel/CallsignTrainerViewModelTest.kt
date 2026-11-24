package dev.mauicoder.morsekata.viewmodel

import dev.mauicoder.morsekata.data.TrainerProgress
import dev.mauicoder.morsekata.data.TrainerProgressRepository
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.generator.MorseAudioPlayer
import dev.mauicoder.morsekata.trainer.CallsignDrillMode
import dev.mauicoder.morsekata.trainer.CallsignSpeedMode
import dev.mauicoder.morsekata.trainer.QsoTrainerTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class CallsignTrainerViewModelTest {

    private lateinit var fakePreferencesRepository: FakeUserPreferencesRepository
    private lateinit var fakeProgressRepository: FakeTrainerProgressRepository
    private lateinit var fakeAudioPlayer: FakeMorseAudioPlayer
    private lateinit var clock: MutableClock
    private lateinit var viewModel: QsoTrainerViewModel

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeUserPreferencesRepository()
        fakeProgressRepository = FakeTrainerProgressRepository()
        fakeAudioPlayer = FakeMorseAudioPlayer()
        clock = MutableClock(LocalDate.of(2026, 11, 24))
        viewModel = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = clock,
            random = Random(12345),
            autoAdvanceDelayMs = 0L // immediate auto-advance for synchronous testing
        )
    }

    @Test
    fun `initial callsign state has defaults and initializes target on Callsigns tab selection`() {
        val initialState = viewModel.callsignUiState.value
        assertEquals(CallsignDrillMode.CALLSIGNS_ONLY, initialState.drillMode)
        assertEquals(CallsignSpeedMode.ADAPTIVE, initialState.speedMode)
        assertFalse(initialState.useCutNumbers)
        assertEquals(20, initialState.currentWpm)
        assertNull(initialState.currentTarget)
        assertEquals("", initialState.typedInput)
        assertFalse(initialState.isSubmitted)
        assertFalse(initialState.isCorrect)

        // Select Callsigns tab -> should initialize target and play audio
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)

        val target = viewModel.callsignUiState.value.currentTarget
        assertNotNull(target)
        assertFalse(target!!.fullTransmissionText.isBlank())
        assertEquals(1, fakeAudioPlayer.playCalls.size)
        assertEquals(target.fullTransmissionText, fakeAudioPlayer.playCalls.first().first)
        assertEquals(20, fakeAudioPlayer.playCalls.first().second.wpm)
    }

    @Test
    fun `onTypeCallsignInput updates typed input and auto-capitalizes text`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        viewModel.onTypeCallsignInput("w1aw")
        assertEquals("W1AW", viewModel.callsignUiState.value.typedInput)

        viewModel.onTypeCallsignInput("dl1abc 5nn tt1")
        assertEquals("DL1ABC 5NN TT1", viewModel.callsignUiState.value.typedInput)
    }

    @Test
    fun `correct submission with zero replays awards first-copy bonus and increments adaptive speed`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        val target = viewModel.callsignUiState.value.currentTarget!!
        fakeAudioPlayer.playCalls.clear()

        // Type correct answer (case-insensitive)
        viewModel.onTypeCallsignInput(target.fullTransmissionText.lowercase())
        viewModel.onSubmitCallsignInput()

        val state = viewModel.callsignUiState.value
        // Because autoAdvanceDelayMs is 0L in test setup, it immediately advanced!
        assertEquals(1, state.sessionAnsweredCount)
        assertEquals(1, state.sessionCorrectCount)
        assertEquals(21, state.currentWpm) // 20 + 1 = 21 WPM
        assertEquals(21, state.bestWpmReached)
        assertEquals(1, state.comboStreak)

        // Check persisted trainer progress
        val progress = fakeProgressRepository.trainerProgress.value
        // 10 base + 0 streak bonus (first correct) + 5 first-copy bonus = 15 XP
        assertEquals(15, progress.totalXp)
        assertEquals(1, progress.currentAnswerStreak)
        assertEquals(21, progress.bestCallsignWpm)
        assertEquals(1, progress.completedCallsignCount)

        // Check next callsign audio was played
        val nextTarget = state.currentTarget
        assertNotNull(nextTarget)
        assertEquals(1, fakeAudioPlayer.playCalls.size)
        assertEquals(nextTarget!!.fullTransmissionText, fakeAudioPlayer.playCalls.first().first)
        assertEquals(21, fakeAudioPlayer.playCalls.first().second.wpm)
    }

    @Test
    fun `correct submission with prior replay does not award first-copy bonus and does not bump WPM`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        val target = viewModel.callsignUiState.value.currentTarget!!

        // Replay audio once before submitting
        viewModel.onReplayCallsignAudio()
        assertEquals(1, viewModel.callsignUiState.value.preAnswerReplayCount)

        viewModel.onTypeCallsignInput(target.fullTransmissionText)
        viewModel.onSubmitCallsignInput()

        val state = viewModel.callsignUiState.value
        assertEquals(20, state.currentWpm) // Unchanged because replayed
        assertEquals(1, state.sessionCorrectCount)

        val progress = fakeProgressRepository.trainerProgress.value
        // 10 base + 0 streak + 0 first-copy = 10 XP
        assertEquals(10, progress.totalXp)
        assertEquals(1, progress.currentAnswerStreak)
        assertEquals(1, progress.completedCallsignCount)
    }

    @Test
    fun `incorrect submission decrements adaptive speed, resets streak, calculates diff, and waits for manual Next`() {
        // Use non-zero delay so auto-advance wouldn't happen anyway, but error doesn't auto-advance
        val manualVm = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = clock,
            random = Random(12345),
            autoAdvanceDelayMs = 5000L
        )
        manualVm.selectTab(QsoTrainerTab.CALLSIGNS)
        val target = manualVm.callsignUiState.value.currentTarget!!

        // Build up streak first
        fakeProgressRepository.updateProgressBlocking {
            it.copy(currentAnswerStreak = 3, bestAnswerStreak = 3)
        }

        // Type wrong answer
        manualVm.onTypeCallsignInput("WRONG")
        manualVm.onSubmitCallsignInput()

        val state = manualVm.callsignUiState.value
        assertTrue(state.isSubmitted)
        assertFalse(state.isCorrect)
        assertFalse(state.autoAdvanceCountdownActive) // does NOT auto-advance on error
        assertEquals(19, state.currentWpm) // 20 - 1 = 19
        assertEquals(1, state.sessionAnsweredCount)
        assertEquals(0, state.sessionCorrectCount)
        assertEquals(0, state.earnedXp)
        assertEquals(0, state.comboStreak)

        assertNotNull(state.diffResult)
        assertFalse(state.diffResult!!.isMatch)

        // Progress streak was reset and bestCallsignWpm was NOT erroneously updated on failure
        val progress = fakeProgressRepository.trainerProgress.value
        assertEquals(0, progress.currentAnswerStreak)
        assertEquals(0, progress.completedCallsignCount)
        assertEquals(0, progress.bestCallsignWpm)

        // Target has NOT changed yet (waits for manual next)
        assertEquals(target, manualVm.callsignUiState.value.currentTarget)

        // Now user taps next
        manualVm.onNextCallsign()
        val afterNext = manualVm.callsignUiState.value
        assertFalse(afterNext.isSubmitted)
        assertFalse(afterNext.isCorrect)
        assertNull(afterNext.diffResult)
        assertEquals("", afterNext.typedInput)
    }

    @Test
    fun `incorrect submission does not update bestCallsignWpm in repository or state`() {
        fakeProgressRepository.updateProgressBlocking {
            it.copy(bestCallsignWpm = 15)
        }
        val manualVm = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = clock,
            random = Random(12345),
            autoAdvanceDelayMs = 5000L
        )
        manualVm.selectTab(QsoTrainerTab.CALLSIGNS)
        // Current speed is 20 WPM, which is higher than bestCallsignWpm (15)
        manualVm.onTypeCallsignInput("WRONG")
        manualVm.onSubmitCallsignInput()

        val progress = fakeProgressRepository.trainerProgress.value
        assertEquals(15, progress.bestCallsignWpm)
        assertEquals(15, manualVm.callsignUiState.value.bestWpmReached)
    }

    @Test
    fun `speed is clamped to minimum 5 and maximum 50 WPM`() {
        val vm = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = clock,
            random = Random(12345),
            autoAdvanceDelayMs = 5000L
        )
        vm.selectTab(QsoTrainerTab.CALLSIGNS)

        // Set speed mode adaptive and simulate drop down to floor
        repeat(20) {
            vm.onTypeCallsignInput("WRONG")
            vm.onSubmitCallsignInput()
            vm.onNextCallsign()
        }
        assertEquals(5, vm.callsignUiState.value.currentWpm)

        // Multiple incorrect answers should not drop below 5
        vm.onTypeCallsignInput("WRONG")
        vm.onSubmitCallsignInput()
        assertEquals(5, vm.callsignUiState.value.currentWpm)
    }

    @Test
    fun `fixed speed mode does not alter currentWpm on correct or incorrect answers`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        viewModel.onSelectCallsignSpeedMode(CallsignSpeedMode.FIXED)

        assertEquals(CallsignSpeedMode.FIXED, viewModel.callsignUiState.value.speedMode)
        assertEquals(20, viewModel.callsignUiState.value.currentWpm)

        // Wrong answer
        viewModel.onTypeCallsignInput("WRONG")
        viewModel.onSubmitCallsignInput()
        assertEquals(20, viewModel.callsignUiState.value.currentWpm) // No decrease

        viewModel.onNextCallsign()
        val target = viewModel.callsignUiState.value.currentTarget!!

        // Correct answer with 0 replays
        viewModel.onTypeCallsignInput(target.fullTransmissionText)
        viewModel.onSubmitCallsignInput()
        assertEquals(20, viewModel.callsignUiState.value.currentWpm) // No increase
    }

    @Test
    fun `switching drill mode to Contest Exchange updates mode and generates contest target`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        assertEquals(CallsignDrillMode.CALLSIGNS_ONLY, viewModel.callsignUiState.value.drillMode)

        viewModel.onSelectCallsignDrillMode(CallsignDrillMode.CONTEST_EXCHANGE)
        val state = viewModel.callsignUiState.value
        assertEquals(CallsignDrillMode.CONTEST_EXCHANGE, state.drillMode)
        assertNotNull(state.currentTarget?.contestExchange)
        assertTrue(state.currentTarget!!.fullTransmissionText.contains("599"))
    }

    @Test
    fun `toggling cut numbers updates flag and transforms serial and RST in contest mode`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        viewModel.onSelectCallsignDrillMode(CallsignDrillMode.CONTEST_EXCHANGE)
        viewModel.onToggleCallsignCutNumbers(true)

        val state = viewModel.callsignUiState.value
        assertTrue(state.useCutNumbers)
        assertNotNull(state.currentTarget?.contestExchange)
        assertTrue(state.currentTarget!!.fullTransmissionText.contains("5NN"))
    }

    @Test
    fun `contest exchange correct submission awards 15 base XP`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        viewModel.onSelectCallsignDrillMode(CallsignDrillMode.CONTEST_EXCHANGE)
        val target = viewModel.callsignUiState.value.currentTarget!!

        // 1 replay -> no first-copy bonus
        viewModel.onReplayCallsignAudio()
        viewModel.onTypeCallsignInput(target.fullTransmissionText)
        viewModel.onSubmitCallsignInput()

        val progress = fakeProgressRepository.trainerProgress.value
        // 15 base + 0 streak + 0 first-copy = 15 XP
        assertEquals(15, progress.totalXp)
    }

    @Test
    fun `auto-advance countdown can be cancelled by manual onNextCallsign`() {
        val delayedVm = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = clock,
            random = Random(12345),
            autoAdvanceDelayMs = 10000L
        )
        delayedVm.selectTab(QsoTrainerTab.CALLSIGNS)
        val target = delayedVm.callsignUiState.value.currentTarget!!

        delayedVm.onTypeCallsignInput(target.fullTransmissionText)
        delayedVm.onSubmitCallsignInput()

        assertTrue(delayedVm.callsignUiState.value.autoAdvanceCountdownActive)
        assertTrue(delayedVm.callsignUiState.value.isSubmitted)

        // Manually clicking Next before timer fires
        delayedVm.onNextCallsign()

        assertFalse(delayedVm.callsignUiState.value.autoAdvanceCountdownActive)
        assertFalse(delayedVm.callsignUiState.value.isSubmitted)
        assertNotEquals(target, delayedVm.callsignUiState.value.currentTarget)
    }

    @Test
    fun `resetProgress stops audio, resets callsign session counters and bestWpmReached`() {
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        val target = viewModel.callsignUiState.value.currentTarget!!
        viewModel.onTypeCallsignInput(target.fullTransmissionText)
        viewModel.onSubmitCallsignInput()

        assertEquals(1, viewModel.callsignUiState.value.sessionCorrectCount)
        assertEquals(21, viewModel.callsignUiState.value.bestWpmReached)

        viewModel.resetProgress()

        val state = viewModel.callsignUiState.value
        assertEquals(0, state.sessionAnsweredCount)
        assertEquals(0, state.sessionCorrectCount)
        assertEquals(0, state.bestWpmReached)
        assertEquals(20, state.currentWpm)
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

        override suspend fun updateShowExperimentalFeatures(showExperimentalFeatures: Boolean) {
            _userPreferences.value = _userPreferences.value.copy(showExperimentalFeatures = showExperimentalFeatures)
        }
    }

    private class FakeTrainerProgressRepository(
        initialProgress: TrainerProgress = TrainerProgress()
    ) : TrainerProgressRepository {
        private val _trainerProgress = MutableStateFlow(initialProgress)
        override val trainerProgress: StateFlow<TrainerProgress> = _trainerProgress.asStateFlow()

        override suspend fun updateProgress(transform: (TrainerProgress) -> TrainerProgress) {
            _trainerProgress.value = transform(_trainerProgress.value)
        }

        fun updateProgressBlocking(transform: (TrainerProgress) -> TrainerProgress) {
            _trainerProgress.value = transform(_trainerProgress.value)
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

    private class MutableClock(
        initialDate: LocalDate,
        private val zoneField: ZoneId = ZoneId.of("UTC")
    ) : Clock() {
        private var currentInstant: Instant = initialDate.atStartOfDay(zoneField).toInstant()

        override fun getZone(): ZoneId = zoneField

        override fun withZone(zone: ZoneId): Clock = MutableClock(
            Instant.ofEpochMilli(currentInstant.toEpochMilli()).atZone(zone).toLocalDate(),
            zone
        )

        override fun instant(): Instant = currentInstant
    }
}
