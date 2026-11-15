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
            itemMastery = mapOf("QTH" to ItemMastery(attempts = 4, correct = 3, consecutiveCorrect = 2))
        )

        val output = ByteArrayOutputStream()
        TrainerProgressSerializer.writeTo(progress, output)

        val input = ByteArrayInputStream(output.toByteArray())
        val deserialized = TrainerProgressSerializer.readFrom(input)

        assertEquals(progress, deserialized)
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
