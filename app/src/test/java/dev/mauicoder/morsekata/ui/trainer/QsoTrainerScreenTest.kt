package dev.mauicoder.morsekata.ui.trainer

import dev.mauicoder.morsekata.data.TrainerProgress
import dev.mauicoder.morsekata.data.TrainerProgressRepository
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.generator.MorseAudioPlayer
import dev.mauicoder.morsekata.trainer.OptionHighlightState
import dev.mauicoder.morsekata.trainer.QsoCatalog
import dev.mauicoder.morsekata.trainer.QsoExchangeFieldType
import dev.mauicoder.morsekata.trainer.QsoStage
import dev.mauicoder.morsekata.trainer.QsoTrainerTab
import dev.mauicoder.morsekata.trainer.QsoTrainerUiMapper
import dev.mauicoder.morsekata.trainer.XpAward
import dev.mauicoder.morsekata.viewmodel.QsoTrainerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class QsoTrainerScreenTest {

    private lateinit var fakePreferencesRepository: FakeUserPreferencesRepository
    private lateinit var fakeProgressRepository: FakeTrainerProgressRepository
    private lateinit var cancellableAudioPlayer: CancellableTrackingAudioPlayer
    private lateinit var viewModel: QsoTrainerViewModel

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeUserPreferencesRepository()
        fakeProgressRepository = FakeTrainerProgressRepository()
        cancellableAudioPlayer = CancellableTrackingAudioPlayer()
        viewModel = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = cancellableAudioPlayer,
            clock = FixedTestClock(LocalDate.of(2026, 11, 26)),
            random = Random(12345)
        )
    }

    @Test
    fun `complete fill request interaction lifecycle in Stage 1 verifies audio cancellation, replay counter, disabled chips, and canonical transcript`() {
        // 1. Initial State: Screen starts on PRACTICE tab
        val initialUi = QsoTrainerUiMapper.map(viewModel.uiState.value)
        assertEquals(QsoTrainerTab.PRACTICE, initialUi.selectedTab)

        // Attempting fill request on non-FULL_QSO tab is a no-op
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)
        assertEquals(0, cancellableAudioPlayer.playHistory.size)
        assertEquals(0, viewModel.uiState.value.qsoStageReplayCount)

        // 2. Select Full QSO tab
        viewModel.selectTab(QsoTrainerTab.FULL_QSO)
        val stage1State = viewModel.uiState.value
        val stage1Ui = QsoTrainerUiMapper.map(stage1State)

        assertEquals(QsoTrainerTab.FULL_QSO, stage1Ui.selectedTab)
        assertEquals(QsoStage.CQ_CALL, stage1Ui.fullQso.stage)
        assertEquals("Stage 1/3: Calling CQ", stage1Ui.fullQso.stageTitle)
        assertEquals(0, stage1State.qsoStageReplayCount)
        assertEquals(0, stage1Ui.fullQso.completedFullQsoCount)

        // Logbook should show placeholder for unsubmitted Stage 1
        assertEquals("???", stage1Ui.fullQso.logbook.callsign)
        assertEquals("—", stage1Ui.fullQso.logbook.rst)

        // Transcript card must be hidden before stage submission
        assertEquals("", stage1Ui.fullQso.revealedTransmitText)
        assertEquals("", stage1Ui.fullQso.revealedMorsePattern)

        // Fill chip should be present and enabled
        assertEquals(1, stage1Ui.fullQso.fields.size)
        val callsignField = stage1Ui.fullQso.fields.first()
        assertEquals(QsoExchangeFieldType.CALLSIGN, callsignField.fieldType)
        assertEquals("Station Callsign", callsignField.label)
        assertEquals("AGN PSE CALL", callsignField.fillActionLabel)
        assertTrue("Fill action must be enabled prior to submission", callsignField.isFillActionEnabled)
        assertFalse("Cannot submit stage until callsign is selected", stage1Ui.fullQso.canSubmitStage)

        // 3. Start over playback via Replay Over button
        viewModel.replayQsoStageAudio()
        val session = viewModel.uiState.value.fullQsoSession
        val stage1TransmitText = session.stageChallenge(QsoStage.CQ_CALL).transmitText

        assertEquals(1, cancellableAudioPlayer.playHistory.size)
        assertEquals(stage1TransmitText, cancellableAudioPlayer.currentlyPlayingText)
        assertTrue(cancellableAudioPlayer.isAudioPlaying)
        assertEquals(1, viewModel.uiState.value.qsoStageReplayCount)

        // 4. User misses callsign and clicks "AGN PSE CALL" fill chip:
        // Prior over audio is cancelled immediately and fill audio plays
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)

        val expectedFillText = session.fillTransmissionTextFor(QsoExchangeFieldType.CALLSIGN)
        assertEquals("CALL ${session.dxCallsign} ${session.dxCallsign} BK", expectedFillText)
        assertEquals(2, cancellableAudioPlayer.playHistory.size)
        assertEquals(1, cancellableAudioPlayer.cancelledPriorPlaybackCount)
        assertEquals(expectedFillText, cancellableAudioPlayer.currentlyPlayingText)

        // Replay counter incremented to 2 and reflected in UI state
        assertEquals(2, viewModel.uiState.value.qsoStageReplayCount)

        // 5. User requests fill a second time (unlimited fills allowed)
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)
        assertEquals(3, cancellableAudioPlayer.playHistory.size)
        assertEquals(2, cancellableAudioPlayer.cancelledPriorPlaybackCount)
        assertEquals(3, viewModel.uiState.value.qsoStageReplayCount)

        // 6. User selects correct callsign
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.CALLSIGN, session.dxCallsign)
        val readyState = viewModel.uiState.value
        val readyUi = QsoTrainerUiMapper.map(readyState)
        assertTrue("Submit must be enabled when option is selected", readyUi.fullQso.canSubmitStage)
        assertTrue("Fill chip must remain enabled before submission", readyUi.fullQso.fields.first().isFillActionEnabled)

        // 7. Submit stage:
        viewModel.submitQsoStage()

        val submittedState = viewModel.uiState.value
        val submittedUi = QsoTrainerUiMapper.map(submittedState)

        assertTrue(submittedState.isQsoStageSubmitted)
        assertFalse(submittedUi.fullQso.areFieldOptionsEnabled)
        assertFalse("Submit button should be disabled after submission", submittedUi.fullQso.canSubmitStage)
        assertTrue("Next stage button should be enabled after submission", submittedUi.fullQso.isNextStageEnabled)

        // Fill chip MUST be disabled upon stage submission
        assertFalse("Fill chip must be disabled once stage is submitted", submittedUi.fullQso.fields.first().isFillActionEnabled)

        // Post-submission fill requests must be no-ops
        val playCallsBeforeNoOp = cancellableAudioPlayer.playHistory.size
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)
        assertEquals(playCallsBeforeNoOp, cancellableAudioPlayer.playHistory.size)
        assertEquals(3, viewModel.uiState.value.qsoStageReplayCount)

        // Scoring: clean copy (1/1) but FirstCopy bonus is forfeited due to fill requests
        val award = submittedState.qsoStageXpAward
        assertNotNull(award)
        assertEquals(5, award!!.baseXp)
        assertEquals(0, award.streakBonusXp)
        assertEquals(0, award.firstCopyBonusXp)
        assertEquals(5, award.totalXpEarned)
        assertEquals(1, submittedState.streak)
        assertEquals(5, submittedState.totalXp)

        // Transcript card displays the CANONICAL transmission text (not the fill text!)
        assertEquals(stage1TransmitText, submittedUi.fullQso.revealedTransmitText)
        assertEquals(QsoCatalog.formatMorsePattern(stage1TransmitText), submittedUi.fullQso.revealedMorsePattern)
        assertFalse(submittedUi.fullQso.revealedTransmitText.contains("BK"))

        // Logbook summary updated
        assertEquals(session.dxCallsign, submittedUi.fullQso.logbook.callsign)
        assertEquals("—", submittedUi.fullQso.logbook.rst)
    }

    @Test
    fun `multi-field fill requests in Stage 2 cancel prior audio, increment replay count, and disable upon submission`() {
        viewModel.selectTab(QsoTrainerTab.FULL_QSO)
        val session = viewModel.uiState.value.fullQsoSession

        // Submit Stage 1 cleanly on first listen (without fills)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.CALLSIGN, session.dxCallsign)
        viewModel.submitQsoStage()
        assertEquals(XpAward(baseXp = 5, streakBonusXp = 0, firstCopyBonusXp = 5), viewModel.uiState.value.qsoStageXpAward)
        assertEquals(10, viewModel.uiState.value.totalXp)
        assertEquals(1, viewModel.uiState.value.streak)

        // Advance to Stage 2: MAIN_EXCHANGE
        viewModel.nextQsoStage()
        val stage2State = viewModel.uiState.value
        val stage2Ui = QsoTrainerUiMapper.map(stage2State)

        assertEquals(QsoStage.MAIN_EXCHANGE, stage2Ui.fullQso.stage)
        assertEquals("Stage 2/3: Main Exchange", stage2Ui.fullQso.stageTitle)
        assertEquals(0, stage2State.qsoStageReplayCount)
        assertEquals(4, stage2Ui.fullQso.fields.size)

        val stage2Fields = stage2Ui.fullQso.fields.associateBy { it.fieldType }
        assertEquals("AGN PSE RST", stage2Fields[QsoExchangeFieldType.RST]?.fillActionLabel)
        assertEquals("AGN PSE QTH", stage2Fields[QsoExchangeFieldType.QTH]?.fillActionLabel)
        assertEquals("AGN PSE NAME", stage2Fields[QsoExchangeFieldType.OP_NAME]?.fillActionLabel)
        assertEquals("AGN PSE COND", stage2Fields[QsoExchangeFieldType.CONDITION]?.fillActionLabel)

        stage2Fields.values.forEach { field ->
            assertTrue("Field ${field.fieldType} fill chip must be enabled", field.isFillActionEnabled)
        }

        // Advancing to stage automatically primed playback with stage 2 over
        val stage2TransmitText = session.stageChallenge(QsoStage.MAIN_EXCHANGE).transmitText
        assertEquals(stage2TransmitText, cancellableAudioPlayer.currentlyPlayingText)

        // Request fill for RST: cancels stage over, plays RST fill, increments replay count
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.RST)
        val expectedRstFill = "UR RST ${session.rst} ${session.rst} BK"
        assertEquals(expectedRstFill, cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(1, viewModel.uiState.value.qsoStageReplayCount)

        // Request fill for QTH: cancels RST fill, plays QTH fill, increments replay count
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.QTH)
        val expectedQthFill = "QTH ${session.qth} ${session.qth} BK"
        assertEquals(expectedQthFill, cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(2, viewModel.uiState.value.qsoStageReplayCount)

        // Request fill for OP_NAME: cancels QTH fill, plays OP_NAME fill, increments replay count
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.OP_NAME)
        val expectedNameFill = "OP ${session.opName} ${session.opName} BK"
        assertEquals(expectedNameFill, cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(3, viewModel.uiState.value.qsoStageReplayCount)

        // Select all 4 correct fields and submit
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.RST, session.rst)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.QTH, session.qth)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.OP_NAME, session.opName)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.CONDITION, session.condition)

        viewModel.submitQsoStage()

        val submittedStage2State = viewModel.uiState.value
        val submittedStage2Ui = QsoTrainerUiMapper.map(submittedStage2State)

        assertTrue(submittedStage2State.isQsoStageSubmitted)
        submittedStage2Ui.fullQso.fields.forEach { field ->
            assertFalse("Field ${field.fieldType} fill chip must be disabled post-submission", field.isFillActionEnabled)
            assertEquals(OptionHighlightState.CORRECT, field.options.first { it.text == field.correctValue }.highlightState)
        }

        // FirstCopy bonus suppressed due to fills; streak bonus active (streak was 1 -> +2 bonus)
        assertEquals(
            XpAward(baseXp = 20, streakBonusXp = 2, firstCopyBonusXp = 0),
            submittedStage2State.qsoStageXpAward
        )
        assertEquals(10 + 22, submittedStage2State.totalXp)
        assertEquals(2, submittedStage2State.streak)

        // Canonical transmission text and pattern displayed
        assertEquals(stage2TransmitText, submittedStage2Ui.fullQso.revealedTransmitText)
        assertEquals(QsoCatalog.formatMorsePattern(stage2TransmitText), submittedStage2Ui.fullQso.revealedMorsePattern)
    }

    @Test
    fun `Stage 3 fill requests for RIG, POWER, and WEATHER award full QSO completion bonus without FirstCopy bonus`() {
        viewModel.selectTab(QsoTrainerTab.FULL_QSO)
        val session = viewModel.uiState.value.fullQsoSession

        // Submit Stage 1
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.CALLSIGN, session.dxCallsign)
        viewModel.submitQsoStage()
        viewModel.nextQsoStage()

        // Submit Stage 2
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.RST, session.rst)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.QTH, session.qth)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.OP_NAME, session.opName)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.CONDITION, session.condition)
        viewModel.submitQsoStage()
        viewModel.nextQsoStage()

        // Stage 3: FINAL_SIGN_OFF
        val stage3State = viewModel.uiState.value
        val stage3Ui = QsoTrainerUiMapper.map(stage3State)

        assertEquals(QsoStage.FINAL_SIGN_OFF, stage3Ui.fullQso.stage)
        assertEquals("Stage 3/3: Technical & 73", stage3Ui.fullQso.stageTitle)
        assertEquals(3, stage3Ui.fullQso.fields.size)

        val stage3Fields = stage3Ui.fullQso.fields.associateBy { it.fieldType }
        assertEquals("AGN PSE RIG", stage3Fields[QsoExchangeFieldType.RIG]?.fillActionLabel)
        assertEquals("AGN PSE PWR", stage3Fields[QsoExchangeFieldType.POWER]?.fillActionLabel)
        assertEquals("AGN PSE WX", stage3Fields[QsoExchangeFieldType.WEATHER]?.fillActionLabel)

        // Request fills for all three fields in Stage 3
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.RIG)
        assertEquals("RIG ${session.rig} ${session.rig} BK", cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(1, viewModel.uiState.value.qsoStageReplayCount)

        viewModel.requestQsoFieldFill(QsoExchangeFieldType.POWER)
        assertEquals("PWR ${session.power} ${session.power} BK", cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(2, viewModel.uiState.value.qsoStageReplayCount)

        viewModel.requestQsoFieldFill(QsoExchangeFieldType.WEATHER)
        assertEquals("WX ${session.weather} ${session.weather} BK", cancellableAudioPlayer.currentlyPlayingText)
        assertEquals(3, viewModel.uiState.value.qsoStageReplayCount)

        // Select correct answers and submit Stage 3
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.RIG, session.rig)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.POWER, session.power)
        viewModel.selectQsoFieldOption(QsoExchangeFieldType.WEATHER, session.weather)
        viewModel.submitQsoStage()

        val submittedStage3State = viewModel.uiState.value
        val submittedStage3Ui = QsoTrainerUiMapper.map(submittedStage3State)

        assertTrue(submittedStage3State.isQsoStageSubmitted)
        assertEquals(1, submittedStage3State.completedFullQsoCount)
        assertEquals(1, submittedStage3Ui.fullQso.completedFullQsoCount)
        assertEquals(1, submittedStage3Ui.stats.completedFullQsoCount)

        // Verify XP: 15 field XP + 25 Full QSO bonus = 40 base XP, streak bonus, 0 FirstCopy bonus
        val stage3Award = submittedStage3State.qsoStageXpAward
        assertNotNull(stage3Award)
        assertEquals(40, stage3Award!!.baseXp)
        assertEquals(0, stage3Award.firstCopyBonusXp)
        assertTrue(submittedStage3Ui.fullQso.feedbackText.contains("Clean Copy! (3/3 fields)"))
        assertTrue(submittedStage3Ui.fullQso.feedbackText.contains("+25 Full QSO Logged"))

        // All 3 fill chips disabled
        submittedStage3Ui.fullQso.fields.forEach { field ->
            assertFalse(field.isFillActionEnabled)
        }

        // Canonical transmission text revealed
        val stage3TransmitText = session.stageChallenge(QsoStage.FINAL_SIGN_OFF).transmitText
        assertEquals(stage3TransmitText, submittedStage3Ui.fullQso.revealedTransmitText)

        // Advancing to next QSO resets stage to Stage 1 with new session
        viewModel.nextQsoStage()
        val nextQsoState = viewModel.uiState.value
        assertEquals(QsoStage.CQ_CALL, nextQsoState.currentQsoStage)
        assertEquals(0, nextQsoState.qsoStageReplayCount)
        assertFalse(nextQsoState.isQsoStageSubmitted)
    }

    @Test
    fun `fill requests are ignored across other tabs and post-completion`() {
        // Tab 1: Callsigns
        viewModel.selectTab(QsoTrainerTab.CALLSIGNS)
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)
        assertEquals(0, cancellableAudioPlayer.playHistory.size)
        assertEquals(0, viewModel.uiState.value.qsoStageReplayCount)

        // Tab 2: Stats
        viewModel.selectTab(QsoTrainerTab.STATS)
        viewModel.requestQsoFieldFill(QsoExchangeFieldType.CALLSIGN)
        assertEquals(0, cancellableAudioPlayer.playHistory.size)
        assertEquals(0, viewModel.uiState.value.qsoStageReplayCount)
    }

    private class CancellableTrackingAudioPlayer : MorseAudioPlayer {
        val playHistory = mutableListOf<String>()
        var currentlyPlayingText: String? = null
            private set
        var isAudioPlaying: Boolean = false
            private set
        var cancelledPriorPlaybackCount: Int = 0
            private set

        override fun play(text: String, preferences: UserPreferences) {
            if (isAudioPlaying) {
                cancelledPriorPlaybackCount++
            }
            currentlyPlayingText = text
            isAudioPlaying = true
            playHistory.add(text)
        }

        override fun stop() {
            if (isAudioPlaying) {
                cancelledPriorPlaybackCount++
            }
            isAudioPlaying = false
            currentlyPlayingText = null
        }
    }

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val _userPreferences = MutableStateFlow(UserPreferences(frequency = 700, wpm = 20, farnsworthWpm = 20))
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

    private class FakeTrainerProgressRepository : TrainerProgressRepository {
        private val _trainerProgress = MutableStateFlow(TrainerProgress())
        override val trainerProgress: StateFlow<TrainerProgress> = _trainerProgress.asStateFlow()

        override suspend fun updateProgress(transform: (TrainerProgress) -> TrainerProgress) {
            _trainerProgress.value = transform(_trainerProgress.value)
        }
    }

    private class FixedTestClock(date: LocalDate) : Clock() {
        private val zone: ZoneId = ZoneId.of("UTC")
        private val instantVal: Instant = date.atStartOfDay(zone).toInstant()

        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instantVal
    }
}
