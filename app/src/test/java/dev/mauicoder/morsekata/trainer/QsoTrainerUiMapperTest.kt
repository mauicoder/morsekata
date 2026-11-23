package dev.mauicoder.morsekata.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun `mapStats builds trailing 7 day streak strip with weekday labels statuses and today fallback`() {
        // 2026-11-15 is a Sunday; trailing 7 days are Mon (Nov 9) through Sun (Nov 15)
        val today = LocalDate.of(2026, 11, 15).toEpochDay()
        val state = QsoTrainerUiState(
            selectedTab = QsoTrainerTab.STATS,
            currentEntry = sampleEntry,
            options = sampleOptions,
            dailyStreak = 3,
            dailyAnsweredCount = 4,
            dailyGoalTarget = 5,
            todayEpochDay = today,
            dailyHistory = mapOf(
                (today - 6L) to 5, // Mon: GOAL_MET
                (today - 5L) to 0, // Tue: MISSED
                (today - 4L) to 2, // Wed: PRACTICED
                (today - 2L) to 7, // Fri: GOAL_MET
                (today - 1L) to 5  // Sat: GOAL_MET
                // Sun (today) omitted from dailyHistory to test fallback to dailyAnsweredCount = 4
            )
        )

        val uiModel = QsoTrainerUiMapper.map(state)
        assertEquals(QsoTrainerTab.STATS, uiModel.selectedTab)
        val stats = uiModel.stats

        assertEquals(3, stats.dailyStreak)
        assertEquals(4, stats.todayAnsweredCount)
        assertEquals(5, stats.dailyGoalTarget)
        assertEquals(0.8f, stats.todayGoalProgressFraction, 0.0001f)

        assertEquals(7, stats.trailingSevenDays.size)
        val expectedLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val expectedCounts = listOf(5, 0, 2, 0, 7, 5, 4)
        val expectedStatuses = listOf(
            DayActivityStatus.GOAL_MET,
            DayActivityStatus.MISSED,
            DayActivityStatus.PRACTICED,
            DayActivityStatus.MISSED,
            DayActivityStatus.GOAL_MET,
            DayActivityStatus.GOAL_MET,
            DayActivityStatus.PRACTICED
        )

        stats.trailingSevenDays.forEachIndexed { index, cell ->
            assertEquals(today - 6L + index, cell.epochDay)
            assertEquals(expectedLabels[index], cell.dayLabel)
            assertEquals(expectedCounts[index], cell.answeredCount)
            assertEquals(expectedStatuses[index], cell.status)
            assertEquals(index == 6, cell.isToday)
        }
    }

    @Test
    fun `mapStats computes rank XP countdown and lifetime accuracy including zero-attempt edge case`() {
        val zeroState = QsoTrainerUiState(
            currentEntry = sampleEntry,
            options = sampleOptions,
            totalXp = 0,
            operatorRank = OperatorRank.SWL,
            rankProgressPercent = 0,
            streak = 0,
            bestStreak = 0,
            itemMastery = emptyMap()
        )
        val zeroStats = QsoTrainerUiMapper.mapStats(zeroState)
        assertEquals("100 XP to Novice", zeroStats.xpToNextRankText)
        assertEquals(0f, zeroStats.rankProgressFraction, 0.0001f)
        assertEquals(0, zeroStats.lifetimeAnswered)
        assertEquals(0, zeroStats.lifetimeCorrect)
        assertEquals(0, zeroStats.lifetimeAccuracyPercent)

        val generalState = QsoTrainerUiState(
            currentEntry = sampleEntry,
            options = sampleOptions,
            totalXp = 950,
            operatorRank = OperatorRank.GENERAL,
            rankProgressPercent = OperatorRank.progressPercent(950),
            streak = 4,
            bestStreak = 11,
            itemMastery = mapOf(
                "QTH" to ItemMastery(attempts = 10, correct = 8, consecutiveCorrect = 3),
                "QSL" to ItemMastery(attempts = 6, correct = 3, consecutiveCorrect = 1)
            )
        )
        val generalStats = QsoTrainerUiMapper.mapStats(generalState)
        assertEquals("550 XP to Amateur Extra", generalStats.xpToNextRankText)
        assertEquals(4, generalStats.currentAnswerStreak)
        assertEquals(11, generalStats.bestAnswerStreak)
        assertEquals(16, generalStats.lifetimeAnswered)
        assertEquals(11, generalStats.lifetimeCorrect)
        assertEquals(68, generalStats.lifetimeAccuracyPercent) // (11 * 100) / 16 = 68

        val extraState = generalState.copy(
            totalXp = 1800,
            operatorRank = OperatorRank.AMATEUR_EXTRA,
            rankProgressPercent = 100
        )
        val extraStats = QsoTrainerUiMapper.mapStats(extraState)
        assertEquals("Highest License Class Achieved", extraStats.xpToNextRankText)
        assertEquals(1.0f, extraStats.rankProgressFraction, 0.0001f)
    }

    @Test
    fun `mapStats computes category mastery breakdown recommended category and top 5 sorted weak items`() {
        // Master 3 single codes (3/29 = ~10.3%) and 1 full phrase (1/16 = 6.25%)
        // plus 6 unmastered attempted items to verify sorting & top-5 truncation
        val mastery = mapOf(
            // Mastered Single Codes (excluded from weakItems)
            "QTH" to ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3),
            "QSL" to ItemMastery(attempts = 4, correct = 3, consecutiveCorrect = 3),
            "QRZ" to ItemMastery(attempts = 5, correct = 4, consecutiveCorrect = 4),
            // Mastered Full Phrase (excluded from weakItems)
            "CQ CQ CQ DE" to ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3),
            // Unattempted item (attempts = 0 -> excluded from weakItems)
            "88" to ItemMastery(attempts = 0, correct = 0, consecutiveCorrect = 0),
            // 6 Attempted & Unmastered items:
            // 1. consecutiveCorrect = 0, accuracy = 0% (0/4), attempts = 4
            "QRM" to ItemMastery(attempts = 4, correct = 0, consecutiveCorrect = 0),
            // 2. consecutiveCorrect = 0, accuracy = 25% (1/4), attempts = 4
            "QRN" to ItemMastery(attempts = 4, correct = 1, consecutiveCorrect = 0),
            // 3. consecutiveCorrect = 0, accuracy = 25% (2/8), attempts = 8 (tie-breaker: higher attempts comes first!)
            "QSY" to ItemMastery(attempts = 8, correct = 2, consecutiveCorrect = 0),
            // 4. consecutiveCorrect = 1, accuracy = 50% (1/2), attempts = 2
            "QRP" to ItemMastery(attempts = 2, correct = 1, consecutiveCorrect = 1),
            // 5. consecutiveCorrect = 2, accuracy = 66% (2/3), attempts = 3
            "QRT" to ItemMastery(attempts = 3, correct = 2, consecutiveCorrect = 2),
            // 6. consecutiveCorrect = 2, accuracy = 80% (4/5), attempts = 5 (6th weakest -> truncated)
            "QRV" to ItemMastery(attempts = 5, correct = 4, consecutiveCorrect = 2)
        )

        val state = QsoTrainerUiState(
            currentEntry = sampleEntry,
            options = sampleOptions,
            itemMastery = mastery
        )

        val stats = QsoTrainerUiMapper.mapStats(state)

        assertEquals(QsoCategory.SINGLE_CODES, stats.singleCodesMastery.category)
        assertEquals("Single Codes", stats.singleCodesMastery.title)
        assertEquals(3, stats.singleCodesMastery.masteredCount)
        assertEquals(29, stats.singleCodesMastery.totalCount)
        assertEquals(3f / 29f, stats.singleCodesMastery.progressFraction, 0.0001f)

        assertEquals(QsoCategory.FULL_PHRASES, stats.fullPhrasesMastery.category)
        assertEquals("Full Phrases", stats.fullPhrasesMastery.title)
        assertEquals(1, stats.fullPhrasesMastery.masteredCount)
        assertEquals(16, stats.fullPhrasesMastery.totalCount)
        assertEquals(1f / 16f, stats.fullPhrasesMastery.progressFraction, 0.0001f)

        // 1/16 (0.0625) < 3/29 (0.1034) -> recommends FULL_PHRASES
        assertEquals(QsoCategory.FULL_PHRASES, stats.recommendedCategory)

        // When ratios are equal (e.g., 0/29 and 0/16), recommends SINGLE_CODES
        val emptyStats = QsoTrainerUiMapper.mapStats(state.copy(itemMastery = emptyMap()))
        assertEquals(QsoCategory.SINGLE_CODES, emptyStats.recommendedCategory)

        // Verify weakItems sorting and top-5 limit:
        // Expected order: QRM (cc=0, acc=0%, att=4), QSY (cc=0, acc=25%, att=8), QRN (cc=0, acc=25%, att=4), QRP (cc=1, acc=50%, att=2), QRT (cc=2, acc=66%, att=3)
        assertEquals(5, stats.weakItems.size)
        assertEquals(listOf("QRM", "QSY", "QRN", "QRP", "QRT"), stats.weakItems.map { it.codeOrPhrase })

        val firstWeak = stats.weakItems.first()
        assertEquals("QRM", firstWeak.codeOrPhrase)
        assertEquals("Man-made interference", firstWeak.meaning)
        assertEquals(QsoCatalog.formatMorsePattern("QRM"), firstWeak.morsePattern)
        assertEquals(4, firstWeak.attempts)
        assertEquals(0, firstWeak.correct)
        assertEquals(0, firstWeak.accuracyPercent)
        assertEquals(0, firstWeak.consecutiveCorrect)
    }
}

