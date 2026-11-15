package net.maui.morselab.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import net.maui.morselab.trainer.QsoCatalog
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoEntryType
import net.maui.morselab.trainer.QsoTrainerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class QsoTrainerViewModelTest {

    private lateinit var fakePreferencesRepository: FakeUserPreferencesRepository
    private lateinit var fakeAudioPlayer: FakeMorseAudioPlayer
    private lateinit var viewModel: QsoTrainerViewModel

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeUserPreferencesRepository()
        fakeAudioPlayer = FakeMorseAudioPlayer()
        viewModel = QsoTrainerViewModel(
            userPreferencesRepository = fakePreferencesRepository,
            morseAudioPlayer = fakeAudioPlayer,
            random = Random(42)
        )
    }

    @Test
    fun `category selection filters prompts to expected subset and resets score and streak`() {
        // Build up score and streak first
        val firstCorrect = viewModel.uiState.value.currentEntry.codeOrPhrase
        viewModel.selectOption(firstCorrect)
        assertEquals(1, viewModel.uiState.value.correctCount)
        assertEquals(1, viewModel.uiState.value.totalAnswered)
        assertEquals(1, viewModel.uiState.value.streak)

        // Switch to FULL_PHRASES
        viewModel.selectCategory(QsoCategory.FULL_PHRASES)
        val fullPhraseState = viewModel.uiState.value
        assertEquals(QsoCategory.FULL_PHRASES, fullPhraseState.selectedCategory)
        assertEquals(QsoEntryType.FULL_PHRASE, fullPhraseState.currentEntry.type)
        assertTrue(QsoCatalog.entriesForCategory(QsoCategory.FULL_PHRASES).contains(fullPhraseState.currentEntry))
        assertResetUnansweredState(fullPhraseState)

        // Verify multiple draws stay in FULL_PHRASES
        repeat(15) {
            viewModel.nextQuestion()
            assertEquals(QsoEntryType.FULL_PHRASE, viewModel.uiState.value.currentEntry.type)
        }

        // Answer to increment counters again, then switch to SINGLE_CODES
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertEquals(1, viewModel.uiState.value.correctCount)

        viewModel.selectCategory(QsoCategory.SINGLE_CODES)
        val singleCodeState = viewModel.uiState.value
        assertEquals(QsoCategory.SINGLE_CODES, singleCodeState.selectedCategory)
        assertEquals(QsoEntryType.SINGLE_CODE, singleCodeState.currentEntry.type)
        assertTrue(QsoCatalog.entriesForCategory(QsoCategory.SINGLE_CODES).contains(singleCodeState.currentEntry))
        assertResetUnansweredState(singleCodeState)

        repeat(15) {
            viewModel.nextQuestion()
            assertEquals(QsoEntryType.SINGLE_CODE, viewModel.uiState.value.currentEntry.type)
        }

        // Answer to increment counters again, then switch to MIXED
        viewModel.selectOption(viewModel.uiState.value.currentEntry.codeOrPhrase)
        assertEquals(1, viewModel.uiState.value.correctCount)

        viewModel.selectCategory(QsoCategory.MIXED)
        val mixedState = viewModel.uiState.value
        assertEquals(QsoCategory.MIXED, mixedState.selectedCategory)
        assertTrue(QsoCatalog.entriesForCategory(QsoCategory.MIXED).contains(mixedState.currentEntry))
        assertResetUnansweredState(mixedState)

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

    private fun assertResetUnansweredState(state: QsoTrainerUiState) {
        assertEquals(0, state.correctCount)
        assertEquals(0, state.totalAnswered)
        assertEquals(0, state.streak)
        assertNull(state.selectedOption)
        assertNull(state.isCorrect)
        assertNull(state.revealedMeaning)
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

        // Initial question
        assertValidOptions()

        // Across categories and nextQuestion() transitions
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
    fun `selecting correct option reveals meaning increments score and streak and locks further selections`() {
        val initialState = viewModel.uiState.value
        assertNull(initialState.selectedOption)
        assertNull(initialState.isCorrect)
        assertNull(initialState.revealedMeaning)
        assertEquals(0, initialState.correctCount)
        assertEquals(0, initialState.totalAnswered)
        assertEquals(0, initialState.streak)

        val correctOption = initialState.currentEntry.codeOrPhrase
        val distractorOption = initialState.options.first { it != correctOption }

        viewModel.selectOption(correctOption)

        val answeredState = viewModel.uiState.value
        assertEquals(correctOption, answeredState.selectedOption)
        assertEquals(true, answeredState.isCorrect)
        assertEquals(initialState.currentEntry.meaning, answeredState.revealedMeaning)
        assertEquals(1, answeredState.correctCount)
        assertEquals(1, answeredState.totalAnswered)
        assertEquals(1, answeredState.streak)

        // Attempting to select another option while locked must be a no-op
        viewModel.selectOption(distractorOption)
        val afterSecondClick = viewModel.uiState.value
        assertEquals(correctOption, afterSecondClick.selectedOption)
        assertEquals(true, afterSecondClick.isCorrect)
        assertEquals(initialState.currentEntry.meaning, afterSecondClick.revealedMeaning)
        assertEquals(1, afterSecondClick.correctCount)
        assertEquals(1, afterSecondClick.totalAnswered)
        assertEquals(1, afterSecondClick.streak)
    }

    @Test
    fun `selecting incorrect option sets isCorrect false reveals meaning increments totalAnswered and resets streak to 0`() {
        // Build a streak of 2 first
        repeat(2) {
            val correct = viewModel.uiState.value.currentEntry.codeOrPhrase
            viewModel.selectOption(correct)
            viewModel.nextQuestion()
        }
        assertEquals(2, viewModel.uiState.value.correctCount)
        assertEquals(2, viewModel.uiState.value.totalAnswered)
        assertEquals(2, viewModel.uiState.value.streak)

        // Now select an incorrect option
        val currentState = viewModel.uiState.value
        val wrongOption = currentState.options.first { it != currentState.currentEntry.codeOrPhrase }
        viewModel.selectOption(wrongOption)

        val answeredState = viewModel.uiState.value
        assertEquals(wrongOption, answeredState.selectedOption)
        assertEquals(false, answeredState.isCorrect)
        assertEquals(currentState.currentEntry.meaning, answeredState.revealedMeaning)
        assertEquals(2, answeredState.correctCount)
        assertEquals(3, answeredState.totalAnswered)
        assertEquals(0, answeredState.streak)

        // Attempting to change to the correct option after answering wrong is also locked
        viewModel.selectOption(currentState.currentEntry.codeOrPhrase)
        assertEquals(wrongOption, viewModel.uiState.value.selectedOption)
        assertEquals(false, viewModel.uiState.value.isCorrect)
        assertEquals(2, viewModel.uiState.value.correctCount)
        assertEquals(3, viewModel.uiState.value.totalAnswered)
        assertEquals(0, viewModel.uiState.value.streak)
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

        // Update reactive preferences
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
