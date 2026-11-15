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
            streak = 2,
            bestStreak = 5,
            totalXp = 150,
            operatorRank = OperatorRank.NOVICE,
            rankProgressPercent = 25,
            dailyStreak = 3,
            dailyAnsweredCount = 2,
            dailyGoalTarget = 5,
            masteredInCategoryCount = 4,
            totalInCategoryCount = 10
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Rank: Novice", uiModel.rankText)
        assertEquals("XP: 150 / 300", uiModel.xpText)
        assertEquals(25, uiModel.rankProgressPercent)
        assertEquals("Score: 2 / 3", uiModel.scoreText)
        assertEquals("Streak: 2 (Best: 5)", uiModel.streakText)
        assertEquals("Daily: 3d (2/5)", uiModel.dailyStreakText)
        assertEquals("Mastered: 4 / 10", uiModel.masteryText)
        assertTrue(uiModel.areOptionsEnabled)
        assertFalse(uiModel.isNextEnabled)
        assertEquals("", uiModel.feedbackText)
        assertEquals("", uiModel.morsePatternText)
        assertEquals("", uiModel.meaningText)
        assertEquals(4, uiModel.optionStates.size)
        uiModel.optionStates.forEachIndexed { index, optionState ->
            assertEquals(sampleOptions[index], optionState.text)
            assertEquals(OptionHighlightState.DEFAULT, optionState.highlightState)
        }
    }

    @Test
    fun `max rank xp text formatting`() {
        val state = QsoTrainerUiState(
            currentEntry = sampleEntry,
            options = sampleOptions,
            totalXp = 1800,
            operatorRank = OperatorRank.AMATEUR_EXTRA,
            rankProgressPercent = 100
        )
        val uiModel = QsoTrainerUiMapper.map(state)
        assertEquals("Rank: Amateur Extra", uiModel.rankText)
        assertEquals("XP: 1800 (Max Rank)", uiModel.xpText)
        assertEquals(100, uiModel.rankProgressPercent)
    }

    @Test
    fun `correctly answered state maps feedback with breakdown rank up and daily goal`() {
        val state = QsoTrainerUiState(
            selectedCategory = QsoCategory.SINGLE_CODES,
            currentEntry = sampleEntry,
            options = sampleOptions,
            selectedOption = "QTH",
            isCorrect = true,
            revealedMeaning = "My location is...",
            revealedMorsePattern = "--.- - ....",
            correctCount = 3,
            totalAnswered = 4,
            streak = 3,
            bestStreak = 5,
            totalXp = 320,
            operatorRank = OperatorRank.TECHNICIAN,
            rankProgressPercent = 10,
            dailyStreak = 4,
            dailyAnsweredCount = 5,
            dailyGoalTarget = 5,
            lastXpAward = XpAward(baseXp = 10, streakBonusXp = 4, firstCopyBonusXp = 5),
            rankedUpTo = OperatorRank.TECHNICIAN,
            dailyGoalJustCompleted = true
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Score: 3 / 4", uiModel.scoreText)
        assertEquals("Streak: 3 (Best: 5)", uiModel.streakText)
        assertEquals("Daily: 4d (5/5)", uiModel.dailyStreakText)
        assertFalse(uiModel.areOptionsEnabled)
        assertTrue(uiModel.isNextEnabled)
        assertEquals("Correct! +19 XP (10 Base + 4 Streak + 5 First-Copy) • Rank Up: Technician! • Daily Goal Complete!", uiModel.feedbackText)
        assertEquals("--.- - ....", uiModel.morsePatternText)
        assertEquals("QTH — My location is...", uiModel.meaningText)
        assertEquals(OptionHighlightState.CORRECT, uiModel.optionStates[1].highlightState)
    }

    @Test
    fun `incorrectly answered state formats feedback and reveals pattern`() {
        val state = QsoTrainerUiState(
            selectedCategory = QsoCategory.SINGLE_CODES,
            currentEntry = sampleEntry,
            options = sampleOptions,
            selectedOption = "QRZ",
            isCorrect = false,
            revealedMeaning = "My location is...",
            revealedMorsePattern = "--.- - ....",
            correctCount = 2,
            totalAnswered = 4,
            streak = 0,
            bestStreak = 5
        )

        val uiModel = QsoTrainerUiMapper.map(state)

        assertEquals("Incorrect — Correct answer: QTH", uiModel.feedbackText)
        assertEquals("--.- - ....", uiModel.morsePatternText)
        assertEquals("QTH — My location is...", uiModel.meaningText)
        assertEquals(OptionHighlightState.INCORRECT, uiModel.optionStates[2].highlightState)
        assertEquals(OptionHighlightState.CORRECT, uiModel.optionStates[1].highlightState)
    }
}

