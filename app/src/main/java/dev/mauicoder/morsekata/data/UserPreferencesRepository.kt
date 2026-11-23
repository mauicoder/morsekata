package dev.mauicoder.morsekata.data

import kotlinx.coroutines.flow.StateFlow

interface UserPreferencesRepository {
    val userPreferences: StateFlow<UserPreferences>

    suspend fun updateFrequency(frequency: Int)
    suspend fun updateWpm(wpm: Int)
    suspend fun updateFarnsworthWpm(farnsworthWpm: Int)
    suspend fun updateShowExperimentalFeatures(showExperimentalFeatures: Boolean)
}