package dev.mauicoder.morsekata.generator

import dev.mauicoder.morsekata.data.UserPreferences

interface MorseAudioPlayer {
    fun play(text: String, preferences: UserPreferences)
    fun stop()
}
