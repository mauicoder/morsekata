package net.maui.morselab.generator

import net.maui.morselab.data.UserPreferences

interface MorseAudioPlayer {
    fun play(text: String, preferences: UserPreferences)
    fun stop()
}
