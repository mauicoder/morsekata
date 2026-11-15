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
        val actualFullPhraseTexts = entries.filter { it.type == QsoEntryType.FULL_PHRASE }
            .map { it.codeOrPhrase }
            .toSet()

        assertTrue(
            "Missing required single codes: ${requiredSingleCodes - singleCodeTexts}",
            singleCodeTexts.containsAll(requiredSingleCodes)
        )
        assertTrue(
            "Missing required full phrases: ${requiredFullPhrases - actualFullPhraseTexts}",
            actualFullPhraseTexts.containsAll(requiredFullPhrases)
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
    fun `ItemMastery isMastered is true when consecutiveCorrect is at least 3`() {
        assertFalse(ItemMastery(attempts = 0, correct = 0, consecutiveCorrect = 0).isMastered)
        assertFalse(ItemMastery(attempts = 2, correct = 2, consecutiveCorrect = 2).isMastered)
        assertTrue(ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3).isMastered)
        assertTrue(ItemMastery(attempts = 6, correct = 5, consecutiveCorrect = 4).isMastered)
    }

    @Test
    fun `OperatorRank fromXp and progressPercent match 5 Ham Radio rank thresholds`() {
        assertEquals("SWL (Shortwave Listener)", OperatorRank.SWL.displayName)
        assertEquals(OperatorRank.SWL, OperatorRank.fromXp(0))
        assertEquals(OperatorRank.SWL, OperatorRank.fromXp(99))
        assertEquals(0, OperatorRank.progressPercent(0))
        assertEquals(50, OperatorRank.progressPercent(50))
        assertEquals(99, OperatorRank.progressPercent(99))

        assertEquals(OperatorRank.NOVICE, OperatorRank.fromXp(100))
        assertEquals(OperatorRank.NOVICE, OperatorRank.fromXp(299))
        assertEquals(0, OperatorRank.progressPercent(100))
        assertEquals(50, OperatorRank.progressPercent(200))

        assertEquals(OperatorRank.TECHNICIAN, OperatorRank.fromXp(300))
        assertEquals(OperatorRank.TECHNICIAN, OperatorRank.fromXp(699))
        assertEquals(0, OperatorRank.progressPercent(300))
        assertEquals(50, OperatorRank.progressPercent(500))

        assertEquals(OperatorRank.GENERAL, OperatorRank.fromXp(700))
        assertEquals(OperatorRank.GENERAL, OperatorRank.fromXp(1499))
        assertEquals(0, OperatorRank.progressPercent(700))
        assertEquals(50, OperatorRank.progressPercent(1100))

        assertEquals(OperatorRank.AMATEUR_EXTRA, OperatorRank.fromXp(1500))
        assertEquals(OperatorRank.AMATEUR_EXTRA, OperatorRank.fromXp(5000))
        assertEquals(100, OperatorRank.progressPercent(1500))
        assertEquals(100, OperatorRank.progressPercent(5000))
    }

    @Test
    fun `XpAward calculate computes Base Streak and FirstCopy bonuses accurately`() {
        val wrong = XpAward.calculate(isCorrect = false, newAnswerStreak = 0, preAnswerReplayCount = 0)
        assertEquals(XpAward(0, 0, 0), wrong)
        assertEquals(0, wrong.totalXpEarned)

        val firstCopyStreak1 = XpAward.calculate(isCorrect = true, newAnswerStreak = 1, preAnswerReplayCount = 0)
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 0, firstCopyBonusXp = 5), firstCopyStreak1)
        assertEquals(15, firstCopyStreak1.totalXpEarned)

        val replayedStreak3 = XpAward.calculate(isCorrect = true, newAnswerStreak = 3, preAnswerReplayCount = 2)
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 4, firstCopyBonusXp = 0), replayedStreak3)
        assertEquals(14, replayedStreak3.totalXpEarned)

        val cappedStreak10 = XpAward.calculate(isCorrect = true, newAnswerStreak = 10, preAnswerReplayCount = 0)
        assertEquals(XpAward(baseXp = 10, streakBonusXp = 10, firstCopyBonusXp = 5), cappedStreak10)
        assertEquals(25, cappedStreak10.totalXpEarned)
    }

    @Test
    fun `entryWeight and selectAdaptiveEntry weight missed items 4x mastered items and exclude previous entry`() {
        assertEquals(3, QsoCatalog.entryWeight(null))
        assertEquals(3, QsoCatalog.entryWeight(ItemMastery(attempts = 0, correct = 0, consecutiveCorrect = 0)))
        assertEquals(4, QsoCatalog.entryWeight(ItemMastery(attempts = 2, correct = 1, consecutiveCorrect = 0)))
        assertEquals(2, QsoCatalog.entryWeight(ItemMastery(attempts = 1, correct = 1, consecutiveCorrect = 1)))
        assertEquals(2, QsoCatalog.entryWeight(ItemMastery(attempts = 2, correct = 2, consecutiveCorrect = 2)))
        assertEquals(1, QsoCatalog.entryWeight(ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3)))

        val singlePool = QsoCatalog.entriesForCategory(QsoCategory.SINGLE_CODES)
        val excluded = singlePool[0]
        val missedTarget = singlePool[1]
        // Mark all other items mastered (weight 1) and missedTarget missed (weight 4)
        val masteryMap = singlePool.associate { entry ->
            entry.codeOrPhrase to if (entry == missedTarget) {
                ItemMastery(attempts = 3, correct = 1, consecutiveCorrect = 0)
            } else {
                ItemMastery(attempts = 3, correct = 3, consecutiveCorrect = 3)
            }
        }

        val random = Random(99)
        var missedCount = 0
        repeat(200) {
            val picked = QsoCatalog.selectAdaptiveEntry(
                category = QsoCategory.SINGLE_CODES,
                masteryMap = masteryMap,
                exclude = excluded,
                random = random
            )
            assertNotEquals(excluded, picked)
            if (picked == missedTarget) {
                missedCount++
            }
        }
        assertTrue("Missed target should be sampled disproportionately often, got $missedCount/200", missedCount >= 15)
    }

    @Test
    fun `formatMorsePattern formats single codes and multi-word phrases with slash word separators`() {
        assertEquals("--.- - ....", QsoCatalog.formatMorsePattern("QTH"))
        assertEquals("-.-. --.- / -.-. --.- / -.-. --.- / -.. .", QsoCatalog.formatMorsePattern("CQ CQ CQ DE"))
        assertEquals("--.- - .... / .. ...", QsoCatalog.formatMorsePattern("QTH IS"))
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
