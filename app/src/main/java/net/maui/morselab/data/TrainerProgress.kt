package net.maui.morselab.data

import kotlinx.serialization.Serializable
import net.maui.morselab.trainer.ItemMastery

@Serializable
data class TrainerProgress(
    val totalXp: Int = 0,
    val currentAnswerStreak: Int = 0,
    val bestAnswerStreak: Int = 0,
    val dailyStreak: Int = 0,
    val currentDayEpochDay: Long? = null,
    val currentDayAnsweredCount: Int = 0,
    val lastGoalCompletedEpochDay: Long? = null,
    val itemMastery: Map<String, ItemMastery> = emptyMap()
)
