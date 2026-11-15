package net.maui.morselab.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QsoTrainerUiMapperTest {

    private val sampleEntry = QsoEntry(
        codeOrPhrase = "QTH",
        meaning = "My location is...",
        type = QsoEntryType.SINGLE_CODE
    )

    private val sampleOptions = listOf("QSL", "QTH", "QRZ", "QRM")

    @Test
    fun `unanswered state maps score streak enabled options disabled next and default option highlights`() {
        val state = QsoTrainerUiState(
            selectedCategory = QsoCategory.SINGLE_CODES,
            currentEntry = sampleEntry,
            options = sampleOptions,
            selectedOption = null,
            isCorrect = null,
            revealedMeaning = null,
            correctCount = 2,
            totalAnswered = 3,
            streak = 2
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Score: 2 / 3", uiModel.scoreText)
        assertEquals("Streak: 2", uiModel.streakText)
        assertTrue(uiModel.areOptionsEnabled)
        assertFalse(uiModel.isNextEnabled)
        assertEquals("", uiModel.feedbackText)
        assertEquals("", uiModel.meaningText)
        assertEquals(4, uiModel.optionStates.size)
        uiModel.optionStates.forEachIndexed { index, optionState ->
            assertEquals(sampleOptions[index], optionState.text)
            assertEquals(OptionHighlightState.DEFAULT, optionState.highlightState)
        }
    }

    @Test
    fun `correctly answered state locks options enables next shows correct feedback meaning and highlights correct option`() {
        val state = QsoTrainerUiState(
            selectedCategory = QsoCategory.SINGLE_CODES,
            currentEntry = sampleEntry,
            options = sampleOptions,
            selectedOption = "QTH",
            isCorrect = true,
            revealedMeaning = "My location is...",
            correctCount = 3,
            totalAnswered = 4,
            streak = 3
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Score: 3 / 4", uiModel.scoreText)
        assertEquals("Streak: 3", uiModel.streakText)
        assertFalse(uiModel.areOptionsEnabled)
        assertTrue(uiModel.isNextEnabled)
        assertEquals("Correct!", uiModel.feedbackText)
        assertEquals("QTH — My location is...", uiModel.meaningText)
        assertEquals(OptionHighlightState.DEFAULT, uiModel.optionStates[0].highlightState)
        assertEquals(OptionHighlightState.CORRECT, uiModel.optionStates[1].highlightState)
        assertEquals(OptionHighlightState.DEFAULT, uiModel.optionStates[2].highlightState)
        assertEquals(OptionHighlightState.DEFAULT, uiModel.optionStates[3].highlightState)
    }

    @Test
    fun `incorrectly answered state highlights selected option as incorrect and target option as correct`() {
        val state = QsoTrainerUiState(
            selectedCategory = QsoCategory.SINGLE_CODES,
            currentEntry = sampleEntry,
            options = sampleOptions,
            selectedOption = "QRZ",
            isCorrect = false,
            revealedMeaning = "My location is...",
            correctCount = 2,
            totalAnswered = 4,
            streak = 0
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Score: 2 / 4", uiModel.scoreText)
        assertEquals("Streak: 0", uiModel.streakText)
        assertFalse(uiModel.areOptionsEnabled)
        assertTrue(uiModel.isNextEnabled)
        assertEquals("Incorrect — Correct answer: QTH", uiModel.feedbackText)
        assertEquals("QTH — My location is...", uiModel.meaningText)
        assertEquals(OptionHighlightState.DEFAULT, uiModel.optionStates[0].highlightState)
        assertEquals(OptionHighlightState.CORRECT, uiModel.optionStates[1].highlightState)
        assertEquals(OptionHighlightState.INCORRECT, uiModel.optionStates[2].highlightState)
        assertEquals(OptionHighlightState.DEFAULT, uiModel.optionStates[3].highlightState)
    }
}
