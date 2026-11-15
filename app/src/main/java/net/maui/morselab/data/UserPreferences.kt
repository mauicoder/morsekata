package net.maui.morselab.data

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val frequency: Int = DEFAULT_FREQUENCY,
    val wpm: Int = DEFAULT_WPM,
    val farnsworthWpm: Int = DEFAULT_WPM
) {
    companion object {
        const val DEFAULT_FREQUENCY = 700
        const val MIN_FREQUENCY = 100
        const val MAX_FREQUENCY = 1000
        const val FREQUENCY_STEP = 50

        const val DEFAULT_WPM = 20
        const val MIN_WPM = 5
        const val MAX_WPM = 50
        const val WPM_STEP = 1

        fun snapFrequency(frequency: Int): Int =
            (((frequency + (FREQUENCY_STEP / 2)) / FREQUENCY_STEP) * FREQUENCY_STEP)
                .coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)

        fun clampWpm(wpm: Int): Int = wpm.coerceIn(MIN_WPM, MAX_WPM)
    }
}