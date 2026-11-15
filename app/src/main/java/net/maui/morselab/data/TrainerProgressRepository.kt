package net.maui.morselab.data

import kotlinx.coroutines.flow.StateFlow

interface TrainerProgressRepository {
    val trainerProgress: StateFlow<TrainerProgress>

    suspend fun updateProgress(transform: (TrainerProgress) -> TrainerProgress)
}
