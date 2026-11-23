package dev.mauicoder.morsekata.trainer

import kotlinx.serialization.Serializable

enum class QsoEntryType {
    SINGLE_CODE,
    FULL_PHRASE
}

enum class QsoCategory(val displayName: String) {
    SINGLE_CODES("Single Codes"),
    FULL_PHRASES("Full Phrases"),
    MIXED("Mixed")
}

data class QsoEntry(
    val codeOrPhrase: String,
    val meaning: String,
    val type: QsoEntryType
)

@Serializable
data class ItemMastery(
    val attempts: Int = 0,
    val correct: Int = 0,
    val consecutiveCorrect: Int = 0
) {
    val isMastered: Boolean
        get() = consecutiveCorrect >= 3
}

@Serializable
enum class OperatorRank(
    val displayName: String,
    val minXp: Int,
    val nextRankXp: Int?
) {
    SWL("SWL (Shortwave Listener)", 0, 100),
    NOVICE("Novice", 100, 300),
    TECHNICIAN("Technician", 300, 700),
    GENERAL("General", 700, 1500),
    AMATEUR_EXTRA("Amateur Extra", 1500, null);

    companion object {
        fun fromXp(xp: Int): OperatorRank {
            val clamped = xp.coerceAtLeast(0)
            return entries.lastOrNull { clamped >= it.minXp } ?: SWL
        }

        fun progressPercent(xp: Int): Int {
            val clamped = xp.coerceAtLeast(0)
            val currentRank = fromXp(clamped)
            val nextXp = currentRank.nextRankXp ?: return 100
            val span = nextXp - currentRank.minXp
            if (span <= 0) return 100
            val earnedInRank = clamped - currentRank.minXp
            return ((earnedInRank * 100) / span).coerceIn(0, 100)
        }
    }
}

@Serializable
data class XpAward(
    val baseXp: Int,
    val streakBonusXp: Int,
    val firstCopyBonusXp: Int
) {
    val totalXpEarned: Int
        get() = baseXp + streakBonusXp + firstCopyBonusXp

    companion object {
        fun calculate(
            isCorrect: Boolean,
            newAnswerStreak: Int,
            preAnswerReplayCount: Int
        ): XpAward {
            if (!isCorrect) return XpAward(0, 0, 0)
            val baseXp = 10
            val streakBonusXp = minOf((newAnswerStreak - 1).coerceAtLeast(0) * 2, 10)
            val firstCopyBonusXp = if (preAnswerReplayCount == 0) 5 else 0
            return XpAward(baseXp, streakBonusXp, firstCopyBonusXp)
        }
    }
}

enum class QsoTrainerTab {
    PRACTICE,
    STATS
}

enum class DayActivityStatus {
    GOAL_MET,
    PRACTICED,
    MISSED
}

data class DayStreakCell(
    val epochDay: Long,
    val dayLabel: String,
    val answeredCount: Int,
    val status: DayActivityStatus,
    val isToday: Boolean
)

data class CategoryMasteryStat(
    val category: QsoCategory,
    val title: String,
    val masteredCount: Int,
    val totalCount: Int,
    val progressFraction: Float
)

data class WeakItemStat(
    val codeOrPhrase: String,
    val meaning: String,
    val morsePattern: String,
    val attempts: Int,
    val correct: Int,
    val accuracyPercent: Int,
    val consecutiveCorrect: Int
)

data class QsoStatsUiModel(
    val dailyStreak: Int,
    val todayAnsweredCount: Int,
    val dailyGoalTarget: Int,
    val todayGoalProgressFraction: Float,
    val trailingSevenDays: List<DayStreakCell>,
    val operatorRank: OperatorRank,
    val totalXp: Int,
    val rankProgressPercent: Int,
    val rankProgressFraction: Float,
    val xpToNextRankText: String,
    val currentAnswerStreak: Int,
    val bestAnswerStreak: Int,
    val lifetimeAnswered: Int,
    val lifetimeCorrect: Int,
    val lifetimeAccuracyPercent: Int,
    val singleCodesMastery: CategoryMasteryStat,
    val fullPhrasesMastery: CategoryMasteryStat,
    val recommendedCategory: QsoCategory,
    val weakItems: List<WeakItemStat>
)
