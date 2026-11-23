package dev.mauicoder.morsekata.data

import kotlinx.serialization.Serializable
import dev.mauicoder.morsekata.trainer.ItemMastery
import dev.mauicoder.morsekata.trainer.OperatorRank
import dev.mauicoder.morsekata.trainer.XpAward

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
    val itemMastery: Map<String, ItemMastery> = emptyMap(),
    val dailyHistory: Map<Long, Int> = emptyMap(),
    val completedFullQsoCount: Int = 0
) {
    fun effectiveEpochDay(todayEpochDay: Long): Long {
        return if (currentDayEpochDay != null && todayEpochDay < currentDayEpochDay) {
            currentDayEpochDay
        } else {
            todayEpochDay
        }
    }

    fun dailyCountOn(epochDay: Long, todayEpochDay: Long): Int {
        val effectiveToday = effectiveEpochDay(todayEpochDay)
        if (epochDay > effectiveToday) return 0
        val fromHistory = dailyHistory[epochDay]
        if (fromHistory != null) return fromHistory
        return if (currentDayEpochDay == epochDay) currentDayAnsweredCount else 0
    }

    fun dailyAnsweredCountFor(todayEpochDay: Long): Int {
        val effectiveDay = effectiveEpochDay(todayEpochDay)
        return dailyCountOn(effectiveDay, todayEpochDay)
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
        val xpAward = XpAward.calculate(
            isCorrect = isCorrect,
            newAnswerStreak = newAnswerStreak,
            preAnswerReplayCount = preAnswerReplayCount
        )

        val previousMastery = itemMastery[codeOrPhrase] ?: ItemMastery()
        val updatedMastery = ItemMastery(
            attempts = previousMastery.attempts + 1,
            correct = if (isCorrect) previousMastery.correct + 1 else previousMastery.correct,
            consecutiveCorrect = if (isCorrect) previousMastery.consecutiveCorrect + 1 else 0
        )
        val updatedMasteryMap = itemMastery + (codeOrPhrase to updatedMastery)

        return applyChallengeCompletion(
            newAnswerStreak = newAnswerStreak,
            xpAward = xpAward,
            updatedMasteryMap = updatedMasteryMap,
            updatedCompletedFullQsoCount = completedFullQsoCount,
            todayEpochDay = todayEpochDay,
            dailyGoalTarget = dailyGoalTarget
        )
    }

    fun recordFullQsoStage(
        correctFieldCount: Int,
        totalFieldCount: Int,
        preStageReplayCount: Int,
        isFinalStage: Boolean,
        todayEpochDay: Long,
        dailyGoalTarget: Int
    ): AnswerProgressTransition {
        val isCleanCopy = totalFieldCount > 0 && correctFieldCount == totalFieldCount
        val newAnswerStreak = if (isCleanCopy) currentAnswerStreak + 1 else 0

        val fieldXp = correctFieldCount.coerceAtLeast(0) * 5
        val completionBonusXp = if (isFinalStage) 25 else 0
        val baseXp = fieldXp + completionBonusXp
        val streakBonusXp = if (isCleanCopy) minOf((newAnswerStreak - 1).coerceAtLeast(0) * 2, 10) else 0
        val firstCopyBonusXp = if (isCleanCopy && preStageReplayCount == 0) 5 else 0
        val xpAward = XpAward(
            baseXp = baseXp,
            streakBonusXp = streakBonusXp,
            firstCopyBonusXp = firstCopyBonusXp
        )

        val updatedCompletedCount = if (isFinalStage) completedFullQsoCount + 1 else completedFullQsoCount

        return applyChallengeCompletion(
            newAnswerStreak = newAnswerStreak,
            xpAward = xpAward,
            updatedMasteryMap = itemMastery,
            updatedCompletedFullQsoCount = updatedCompletedCount,
            todayEpochDay = todayEpochDay,
            dailyGoalTarget = dailyGoalTarget
        )
    }

    private fun applyChallengeCompletion(
        newAnswerStreak: Int,
        xpAward: XpAward,
        updatedMasteryMap: Map<String, ItemMastery>,
        updatedCompletedFullQsoCount: Int,
        todayEpochDay: Long,
        dailyGoalTarget: Int
    ): AnswerProgressTransition {
        val newBestStreak = maxOf(bestAnswerStreak, newAnswerStreak)
        val previousRank = OperatorRank.fromXp(totalXp)
        val newTotalXp = totalXp + xpAward.totalXpEarned
        val newRank = OperatorRank.fromXp(newTotalXp)
        val rankedUpTo = newRank.takeIf { it != previousRank }

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

        val minRetainedDay = effectiveDay - 13L
        val seedHistory = if (
            currentDayEpochDay != null &&
            currentDayAnsweredCount > 0 &&
            currentDayEpochDay !in dailyHistory
        ) {
            dailyHistory + (currentDayEpochDay to currentDayAnsweredCount)
        } else {
            dailyHistory
        }
        val updatedDailyHistory = (seedHistory + (effectiveDay to newDayAnsweredCount))
            .filterKeys { it in minRetainedDay..effectiveDay }

        val updatedProgress = copy(
            totalXp = newTotalXp,
            currentAnswerStreak = newAnswerStreak,
            bestAnswerStreak = newBestStreak,
            dailyStreak = newDailyStreak,
            currentDayEpochDay = effectiveDay,
            currentDayAnsweredCount = newDayAnsweredCount,
            lastGoalCompletedEpochDay = newLastGoalCompletedEpochDay,
            itemMastery = updatedMasteryMap,
            dailyHistory = updatedDailyHistory,
            completedFullQsoCount = updatedCompletedFullQsoCount
        )

        return AnswerProgressTransition(
            updatedProgress = updatedProgress,
            xpAward = xpAward,
            rankedUpTo = rankedUpTo,
            dailyGoalJustCompleted = dailyGoalJustCompleted
        )
    }
}
