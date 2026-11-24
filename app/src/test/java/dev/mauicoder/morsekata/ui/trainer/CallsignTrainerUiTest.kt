package dev.mauicoder.morsekata.ui.trainer

import dev.mauicoder.morsekata.trainer.CallsignDiffCalculator
import dev.mauicoder.morsekata.trainer.CallsignDrillMode
import dev.mauicoder.morsekata.trainer.CallsignSpeedMode
import dev.mauicoder.morsekata.trainer.CallsignTarget
import dev.mauicoder.morsekata.trainer.CallsignTrainerUiModel
import dev.mauicoder.morsekata.trainer.CallsignTrainerUiMapper
import dev.mauicoder.morsekata.trainer.CallsignTrainerUiState
import dev.mauicoder.morsekata.trainer.QsoTrainerTab
import dev.mauicoder.morsekata.trainer.QsoTrainerUiMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsignTrainerUiTest {

    @Test
    fun tabRow_containsAllFourTabs_withCorrectTitles() {
        assertEquals(4, QsoTrainerTab.entries.size)
        assertEquals("Code Drill", QsoTrainerUiMapper.tabTitle(QsoTrainerTab.PRACTICE))
        assertEquals("Callsigns", QsoTrainerUiMapper.tabTitle(QsoTrainerTab.CALLSIGNS))
        assertEquals("Full QSO", QsoTrainerUiMapper.tabTitle(QsoTrainerTab.FULL_QSO))
        assertEquals("Stats", QsoTrainerUiMapper.tabTitle(QsoTrainerTab.STATS))
    }

    @Test
    fun statsTab_includesCallsignMilestones() {
        val state = dev.mauicoder.morsekata.trainer.QsoTrainerUiState(
            currentEntry = dev.mauicoder.morsekata.trainer.QsoEntry("QTH", "My location is...", dev.mauicoder.morsekata.trainer.QsoEntryType.SINGLE_CODE),
            options = listOf("QTH"),
            bestCallsignWpm = 25,
            completedCallsignCount = 12
        )
        val uiModel = QsoTrainerUiMapper.map(state)
        assertEquals(25, uiModel.stats.bestCallsignWpm)
        assertEquals("25 WPM", uiModel.stats.bestCallsignWpmText)
        assertEquals(12, uiModel.stats.completedCallsignCount)
    }

    @Test
    fun idleReadyState_displaysDefaultMetricsAndDisabledSubmit() {
        val target = CallsignTarget(
            callsign = "W1AW",
            fullTransmissionText = "W1AW",
            morsePattern = ".-- .---- .- .--"
        )
        val state = CallsignTrainerUiState(
            drillMode = CallsignDrillMode.CALLSIGNS_ONLY,
            speedMode = CallsignSpeedMode.ADAPTIVE,
            currentWpm = 20,
            bestWpmReached = 22,
            currentTarget = target,
            typedInput = "",
            isSubmitted = false,
            comboStreak = 3,
            sessionAnsweredCount = 0,
            sessionCorrectCount = 0
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertEquals("Callsigns Only", model.drillModeTitle)
        assertEquals("20 WPM", model.speedText)
        assertEquals("Best: 22 WPM", model.bestWpmText)
        assertEquals("Accuracy: 100%", model.accuracyText)
        assertEquals("Streak: 3", model.streakText)
        assertEquals("Replay Audio", model.replayButtonText)
        assertFalse("Submit should be disabled when input is blank", model.isSubmitEnabled)
        assertFalse("Feedback should not be visible before submit", model.isFeedbackVisible)
        assertNull(model.earnedXpText)
        assertNull(model.expectedText)
        assertFalse(model.isNextButtonVisible)
    }

    @Test
    fun idleReadyState_withInput_enablesSubmit() {
        val state = CallsignTrainerUiState(
            typedInput = "W1AW",
            isSubmitted = false
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertTrue("Submit should be enabled when input is not blank and not submitted", model.isSubmitEnabled)
        assertFalse(model.isFeedbackVisible)
    }

    @Test
    fun replayCount_updatesReplayButtonText() {
        val state = CallsignTrainerUiState(
            preAnswerReplayCount = 2
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertEquals("Replay Audio (2)", model.replayButtonText)
    }

    @Test
    fun correctState_displaysSuccessBannerAndAutoAdvance() {
        val target = CallsignTarget(
            callsign = "DL1ABC",
            fullTransmissionText = "DL1ABC",
            morsePattern = "-.. .-.. .---- .- -... -.-."
        )
        val state = CallsignTrainerUiState(
            currentTarget = target,
            typedInput = "DL1ABC",
            isSubmitted = true,
            isCorrect = true,
            earnedXp = 15,
            comboStreak = 4,
            sessionAnsweredCount = 5,
            sessionCorrectCount = 4,
            autoAdvanceCountdownActive = true
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertTrue(model.isFeedbackVisible)
        assertTrue(model.isCorrect)
        assertEquals("Correct!", model.feedbackTitle)
        assertEquals("+15 XP", model.earnedXpText)
        assertEquals("-.. .-.. .---- .- -... -.-.", model.morsePatternText)
        assertTrue("Auto-advance should be active on correct answer", model.isAutoAdvancing)
        assertFalse("Next button should not be shown on auto-advancing correct answer", model.isNextButtonVisible)
        assertFalse("Submit should be disabled once submitted", model.isSubmitEnabled)
        assertEquals("Accuracy: 80% (4/5)", model.accuracyText)
        assertEquals("Streak: 4", model.streakText)
    }

    @Test
    fun incorrectState_displaysDiffAndExpectedAndNextButton() {
        val target = CallsignTarget(
            callsign = "DL1ABC",
            fullTransmissionText = "DL1ABC",
            morsePattern = "-.. .-.. .---- .- -... -.-."
        )
        val diff = CallsignDiffCalculator.calculate(typed = "DL1ABX", expected = "DL1ABC")
        val state = CallsignTrainerUiState(
            currentTarget = target,
            typedInput = "DL1ABX",
            isSubmitted = true,
            isCorrect = false,
            diffResult = diff,
            earnedXp = 0,
            comboStreak = 0,
            sessionAnsweredCount = 2,
            sessionCorrectCount = 1,
            autoAdvanceCountdownActive = false
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertTrue(model.isFeedbackVisible)
        assertFalse(model.isCorrect)
        assertEquals("Incorrect", model.feedbackTitle)
        assertNull(model.earnedXpText)
        assertEquals("DL1ABC", model.expectedText)
        assertEquals("-.. .-.. .---- .- -... -.-.", model.morsePatternText)
        assertFalse("Auto-advance must NOT be active on incorrect answer", model.isAutoAdvancing)
        assertTrue("Next button must be visible on incorrect answer", model.isNextButtonVisible)
        assertFalse("Submit should be disabled once submitted", model.isSubmitEnabled)
        assertEquals("Accuracy: 50% (1/2)", model.accuracyText)
        assertEquals("Streak: 0", model.streakText)
    }

    @Test
    fun contestExchangeMode_formatsTitleProperly() {
        val state = CallsignTrainerUiState(
            drillMode = CallsignDrillMode.CONTEST_EXCHANGE
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertEquals("Contest Exchange", model.drillModeTitle)
    }

    @Test
    fun accuracy_withZeroCorrect_calculatesZeroPercent() {
        val state = CallsignTrainerUiState(
            sessionAnsweredCount = 4,
            sessionCorrectCount = 0
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertEquals("Accuracy: 0% (0/4)", model.accuracyText)
    }

    @Test
    fun submitButton_withWhitespaceOnly_isDisabled() {
        val state = CallsignTrainerUiState(
            typedInput = "   ",
            isSubmitted = false
        )

        val model = CallsignTrainerUiMapper.map(state)

        assertFalse(model.isSubmitEnabled)
    }

    @Test
    fun diffSegments_arePresentInStateForRendering() {
        val diff = CallsignDiffCalculator.calculate(typed = "W1AX", expected = "W1AW")
        val state = CallsignTrainerUiState(
            typedInput = "W1AX",
            isSubmitted = true,
            isCorrect = false,
            diffResult = diff
        )

        val segments = state.diffResult!!.diffSegments
        assertEquals(4, segments.size)
        assertTrue(segments[0].isCorrect) // W
        assertTrue(segments[1].isCorrect) // 1
        assertTrue(segments[2].isCorrect) // A
        assertFalse(segments[3].isCorrect) // X (wrong letter)
    }
}
