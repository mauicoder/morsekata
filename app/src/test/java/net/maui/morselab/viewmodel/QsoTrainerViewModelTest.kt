package net.maui.morselab.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import net.maui.morselab.data.TrainerProgress
import net.maui.morselab.data.TrainerProgressRepository
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import net.maui.morselab.trainer.ItemMastery
import net.maui.morselab.trainer.OperatorRank
import net.maui.morselab.trainer.QsoCatalog
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoEntryType
import net.maui.morselab.trainer.QsoTrainerTab
import net.maui.morselab.trainer.QsoTrainerUiState
import net.maui.morselab.trainer.XpAward
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

class QsoTrainerViewModelTest {

    private lateinit var fakePreferencesRepository: FakeUserPreferencesRepository
    private lateinit var fakeProgressRepository: FakeTrainerProgressRepository
    private lateinit var fakeAudioPlayer: FakeMorseAudioPlayer
    private lateinit var mutableClock: MutableClock
    private lateinit var viewModel: QsoTrainerViewModel

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeUserPreferencesRepository()
        fakeProgressRepository = FakeTrainerProgressRepository()
        fakeAudioPlayer = FakeMorseAudioPlayer()
        mutableClock = MutableClock(LocalDate.of(2026, 11, 15))
        viewModel = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            trainerProgressRepository = fakeProgressRepository,
            morseAudioPlayer = fakeAudioPlayer,
            clock = mutableClock,
            random = Random(42)
        )
    }

    @Test
    fun `category selection filters prompts resets session score stops audio and preserves active combo streak`() {
        // Build up score and streak of 2 first
        repeat(2) {
            val correct = viewModel.uiState.value.currentEntry.codeOrPhrase
            viewModel.selectOption(correct)
            if (it == 0) viewModel.nextQuestion()
        }
        assertEquals(2, viewModel.uiState.value.correctCount)
        assertEquals(2, viewModel.uiState.value.totalAnswered)
        assertEquals(2, viewModel.uiState.value.streak)
        assertEquals(2, viewModel.uiState.value.bestStreak)

        val stopCountBeforeSwitch = fakeAudioPlayer.stopCallCount

        // Switch to FULL_PHRASES
        viewModel.selectCategory(QsoCategory.FULL_PHRASES)
        val fullPhraseState = viewModel.uiState.value
        assertEquals(stopCountBeforeSwitch + 1, fakeAudioPlayer.stopCallCount)
        assertEquals(QsoCategory.FULL_PHRASES, fullPhraseState.selectedCategory)
        assertEquals(QsoEntryType.FULL_PHRASE, fullPhraseState.currentEntry.type)
        assertTrue(QsoCatalog.entriesForCategory(QsoCategory.FULL_PHRASES).contains(fullPhraseState.currentEntry))
        assertEquals(QsoCatalog.entriesForCategory(QsoCategory.FULL_PHRASES).size, fullPhraseState.totalInCategoryCount)
        assertResetSessionAndRoundStatePreservingStreak(fullPhraseState, expectedStreak = 2, expectedBestStreak = 2)

        // Verify multiple draws stay in FULL_PHRASES
        repeat(15) {
            viewModel.nextQuestion()
            assertEquals(QsoEntryType.FULL_PHRASE, viewModel.uiState.value.currentEntry.type)
        }

        // Answer to increment streak to 3, then switch to SINGLE_CODES
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertEquals(1, viewModel.uiState.value.correctCount)
        assertEquals(3, viewModel.uiState.value.streak)
        assertEquals(3, viewModel.uiState.value.bestStreak)

        viewModel.selectCategory(QsoCategory.SINGLE_CODES)
        val singleCodeState = viewModel.uiState.value
        assertEquals(QsoCategory.SINGLE_CODES, singleCodeState.selectedCategory)
        assertEquals(QsoEntryType.SINGLE_CODE, singleCodeState.currentEntry.type)
        assertEquals(QsoCatalog.entriesForCategory(QsoCategory.SINGLE_CODES).size, singleCodeState.totalInCategoryCount)
        assertResetSessionAndRoundStatePreservingStreak(singleCodeState, expectedStreak = 3, expectedBestStreak = 3)

        // Switch to MIXED
        viewModel.selectCategory(QsoCategory.MIXED)
        val mixedState = viewModel.uiState.value
        assertEquals(QsoCategory.MIXED, mixedState.selectedCategory)
        assertEquals(QsoCatalog.entriesForCategory(QsoCategory.MIXED).size, mixedState.totalInCategoryCount)
        assertResetSessionAndRoundStatePreservingStreak(mixedState, expectedStreak = 3, expectedBestStreak = 3)

        val seenTypes = mutableSetOf(mixedState.currentEntry.type)
        repeat(30) {
            viewModel.nextQuestion()
            seenTypes.add(viewModel.uiState.value.currentEntry.type)
        }
        assertTrue(
            "MIXED category should draw both SINGLE_CODE and FULL_PHRASE entries",
            seenTypes.containsAll(listOf(QsoEntryType.SINGLE_CODE, QsoEntryType.FULL_PHRASE))
        )
    }

    private fun assertResetSessionAndRoundStatePreservingStreak(
        state: QsoTrainerUiState,
        expectedStreak: Int,
        expectedBestStreak: Int
    ) {
        assertEquals(0, state.correctCount)
        assertEquals(0, state.totalAnswered)
        assertEquals(expectedStreak, state.streak)
        assertEquals(expectedBestStreak, state.bestStreak)
        assertNull(state.selectedOption)
        assertNull(state.isCorrect)
        assertNull(state.revealedMeaning)
        assertNull(state.revealedMorsePattern)
        assertEquals(0, state.replayCount)
        assertNull(state.lastXpAward)
        assertNull(state.rankedUpTo)
        assertFalse(state.dailyGoalJustCompleted)
        assertEquals(4, state.options.size)
    }

    @Test
    fun `every generated question exposes 4 unique options with target and 3 distractors of matching structural type`() {
        val entriesByText = QsoCatalog.entries.associateBy { it.codeOrPhrase }

        fun assertValidOptions() {
            val state = viewModel.uiState.value
            val target = state.currentEntry
            assertEquals(4, state.options.size)
            assertEquals(4, state.options.toSet().size)
            assertTrue(state.options.contains(target.codeOrPhrase))
            state.options.forEach { optionText ->
                val optionEntry = entriesByText[optionText]
                assertNotNull("Option '$optionText' must exist in catalog", optionEntry)
                assertEquals(
                    "Option '$optionText' must match target structural type ${target.type}",
                    target.type,
                    optionEntry!!.type
                )
            }
        }

        assertValidOptions()

        for (category in QsoCategory.entries) {
            viewModel.selectCategory(category)
            assertValidOptions()
            repeat(20) {
                viewModel.nextQuestion()
                assertValidOptions()
            }
        }
    }

    @Test
    fun `selecting correct option awards Base Streak and FirstCopy XP reveals meaning and Morse pattern and locks round`() {
        val initialState = viewModel.uiState.value
        assertNull(initialState.selectedOption)
        assertNull(initialState.isCorrect)
        assertNull(initialState.revealedMeaning)
        assertNull(initialState.revealedMorsePattern)
        assertEquals(0, initialState.totalXp)

        val correctOption = initialState.currentEntry.codeOrPhrase
        val distractorOption = initialState.options.first { it != correctOption }

        viewModel.selectOption(correctOption)

        val answeredState = viewModel.uiState.value
        assertEquals(correctOption, answeredState.selectedOption)
        assertEquals(true, answeredState.isCorrect)
        assertEquals(initialState.currentEntry.meaning, answeredState.revealedMeaning)
        assertEquals(
            QsoCatalog.formatMorsePattern(correctOption),
            answeredState.revealedMorsePattern
        )
        assertEquals(1, answeredState.correctCount)
        assertEquals(1, answeredState.totalAnswered)
        assertEquals(1, answeredState.streak)
        assertEquals(1, answeredState.bestStreak)
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 0, firstCopyBonusXp = 5), answeredState.lastXpAward)
        assertEquals(15, answeredState.totalXp)
        assertEquals(15, fakeProgressRepository.trainerProgress.value.totalXp)

        // Attempting to select another option while locked must be a no-op
        viewModel.selectOption(distractorOption)
        assertEquals(correctOption, viewModel.uiState.value.selectedOption)
        assertEquals(15, viewModel.uiState.value.totalXp)
    }

    @Test
    fun `replaying before answering forfeits FirstCopy bonus while replaying after answering does not alter score`() {
        // Question 1: replay twice BEFORE answering -> forfeits +5 First-Copy bonus
        viewModel.replayAudio()
        viewModel.replayAudio()
        assertEquals(2, viewModel.uiState.value.replayCount)

        val firstTarget = viewModel.uiState.value.currentEntry.codeOrPhrase
        viewModel.selectOption(firstTarget)

        val stateAfterQ1 = viewModel.uiState.value
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 0, firstCopyBonusXp = 0), stateAfterQ1.lastXpAward)
        assertEquals(10, stateAfterQ1.totalXp)

        // Replay AFTER answering -> replayCount and XP remain unchanged
        viewModel.replayAudio()
        assertEquals(2, viewModel.uiState.value.replayCount)
        assertEquals(10, viewModel.uiState.value.totalXp)

        // Question 2: advance, answer on first listen -> gets +10 Base + 2 Streak + 5 First-Copy = 17 XP
        viewModel.nextQuestion()
        assertEquals(0, viewModel.uiState.value.replayCount)
        val secondTarget = viewModel.uiState.value.currentEntry.codeOrPhrase
        viewModel.selectOption(secondTarget)

        val stateAfterQ2 = viewModel.uiState.value
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 2, firstCopyBonusXp = 5), stateAfterQ2.lastXpAward)
        assertEquals(27, stateAfterQ2.totalXp)
    }

    @Test
    fun `selecting incorrect option awards 0 XP reveals meaning and Morse pattern resets streak to 0 and preserves bestStreak`() {
        repeat(2) {
            val correct = viewModel.uiState.value.currentEntry.codeOrPhrase
            viewModel.selectOption(correct)
            viewModel.nextQuestion()
        }
        assertEquals(2, viewModel.uiState.value.streak)
        assertEquals(2, viewModel.uiState.value.bestStreak)
        val xpBeforeWrong = viewModel.uiState.value.totalXp

        val currentState = viewModel.uiState.value
        val wrongOption = currentState.options.first { it != currentState.currentEntry.codeOrPhrase }
        viewModel.selectOption(wrongOption)

        val answeredState = viewModel.uiState.value
        assertEquals(wrongOption, answeredState.selectedOption)
        assertEquals(false, answeredState.isCorrect)
        assertEquals(currentState.currentEntry.meaning, answeredState.revealedMeaning)
        assertEquals(
            QsoCatalog.formatMorsePattern(currentState.currentEntry.codeOrPhrase),
            answeredState.revealedMorsePattern
        )
        assertEquals(XpAward(0, 0, 0), answeredState.lastXpAward)
        assertEquals(xpBeforeWrong, answeredState.totalXp)
        assertEquals(0, answeredState.streak)
        assertEquals(2, answeredState.bestStreak)
        assertEquals(0, fakeProgressRepository.trainerProgress.value.currentAnswerStreak)
        assertEquals(2, fakeProgressRepository.trainerProgress.value.bestAnswerStreak)
    }

    @Test
    fun `operator rank progresses across thresholds and emits rankedUpTo only on rank transition`() = runBlocking {
        // Seed progress at 90 XP (SWL, 10 XP away from Novice at 100 XP)
        fakeProgressRepository.updateProgress { it.copy(totalXp = 90) }
        assertEquals(OperatorRank.SWL, viewModel.uiState.value.operatorRank)
        assertEquals(90, viewModel.uiState.value.rankProgressPercent)

        // Answer correctly on first copy (+15 XP -> 105 XP -> Novice!)
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        val noviceState = viewModel.uiState.value
        assertEquals(105, noviceState.totalXp)
        assertEquals(OperatorRank.NOVICE, noviceState.operatorRank)
        assertEquals(OperatorRank.NOVICE, noviceState.rankedUpTo)
        assertEquals(2, noviceState.rankProgressPercent) // (105 - 100) * 100 / 200 = 2

        // Next question within Novice rank -> rankedUpTo is null
        viewModel.nextQuestion()
        assertNull(viewModel.uiState.value.rankedUpTo)
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertNull(viewModel.uiState.value.rankedUpTo)
        assertEquals(OperatorRank.NOVICE, viewModel.uiState.value.operatorRank)
    }

    @Test
    fun `daily streak increments on 5th question of day advances across consecutive days resets after missed day and resists backward clock shift`() {
        // Day 1 (2026-11-15): Answer 4 questions -> dailyStreak still 0
        repeat(4) {
            viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
            assertFalse(viewModel.uiState.value.dailyGoalJustCompleted)
            assertEquals(0, viewModel.uiState.value.dailyStreak)
            assertEquals(it + 1, viewModel.uiState.value.dailyAnsweredCount)
            viewModel.nextQuestion()
        }

        // 5th question on Day 1 -> completes daily goal!
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertTrue(viewModel.uiState.value.dailyGoalJustCompleted)
        assertEquals(1, viewModel.uiState.value.dailyStreak)
        assertEquals(5, viewModel.uiState.value.dailyAnsweredCount)

        // 6th question on Day 1 -> does not double-increment dailyStreak
        viewModel.nextQuestion()
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertFalse(viewModel.uiState.value.dailyGoalJustCompleted)
        assertEquals(1, viewModel.uiState.value.dailyStreak)
        assertEquals(6, viewModel.uiState.value.dailyAnsweredCount)

        // Day 2 (2026-11-16, consecutive day): Answer 5 questions -> dailyStreak becomes 2
        mutableClock.setLocalDate(LocalDate.of(2026, 11, 16))
        repeat(5) { step ->
            viewModel.nextQuestion()
            viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
            if (step < 4) {
                assertEquals(1, viewModel.uiState.value.dailyStreak)
                assertFalse(viewModel.uiState.value.dailyGoalJustCompleted)
            } else {
                assertEquals(2, viewModel.uiState.value.dailyStreak)
                assertTrue(viewModel.uiState.value.dailyGoalJustCompleted)
            }
        }

        // Backward clock shift to 2026-11-15 (tampering) -> clamps to 2026-11-16 without resetting or double counting
        mutableClock.setLocalDate(LocalDate.of(2026, 11, 15))
        viewModel.nextQuestion()
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertEquals(2, viewModel.uiState.value.dailyStreak)
        assertEquals(6, viewModel.uiState.value.dailyAnsweredCount)
        assertFalse(viewModel.uiState.value.dailyGoalJustCompleted)

        // Skip Day 3 (2026-11-17) and jump to Day 4 (2026-11-18):
        // First question on Day 4 resets broken dailyStreak to 0; 5th question starts new streak at 1
        mutableClock.setLocalDate(LocalDate.of(2026, 11, 18))
        viewModel.nextQuestion()
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertEquals(0, viewModel.uiState.value.dailyStreak)
        assertEquals(1, viewModel.uiState.value.dailyAnsweredCount)

        repeat(4) {
            viewModel.nextQuestion()
            viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        }
        assertEquals(1, viewModel.uiState.value.dailyStreak)
        assertTrue(viewModel.uiState.value.dailyGoalJustCompleted)
    }

    @Test
    fun `item mastery marks entry mastered at 3 consecutive correct and unmasters on incorrect answer`() = runBlocking {
        val targetCode = viewModel.uiState.value.currentEntry.codeOrPhrase
        // Pre-seed targetCode with 2 consecutive correct answers
        fakeProgressRepository.updateProgress {
            it.copy(
                itemMastery = mapOf(
                    targetCode to ItemMastery(attempts = 2, correct = 2, consecutiveCorrect = 2)
                )
            )
        }
        assertEquals(0, viewModel.uiState.value.masteredInCategoryCount)

        // 3rd consecutive correct answer on targetCode -> becomes mastered!
        viewModel.selectOption(targetCode)
        assertEquals(1, viewModel.uiState.value.masteredInCategoryCount)
        val masteryAfter3 = fakeProgressRepository.trainerProgress.value.itemMastery[targetCode]!!
        assertEquals(ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3), masteryAfter3)
        assertTrue(masteryAfter3.isMastered)

        // Now suppose the user misses a mastered item -> consecutiveCorrect resets to 0
        viewModel.nextQuestion()
        val secondEntry = viewModel.uiState.value.currentEntry.codeOrPhrase
        fakeProgressRepository.updateProgress {
            it.copy(
                itemMastery = it.itemMastery + (secondEntry to ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3))
            )
        }
        assertEquals(2, viewModel.uiState.value.masteredInCategoryCount)

        val wrongOption = viewModel.uiState.value.options.first { it != secondEntry }
        viewModel.selectOption(wrongOption)
        assertEquals(1, viewModel.uiState.value.masteredInCategoryCount)
        val secondMasteryAfterMiss = fakeProgressRepository.trainerProgress.value.itemMastery[secondEntry]!!
        assertEquals(0, secondMasteryAfterMiss.consecutiveCorrect)
        assertFalse(secondMasteryAfterMiss.isMastered)
    }

    @Test
    fun `nextQuestion advances to non-repeating prompt clears answered state preserves counters and plays audio with current preferences`() = runBlocking {
        fakePreferencesRepository.updateFrequency(650)
        fakePreferencesRepository.updateWpm(25)
        fakePreferencesRepository.updateFarnsworthWpm(18)

        val firstEntry = viewModel.uiState.value.currentEntry
        viewModel.selectOption(firstEntry.codeOrPhrase)
        assertEquals(1, viewModel.uiState.value.correctCount)
        assertEquals(1, viewModel.uiState.value.totalAnswered)
        assertEquals(1, viewModel.uiState.value.streak)

        var previousEntry = firstEntry
        repeat(25) { step ->
            val playCountBefore = fakeAudioPlayer.playCalls.size
            viewModel.nextQuestion()
            val state = viewModel.uiState.value

            assertNotEquals("Prompt should not repeat consecutively on step $step", previousEntry, state.currentEntry)
            assertNull(state.selectedOption)
            assertNull(state.isCorrect)
            assertNull(state.revealedMeaning)
            assertNull(state.revealedMorsePattern)
            assertEquals(0, state.replayCount)
            assertNull(state.lastXpAward)
            assertNull(state.rankedUpTo)
            assertFalse(state.dailyGoalJustCompleted)
            assertEquals(1, state.correctCount)
            assertEquals(1, state.totalAnswered)
            assertEquals(1, state.streak)

            assertEquals(playCountBefore + 1, fakeAudioPlayer.playCalls.size)
            val lastPlay = fakeAudioPlayer.playCalls.last()
            assertEquals(state.currentEntry.codeOrPhrase, lastPlay.first)
            assertEquals(UserPreferences(frequency = 650, wpm = 25, farnsworthWpm = 18), lastPlay.second)

            previousEntry = state.currentEntry
        }
    }

    @Test
    fun `replayAudio invokes MorseAudioPlayer with active prompt and latest reactive UserPreferences`() = runBlocking {
        val activePrompt = viewModel.uiState.value.currentEntry.codeOrPhrase

        viewModel.replayAudio()
        assertEquals(1, fakeAudioPlayer.playCalls.size)
        assertEquals(activePrompt, fakeAudioPlayer.playCalls.last().first)
        assertEquals(UserPreferences(frequency = 700, wpm = 20, farnsworthWpm = 20), fakeAudioPlayer.playCalls.last().second)

        fakePreferencesRepository.updateFrequency(800)
        fakePreferencesRepository.updateWpm(28)
        fakePreferencesRepository.updateFarnsworthWpm(15)

        viewModel.replayAudio()
        assertEquals(2, fakeAudioPlayer.playCalls.size)
        assertEquals(activePrompt, fakeAudioPlayer.playCalls.last().first)
        assertEquals(UserPreferences(frequency = 800, wpm = 28, farnsworthWpm = 15), fakeAudioPlayer.playCalls.last().second)
    }

    @Test
    fun `stopAudio invokes MorseAudioPlayer stop`() {
        assertEquals(0, fakeAudioPlayer.stopCallCount)
        viewModel.stopAudio()
        assertEquals(1, fakeAudioPlayer.stopCallCount)
        viewModel.stopAudio()
        assertEquals(2, fakeAudioPlayer.stopCallCount)
    }

    @Test
    fun `selectTab switches between PRACTICE and STATS and preserves tab across progress updates and category changes`() = runBlocking {
        val today = LocalDate.of(2026, 11, 15).toEpochDay()
        assertEquals(QsoTrainerTab.PRACTICE, viewModel.uiState.value.selectedTab)
        assertEquals(today, viewModel.uiState.value.todayEpochDay)

        viewModel.selectTab(QsoTrainerTab.STATS)
        assertEquals(QsoTrainerTab.STATS, viewModel.uiState.value.selectedTab)

        // Progress updates should sync dailyHistory, itemMastery, and todayEpochDay while preserving selectedTab
        val masteryMap = mapOf("QTH" to ItemMastery(attempts = 2, correct = 1, consecutiveCorrect = 1))
        val historyMap = mapOf(today to 3)
        fakeProgressRepository.updateProgress {
            it.copy(
                totalXp = 120,
                currentDayEpochDay = today,
                currentDayAnsweredCount = 3,
                dailyHistory = historyMap,
                itemMastery = masteryMap
            )
        }
        assertEquals(QsoTrainerTab.STATS, viewModel.uiState.value.selectedTab)
        assertEquals(historyMap, viewModel.uiState.value.dailyHistory)
        assertEquals(masteryMap, viewModel.uiState.value.itemMastery)
        assertEquals(today, viewModel.uiState.value.todayEpochDay)

        // Category change should also preserve selectedTab
        viewModel.selectCategory(QsoCategory.FULL_PHRASES)
        assertEquals(QsoCategory.FULL_PHRASES, viewModel.uiState.value.selectedCategory)
        assertEquals(QsoTrainerTab.STATS, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(QsoTrainerTab.PRACTICE)
        assertEquals(QsoTrainerTab.PRACTICE, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `practiceWeakestCategory selects target category and switches to PRACTICE tab`() {
        viewModel.selectTab(QsoTrainerTab.STATS)
        assertEquals(QsoTrainerTab.STATS, viewModel.uiState.value.selectedTab)
        assertEquals(QsoCategory.SINGLE_CODES, viewModel.uiState.value.selectedCategory)

        viewModel.practiceWeakestCategory(QsoCategory.FULL_PHRASES)

        val state = viewModel.uiState.value
        assertEquals(QsoCategory.FULL_PHRASES, state.selectedCategory)
        assertEquals(QsoTrainerTab.PRACTICE, state.selectedTab)
        assertEquals(QsoEntryType.FULL_PHRASE, state.currentEntry.type)
    }

    @Test
    fun `previewItemAudio plays requested item with current UserPreferences without altering replayCount or quiz state`() = runBlocking {
        fakePreferencesRepository.updateFrequency(620)
        fakePreferencesRepository.updateWpm(22)
        fakePreferencesRepository.updateFarnsworthWpm(16)

        val stateBefore = viewModel.uiState.value
        assertEquals(0, stateBefore.replayCount)

        viewModel.previewItemAudio("QSL VIA BURO")

        val stateAfter = viewModel.uiState.value
        assertEquals(0, stateAfter.replayCount)
        assertEquals(stateBefore.currentEntry, stateAfter.currentEntry)
        assertEquals(stateBefore.options, stateAfter.options)
        assertNull(stateAfter.selectedOption)
        assertEquals(1, fakeAudioPlayer.playCalls.size)
        assertEquals("QSL VIA BURO", fakeAudioPlayer.playCalls.last().first)
        assertEquals(
            UserPreferences(frequency = 620, wpm = 22, farnsworthWpm = 16),
            fakeAudioPlayer.playCalls.last().second
        )
    }

    @Test
    fun `resetProgress stops audio clears persisted TrainerProgress and resets session counters while preserving category and tab`() {
        viewModel.selectCategory(QsoCategory.FULL_PHRASES)
        viewModel.selectTab(QsoTrainerTab.STATS)
        val stopCallsBeforeAnswer = fakeAudioPlayer.stopCallCount

        // Answer 2 questions to accumulate session score, XP, daily history, and mastery
        val firstCorrect = viewModel.uiState.value.currentEntry.codeOrPhrase
        viewModel.selectOption(firstCorrect)
        viewModel.nextQuestion()
        val secondCorrect = viewModel.uiState.value.currentEntry.codeOrPhrase
        viewModel.selectOption(secondCorrect)

        assertTrue(viewModel.uiState.value.totalXp > 0)
        assertEquals(2, viewModel.uiState.value.correctCount)
        assertEquals(2, viewModel.uiState.value.totalAnswered)
        assertTrue(viewModel.uiState.value.dailyHistory.isNotEmpty())
        assertTrue(viewModel.uiState.value.itemMastery.isNotEmpty())

        viewModel.resetProgress()

        assertEquals(stopCallsBeforeAnswer + 1, fakeAudioPlayer.stopCallCount)
        assertEquals(TrainerProgress(), fakeProgressRepository.trainerProgress.value)

        val resetState = viewModel.uiState.value
        assertEquals(QsoCategory.FULL_PHRASES, resetState.selectedCategory)
        assertEquals(QsoTrainerTab.STATS, resetState.selectedTab)
        assertEquals(0, resetState.correctCount)
        assertEquals(0, resetState.totalAnswered)
        assertEquals(0, resetState.streak)
        assertEquals(0, resetState.bestStreak)
        assertEquals(0, resetState.totalXp)
        assertEquals(OperatorRank.SWL, resetState.operatorRank)
        assertEquals(0, resetState.rankProgressPercent)
        assertEquals(0, resetState.dailyStreak)
        assertEquals(0, resetState.dailyAnsweredCount)
        assertEquals(0, resetState.masteredInCategoryCount)
        assertTrue(resetState.dailyHistory.isEmpty())
        assertTrue(resetState.itemMastery.isEmpty())
        assertNull(resetState.selectedOption)
        assertNull(resetState.isCorrect)
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

    private class FakeTrainerProgressRepository(
        initialProgress: TrainerProgress = TrainerProgress()
    ) : TrainerProgressRepository {
        private val _trainerProgress = MutableStateFlow(initialProgress)
        override val trainerProgress: StateFlow<TrainerProgress> = _trainerProgress.asStateFlow()

        override suspend fun updateProgress(transform: (TrainerProgress) -> TrainerProgress) {
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

        fun setLocalDate(date: LocalDate) {
            currentInstant = date.atStartOfDay(zoneField).toInstant()
        }

        override fun getZone(): ZoneId = zoneField

        override fun withZone(zone: ZoneId): Clock = MutableClock(
            Instant.ofEpochMilli(currentInstant.toEpochMilli()).atZone(zone).toLocalDate(),
            zone
        )

        override fun instant(): Instant = currentInstant
    }
}
