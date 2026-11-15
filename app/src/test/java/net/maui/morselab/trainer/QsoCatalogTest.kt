package net.maui.morselab.trainer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import net.maui.morselab.utils.MorseCodeMaps
import net.maui.morselab.viewmodel.PlayTextViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QsoCatalogTest {

    private val requiredSingleCodes = setOf(
        "QTH", "QSL", "QRZ", "QRM", "QRN", "QSY", "QRP", "QRT", "QRV", "QSB",
        "CQ", "DE", "K", "R", "AR", "SK", "RST", "73", "88", "PSE",
        "TNX", "FER", "UR", "ES", "GL", "OM", "YL", "FB", "HW?"
    )

    private val requiredFullPhrases = setOf(
        "CQ CQ CQ DE", "UR RST 599", "TNX FER QSO", "QTH IS", "73 ES GL",
        "QRZ DE", "PSE K", "HW CPY?", "FB OM", "TU ES 73",
        "WX HR IS", "RIG HR IS", "ANT IS", "NAME HR IS", "QSL VIA BURO", "SRI QRM"
    )

    @Test
    fun `entries contains all required single codes and full phrases with valid meanings and characters`() {
        val entries = QsoCatalog.entries
        assertFalse("Catalog entries should not be empty", entries.isEmpty())

        val singleCodeTexts = entries.filter { it.type == QsoEntryType.SINGLE_CODE }
            .map { it.codeOrPhrase }
            .toSet()
        val fullPhraseTexts = entries.filter { it.type == QsoEntryType.FULL_PHRASE }
            .map { it.codeOrPhrase }
            .toSet()

        assertTrue(
            "Missing required single codes: ${requiredSingleCodes - singleCodeTexts}",
            singleCodeTexts.containsAll(requiredSingleCodes)
        )
        assertTrue(
            "Missing required full phrases: ${requiredFullPhrases - fullPhraseTexts}",
            fullPhraseTexts.containsAll(requiredFullPhrases)
        )

        entries.forEach { entry ->
            assertTrue(
                "Entry ${entry.codeOrPhrase} must have a non-blank meaning",
                entry.meaning.isNotBlank()
            )
            entry.codeOrPhrase.forEach { ch ->
                assertTrue(
                    "Character '$ch' in '${entry.codeOrPhrase}' must be space or in MorseCodeMaps.asciiToMorse",
                    ch == ' ' || MorseCodeMaps.asciiToMorse.containsKey(ch)
                )
            }
        }
    }

    @Test
    fun `entriesForCategory filters entries accurately by category`() {
        val singleEntries = QsoCatalog.entriesForCategory(QsoCategory.SINGLE_CODES)
        assertTrue(singleEntries.isNotEmpty())
        assertTrue(singleEntries.all { it.type == QsoEntryType.SINGLE_CODE })

        val phraseEntries = QsoCatalog.entriesForCategory(QsoCategory.FULL_PHRASES)
        assertTrue(phraseEntries.isNotEmpty())
        assertTrue(phraseEntries.all { it.type == QsoEntryType.FULL_PHRASE })

        val mixedEntries = QsoCatalog.entriesForCategory(QsoCategory.MIXED)
        assertEquals(QsoCatalog.entries, mixedEntries)
        assertEquals(singleEntries.size + phraseEntries.size, mixedEntries.size)
    }

    @Test
    fun `randomEntry returns entry from category and never returns excluded entry`() {
        val random = Random(42)
        for (category in QsoCategory.entries) {
            val pool = QsoCatalog.entriesForCategory(category)
            assertTrue(pool.size > 1)
            val excluded = pool.first()
            repeat(50) {
                val chosen = QsoCatalog.randomEntry(category, exclude = excluded, random = random)
                assertTrue(pool.contains(chosen))
                assertNotEquals(excluded, chosen)
            }
        }
    }

    @Test
    fun `generateOptions returns 4 unique options with target and 3 distractors of matching QsoEntryType`() {
        val random = Random(123)
        assertTrue("Catalog entries should not be empty", QsoCatalog.entries.isNotEmpty())
        val entriesByCode = QsoCatalog.entries.associateBy { it.codeOrPhrase }

        for (target in QsoCatalog.entries) {
            val options = QsoCatalog.generateOptions(target, random)
            assertEquals(4, options.size)
            assertEquals(4, options.toSet().size)
            assertTrue(options.contains(target.codeOrPhrase))

            options.forEach { optionText ->
                val optionEntry = entriesByCode[optionText]
                assertTrue("Option '$optionText' must exist in catalog", optionEntry != null)
                assertEquals(
                    "Option '$optionText' must match target type ${target.type}",
                    target.type,
                    optionEntry!!.type
                )
            }
        }
    }

    @Test
    fun `PlayTextViewModel delegates playMorseCallback and stopPlayback to MorseAudioPlayer`() {
        val prefs = UserPreferences(frequency = 650, wpm = 22, farnsworthWpm = 18)
        val fakeRepo = object : UserPreferencesRepository {
            override val userPreferences: StateFlow<UserPreferences> = MutableStateFlow(prefs)
            override suspend fun updateFrequency(frequency: Int) {}
            override suspend fun updateWpm(wpm: Int) {}
            override suspend fun updateFarnsworthWpm(farnsworthWpm: Int) {}
        }
        var playedText: String? = null
        var playedPrefs: UserPreferences? = null
        var stopCount = 0
        val fakePlayer = object : MorseAudioPlayer {
            override fun play(text: String, preferences: UserPreferences) {
                playedText = text
                playedPrefs = preferences
            }

            override fun stop() {
                stopCount++
            }
        }

        val viewModel = PlayTextViewModel(fakeRepo, fakePlayer)
        viewModel.playMorseCallback()
        assertEquals("Hello", playedText)
        assertEquals(prefs, playedPrefs)

        viewModel.stopPlayback()
        assertEquals(1, stopCount)
    }
}
