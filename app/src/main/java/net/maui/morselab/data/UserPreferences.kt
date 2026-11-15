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
    }
}