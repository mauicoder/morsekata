package dev.mauicoder.morsekata.data

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UserPreferencesSerializerTest {

    @Test
    fun `defaultValue has expected defaults`() {
        val defaults = UserPreferencesSerializer.defaultValue
        assertEquals(700, defaults.frequency)
        assertEquals(20, defaults.wpm)
        assertEquals(20, defaults.farnsworthWpm)
        assertFalse(defaults.showExperimentalFeatures)
    }

    @Test
    fun `writeTo and readFrom round-trip accurately`(): Unit = runBlocking {
        val prefs = UserPreferences(
            frequency = 650,
            wpm = 25,
            farnsworthWpm = 22,
            showExperimentalFeatures = true
        )

        val output = ByteArrayOutputStream()
        UserPreferencesSerializer.writeTo(prefs, output)

        val input = ByteArrayInputStream(output.toByteArray())
        val deserialized = UserPreferencesSerializer.readFrom(input)

        assertEquals(prefs, deserialized)
    }

    @Test
    fun `readFrom ignores unknown fields for forward compatibility across app updates`(): Unit = runBlocking {
        val jsonWithNewFeature = """
            {
                "frequency": 800,
                "wpm": 28,
                "futureSettingNotYetInOldCode": "cool_value",
                "showExperimentalFeatures": true
            }
        """.trimIndent()

        val input = ByteArrayInputStream(jsonWithNewFeature.encodeToByteArray())
        val deserialized = UserPreferencesSerializer.readFrom(input)

        assertEquals(800, deserialized.frequency)
        assertEquals(28, deserialized.wpm)
        assertEquals(20, deserialized.farnsworthWpm) // defaults gracefully
        assertTrue(deserialized.showExperimentalFeatures)
    }

    @Test(expected = CorruptionException::class)
    fun `readFrom throws CorruptionException on invalid JSON`(): Unit = runBlocking {
        val invalid = ByteArrayInputStream("not a json".encodeToByteArray())
        UserPreferencesSerializer.readFrom(invalid)
    }
}
