package net.maui.morselab.trainer

import kotlinx.serialization.Serializable

enum class QsoEntryType {
    SINGLE_CODE,
    FULL_PHRASE
}

enum class QsoCategory {
    SINGLE_CODES,
    FULL_PHRASES,
    MIXED
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
