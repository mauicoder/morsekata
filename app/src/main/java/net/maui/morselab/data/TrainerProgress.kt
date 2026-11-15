package net.maui.morselab.data

import kotlinx.serialization.Serializable
import net.maui.morselab.trainer.ItemMastery
import net.maui.morselab.trainer.OperatorRank
import net.maui.morselab.trainer.XpAward

data class AnswerProgressTransition(
    val updatedProgress: TrainerProgress,
    val xpAward: XpAward,
    val rankedUpTo: OperatorRank?,
    val dailyGoalJustCompleted: Boolean
)

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
) {
    fun effectiveEpochDay(todayEpochDay: Long): Long {
        return if (currentDayEpochDay != null && todayEpochDay < currentDayEpochDay) {
            currentDayEpochDay
        } else {
            todayEpochDay
        }
    }

    fun dailyAnsweredCountFor(todayEpochDay: Long): Int {
        val effectiveDay = effectiveEpochDay(todayEpochDay)
        return if (currentDayEpochDay != effectiveDay) 0 else currentDayAnsweredCount
    }

    fun activeDailyStreakFor(todayEpochDay: Long): Int {
        val effectiveDay = effectiveEpochDay(todayEpochDay)
        val isNewCalendarDay = currentDayEpochDay != effectiveDay
        return if (
            isNewCalendarDay &&
            lastGoalCompletedEpochDay != null &&
            effectiveDay - lastGoalCompletedEpochDay > 1L
        ) {
            0
        } else {
            dailyStreak
        }
    }

    fun recordAnswer(
        codeOrPhrase: String,
        isCorrect: Boolean,
        preAnswerReplayCount: Int,
        todayEpochDay: Long,
        dailyGoalTarget: Int
    ): AnswerProgressTransition {
        val newAnswerStreak = if (isCorrect) currentAnswerStreak + 1 else 0
        val newBestStreak = maxOf(bestAnswerStreak, newAnswerStreak)
        val xpAward = XpAward.calculate(
            isCorrect = isCorrect,
            newAnswerStreak = newAnswerStreak,
            preAnswerReplayCount = preAnswerReplayCount
        )

        val previousRank = OperatorRank.fromXp(totalXp)
        val newTotalXp = totalXp + xpAward.totalXpEarned
        val newRank = OperatorRank.fromXp(newTotalXp)
        val rankedUpTo = newRank.takeIf { it != previousRank }

        val previousMastery = itemMastery[codeOrPhrase] ?: ItemMastery()
        val updatedMastery = ItemMastery(
            attempts = previousMastery.attempts + 1,
            correct = if (isCorrect) previousMastery.correct + 1 else previousMastery.correct,
            consecutiveCorrect = if (isCorrect) previousMastery.consecutiveCorrect + 1 else 0
        )
        val updatedMasteryMap = itemMastery + (codeOrPhrase to updatedMastery)

        val effectiveDay = effectiveEpochDay(todayEpochDay)
        val baseDayCount = dailyAnsweredCountFor(todayEpochDay)
        val activeStreak = activeDailyStreakFor(todayEpochDay)

        val newDayAnsweredCount = baseDayCount + 1
        val dailyGoalJustCompleted =
            newDayAnsweredCount >= dailyGoalTarget && lastGoalCompletedEpochDay != effectiveDay
        val newDailyStreak = if (dailyGoalJustCompleted) {
            if (lastGoalCompletedEpochDay != null && effectiveDay - lastGoalCompletedEpochDay == 1L) {
                activeStreak + 1
            } else {
                1
            }
        } else {
            activeStreak
        }
        val newLastGoalCompletedEpochDay = if (dailyGoalJustCompleted) {
            effectiveDay
        } else {
            lastGoalCompletedEpochDay
        }

        val updatedProgress = copy(
            totalXp = newTotalXp,
            currentAnswerStreak = newAnswerStreak,
            bestAnswerStreak = newBestStreak,
            dailyStreak = newDailyStreak,
            currentDayEpochDay = effectiveDay,
            currentDayAnsweredCount = newDayAnsweredCount,
            lastGoalCompletedEpochDay = newLastGoalCompletedEpochDay,
            itemMastery = updatedMasteryMap
        )

        return AnswerProgressTransition(
            updatedProgress = updatedProgress,
            xpAward = xpAward,
            rankedUpTo = rankedUpTo,
            dailyGoalJustCompleted = dailyGoalJustCompleted
        )
    }
}
