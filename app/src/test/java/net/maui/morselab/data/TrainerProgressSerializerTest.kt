package net.maui.morselab.data

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import net.maui.morselab.trainer.ItemMastery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            dailyHistory = mapOf(19999L to 6, 20000L to 10)
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
}
