package dev.mauicoder.morsekata.data

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import dev.mauicoder.morsekata.trainer.ItemMastery
import dev.mauicoder.morsekata.trainer.OperatorRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TrainerProgressSerializerTest {

    @Test
    fun `defaultValue has expected initial zeroed state`() {
        val defaultProgress = TrainerProgressSerializer.defaultValue
        assertEquals(0, defaultProgress.totalXp)
        assertEquals(0, defaultProgress.currentAnswerStreak)
        assertEquals(0, defaultProgress.bestAnswerStreak)
        assertEquals(0, defaultProgress.dailyStreak)
        assertNull(defaultProgress.currentDayEpochDay)
        assertEquals(0, defaultProgress.currentDayAnsweredCount)
        assertNull(defaultProgress.lastGoalCompletedEpochDay)
        assertEquals(emptyMap<String, ItemMastery>(), defaultProgress.itemMastery)
        assertEquals(emptyMap<Long, Int>(), defaultProgress.dailyHistory)
        assertEquals(0, defaultProgress.completedFullQsoCount)
        assertEquals(0, defaultProgress.bestCallsignWpm)
        assertEquals(0, defaultProgress.completedCallsignCount)
    }

    @Test
    fun `writeTo and readFrom round-trip TrainerProgress accurately`(): Unit = runBlocking {
        val progress = TrainerProgress(
            totalXp = 150,
            currentAnswerStreak = 3,
            bestAnswerStreak = 5,
            dailyStreak = 2,
            currentDayEpochDay = 20000L,
            currentDayAnsweredCount = 10,
            lastGoalCompletedEpochDay = 19999L,
            itemMastery = mapOf("QTH" to ItemMastery(attempts = 4, correct = 3, consecutiveCorrect = 2)),
            dailyHistory = mapOf(19999L to 6, 20000L to 10),
            completedFullQsoCount = 4
        )

        val output = ByteArrayOutputStream()
        TrainerProgressSerializer.writeTo(progress, output)

        val input = ByteArrayInputStream(output.toByteArray())
        val deserialized = TrainerProgressSerializer.readFrom(input)

        assertEquals(progress, deserialized)
    }

    @Test
    fun `readFrom legacy JSON without dailyHistory falls back via dailyCountOn`(): Unit = runBlocking {
        val legacyJson = """
            {"totalXp":120,"currentDayEpochDay":20000,"currentDayAnsweredCount":4}
        """.trimIndent()
        val input = ByteArrayInputStream(legacyJson.encodeToByteArray())
        val deserialized = TrainerProgressSerializer.readFrom(input)

        assertEquals(emptyMap<Long, Int>(), deserialized.dailyHistory)
        assertEquals(4, deserialized.dailyCountOn(epochDay = 20000L, todayEpochDay = 20000L))
        assertEquals(0, deserialized.dailyCountOn(epochDay = 19999L, todayEpochDay = 20000L))
        assertEquals(0, deserialized.dailyCountOn(epochDay = 20001L, todayEpochDay = 20000L))
    }

    @Test
    fun `dailyCountOn prefers dailyHistory and returns zero for future days`() {
        val progress = TrainerProgress(
            currentDayEpochDay = 20005L,
            currentDayAnsweredCount = 3,
            dailyHistory = mapOf(20004L to 7, 20005L to 3, 20006L to 9)
        )

        assertEquals(7, progress.dailyCountOn(epochDay = 20004L, todayEpochDay = 20005L))
        assertEquals(3, progress.dailyCountOn(epochDay = 20005L, todayEpochDay = 20005L))
        assertEquals(0, progress.dailyCountOn(epochDay = 20006L, todayEpochDay = 20005L))
    }

    @Test
    fun `recordAnswer preserves legacy currentDay count and prunes dailyHistory to 14-day window`() {
        var progress = TrainerProgress(
            currentDayEpochDay = 100L,
            currentDayAnsweredCount = 5
        )

        // Roll over to day 101 from a legacy state without dailyHistory
        progress = progress.recordAnswer(
            codeOrPhrase = "QTH",
            isCorrect = true,
            preAnswerReplayCount = 0,
            todayEpochDay = 101L,
            dailyGoalTarget = 5
        ).updatedProgress

        assertEquals(mapOf(100L to 5, 101L to 1), progress.dailyHistory)

        // Record answers across days 102..114
        for (day in 102L..114L) {
            progress = progress.recordAnswer(
                codeOrPhrase = "QTH",
                isCorrect = true,
                preAnswerReplayCount = 0,
                todayEpochDay = day,
                dailyGoalTarget = 5
            ).updatedProgress
        }

        // At day 114, retention window is 101..114 (14 days); day 100 is pruned
        assertEquals((101L..114L).toSet(), progress.dailyHistory.keys)
        assertEquals(0, progress.dailyCountOn(epochDay = 100L, todayEpochDay = 114L))
        assertEquals(1, progress.dailyCountOn(epochDay = 101L, todayEpochDay = 114L))
        assertEquals(1, progress.dailyCountOn(epochDay = 114L, todayEpochDay = 114L))
    }

    @Test
    fun `readFrom ignores unknown JSON keys for forward schema evolution`(): Unit = runBlocking {
        val jsonWithUnknownField = """
            {"totalXp":250,"futureField":"ignored","currentAnswerStreak":4}
        """.trimIndent()
        val input = ByteArrayInputStream(jsonWithUnknownField.encodeToByteArray())
        val deserialized = TrainerProgressSerializer.readFrom(input)

        assertEquals(250, deserialized.totalXp)
        assertEquals(4, deserialized.currentAnswerStreak)
    }

    @Test(expected = CorruptionException::class)
    fun `readFrom throws CorruptionException on malformed JSON`(): Unit = runBlocking {
        val corruptedInput = ByteArrayInputStream("{ invalid json".encodeToByteArray())
        TrainerProgressSerializer.readFrom(corruptedInput)
    }

    @Test
    fun `recordFullQsoStage awards field XP, streak bonus, first-copy bonus, and final stage bonus on clean copy`() {
        val initial = TrainerProgress(
            totalXp = 80,
            currentAnswerStreak = 2,
            bestAnswerStreak = 2,
            currentDayEpochDay = 200L,
            currentDayAnsweredCount = 4,
            dailyHistory = mapOf(200L to 4),
            completedFullQsoCount = 1
        )

        val transition = initial.recordFullQsoStage(
            correctFieldCount = 3,
            totalFieldCount = 3,
            preStageReplayCount = 0,
            isFinalStage = true,
            todayEpochDay = 200L,
            dailyGoalTarget = 5
        )

        // 3 * 5 = 15 field XP + 25 final bonus = 40 base XP; newStreak = 3 -> (3-1)*2 = 4 streak XP; 0 replays -> 5 first-copy XP
        assertEquals(40, transition.xpAward.baseXp)
        assertEquals(4, transition.xpAward.streakBonusXp)
        assertEquals(5, transition.xpAward.firstCopyBonusXp)
        assertEquals(49, transition.xpAward.totalXpEarned)
        assertEquals(OperatorRank.NOVICE, transition.rankedUpTo)
        assertTrue(transition.dailyGoalJustCompleted)

        val updated = transition.updatedProgress
        assertEquals(129, updated.totalXp)
        assertEquals(3, updated.currentAnswerStreak)
        assertEquals(3, updated.bestAnswerStreak)
        assertEquals(1, updated.dailyStreak)
        assertEquals(5, updated.currentDayAnsweredCount)
        assertEquals(5, updated.dailyHistory[200L])
        assertEquals(2, updated.completedFullQsoCount)
    }

    @Test
    fun `recordFullQsoStage resets answer streak on partial copy while still awarding partial field XP and final stage bonus`() {
        val initial = TrainerProgress(
            totalXp = 50,
            currentAnswerStreak = 4,
            bestAnswerStreak = 4,
            currentDayEpochDay = 200L,
            currentDayAnsweredCount = 1,
            dailyHistory = mapOf(200L to 1),
            completedFullQsoCount = 0
        )

        val stage2Transition = initial.recordFullQsoStage(
            correctFieldCount = 2,
            totalFieldCount = 4,
            preStageReplayCount = 0,
            isFinalStage = false,
            todayEpochDay = 200L,
            dailyGoalTarget = 5
        )

        assertEquals(10, stage2Transition.xpAward.baseXp)
        assertEquals(0, stage2Transition.xpAward.streakBonusXp)
        assertEquals(0, stage2Transition.xpAward.firstCopyBonusXp)
        assertEquals(10, stage2Transition.xpAward.totalXpEarned)
        assertFalse(stage2Transition.dailyGoalJustCompleted)
        assertEquals(0, stage2Transition.updatedProgress.currentAnswerStreak)
        assertEquals(4, stage2Transition.updatedProgress.bestAnswerStreak)
        assertEquals(0, stage2Transition.updatedProgress.completedFullQsoCount)

        val stage3Transition = stage2Transition.updatedProgress.recordFullQsoStage(
            correctFieldCount = 1,
            totalFieldCount = 3,
            preStageReplayCount = 2,
            isFinalStage = true,
            todayEpochDay = 200L,
            dailyGoalTarget = 5
        )

        // 1 * 5 + 25 final bonus = 30 base XP; partial copy -> 0 streak & 0 first-copy
        assertEquals(30, stage3Transition.xpAward.baseXp)
        assertEquals(0, stage3Transition.xpAward.streakBonusXp)
        assertEquals(0, stage3Transition.xpAward.firstCopyBonusXp)
        assertEquals(90, stage3Transition.updatedProgress.totalXp)
        assertEquals(1, stage3Transition.updatedProgress.completedFullQsoCount)
        assertEquals(3, stage3Transition.updatedProgress.currentDayAnsweredCount)
    }

    @Test
    fun `readFrom legacy JSON without callsign metrics preserves backward compatibility with zero defaults`(): Unit = runBlocking {
        val legacyJson = """
            {"totalXp":200,"currentAnswerStreak":2,"bestAnswerStreak":5,"dailyStreak":1,"completedFullQsoCount":3}
        """.trimIndent()
        val input = ByteArrayInputStream(legacyJson.encodeToByteArray())
        val deserialized = TrainerProgressSerializer.readFrom(input)

        assertEquals(200, deserialized.totalXp)
        assertEquals(2, deserialized.currentAnswerStreak)
        assertEquals(3, deserialized.completedFullQsoCount)
        assertEquals(0, deserialized.bestCallsignWpm)
        assertEquals(0, deserialized.completedCallsignCount)
    }

    @Test
    fun `writeTo and readFrom round-trip TrainerProgress with bestCallsignWpm and completedCallsignCount`(): Unit = runBlocking {
        val progress = TrainerProgress(
            totalXp = 320,
            currentAnswerStreak = 4,
            bestAnswerStreak = 7,
            completedFullQsoCount = 2,
            bestCallsignWpm = 28,
            completedCallsignCount = 15
        )
        val output = ByteArrayOutputStream()
        TrainerProgressSerializer.writeTo(progress, output)

        val deserialized = TrainerProgressSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(progress, deserialized)
        assertEquals(28, deserialized.bestCallsignWpm)
        assertEquals(15, deserialized.completedCallsignCount)
    }

    @Test
    fun `recordCallsignAnswer awards base XP, streak bonus, first-copy bonus, and updates callsign count and best WPM`() {
        val initial = TrainerProgress(
            totalXp = 50,
            currentAnswerStreak = 2,
            bestAnswerStreak = 2,
            bestCallsignWpm = 20,
            completedCallsignCount = 3,
            currentDayEpochDay = 300L,
            currentDayAnsweredCount = 1
        )

        // Callsign only correct with 0 replays -> 10 base + 4 streak ((3-1)*2) + 5 first-copy = 19 XP
        val transition = initial.recordCallsignAnswer(
            isCorrect = true,
            isContestExchange = false,
            preAnswerReplayCount = 0,
            achievedWpm = 25,
            todayEpochDay = 300L,
            dailyGoalTarget = 5
        )

        assertEquals(10, transition.xpAward.baseXp)
        assertEquals(4, transition.xpAward.streakBonusXp)
        assertEquals(5, transition.xpAward.firstCopyBonusXp)
        assertEquals(19, transition.xpAward.totalXpEarned)

        val updated = transition.updatedProgress
        assertEquals(69, updated.totalXp)
        assertEquals(3, updated.currentAnswerStreak)
        assertEquals(3, updated.bestAnswerStreak)
        assertEquals(25, updated.bestCallsignWpm)
        assertEquals(4, updated.completedCallsignCount)
        assertEquals(2, updated.currentDayAnsweredCount)
    }

    @Test
    fun `recordCallsignAnswer in contest exchange awards 15 base XP and does not award first-copy bonus if replayed`() {
        val initial = TrainerProgress(
            totalXp = 100,
            currentAnswerStreak = 0,
            bestCallsignWpm = 24,
            completedCallsignCount = 5
        )

        val transition = initial.recordCallsignAnswer(
            isCorrect = true,
            isContestExchange = true,
            preAnswerReplayCount = 1,
            achievedWpm = 22,
            todayEpochDay = 300L,
            dailyGoalTarget = 5
        )

        assertEquals(15, transition.xpAward.baseXp)
        assertEquals(0, transition.xpAward.streakBonusXp)
        assertEquals(0, transition.xpAward.firstCopyBonusXp)
        assertEquals(15, transition.xpAward.totalXpEarned)

        val updated = transition.updatedProgress
        assertEquals(115, updated.totalXp)
        assertEquals(1, updated.currentAnswerStreak)
        assertEquals(24, updated.bestCallsignWpm) // 22 < 24, keeps 24
        assertEquals(6, updated.completedCallsignCount)
    }

    @Test
    fun `recordCallsignAnswer on incorrect resets streak and does not increment completed callsigns or update best WPM`() {
        val initial = TrainerProgress(
            totalXp = 100,
            currentAnswerStreak = 5,
            bestCallsignWpm = 24,
            completedCallsignCount = 5
        )

        val transition = initial.recordCallsignAnswer(
            isCorrect = false,
            isContestExchange = false,
            preAnswerReplayCount = 0,
            achievedWpm = 30, // Higher than bestCallsignWpm (24)
            todayEpochDay = 300L,
            dailyGoalTarget = 5
        )

        assertEquals(0, transition.xpAward.totalXpEarned)
        val updated = transition.updatedProgress
        assertEquals(100, updated.totalXp)
        assertEquals(0, updated.currentAnswerStreak)
        assertEquals(5, updated.completedCallsignCount)
        assertEquals(24, updated.bestCallsignWpm) // Must remain 24 despite higher achievedWpm on failure
    }

    @Test
    fun `recordCallsignAnswer on incorrect with zero initial best WPM retains zero`() {
        val initial = TrainerProgress(
            bestCallsignWpm = 0
        )

        val transition = initial.recordCallsignAnswer(
            isCorrect = false,
            isContestExchange = false,
            preAnswerReplayCount = 0,
            achievedWpm = 20,
            todayEpochDay = 300L,
            dailyGoalTarget = 5
        )

        assertEquals(0, transition.updatedProgress.bestCallsignWpm)
    }

    @Test
    fun `readFrom version 1 progress JSON without callsign or QSO fields successfully migrates preserving all stats`(): Unit = runBlocking {
        val v1Json = """
            {
                "totalXp": 1250,
                "currentAnswerStreak": 7,
                "bestAnswerStreak": 15,
                "dailyStreak": 5,
                "currentDayEpochDay": 20412,
                "currentDayAnsweredCount": 12,
                "lastGoalCompletedEpochDay": 20411,
                "itemMastery": {
                    "SOS": {"attempts": 10, "correct": 9, "consecutiveCorrect": 5},
                    "CQ": {"attempts": 8, "correct": 8, "consecutiveCorrect": 8}
                }
            }
        """.trimIndent()

        val input = ByteArrayInputStream(v1Json.encodeToByteArray())
        val migrated = TrainerProgressSerializer.readFrom(input)

        // All existing historical stats must be fully intact
        assertEquals(1250, migrated.totalXp)
        assertEquals(7, migrated.currentAnswerStreak)
        assertEquals(15, migrated.bestAnswerStreak)
        assertEquals(5, migrated.dailyStreak)
        assertEquals(20412L, migrated.currentDayEpochDay)
        assertEquals(12, migrated.currentDayAnsweredCount)
        assertEquals(20411L, migrated.lastGoalCompletedEpochDay)
        assertEquals(2, migrated.itemMastery.size)
        assertEquals(9, migrated.itemMastery["SOS"]?.correct)
        assertEquals(8, migrated.itemMastery["CQ"]?.correct)

        // New fields cleanly default to zero without crashing or throwing
        assertEquals(0, migrated.completedFullQsoCount)
        assertEquals(0, migrated.bestCallsignWpm)
        assertEquals(0, migrated.completedCallsignCount)

        // Recording a new callsign activity keeps previous XP and updates stats
        val transition = migrated.recordCallsignAnswer(
            isCorrect = true,
            isContestExchange = false,
            preAnswerReplayCount = 0,
            achievedWpm = 22,
            todayEpochDay = 20412L,
            dailyGoalTarget = 10
        )

        // 1250 + 10 base + 10 streak bonus + 5 first-copy = 1275 XP
        assertEquals(1275, transition.updatedProgress.totalXp)
        assertEquals(22, transition.updatedProgress.bestCallsignWpm)
        assertEquals(1, transition.updatedProgress.completedCallsignCount)
        assertEquals(9, transition.updatedProgress.itemMastery["SOS"]?.correct)
    }
}
