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
    CALLSIGNS,
    FULL_QSO,
    STATS
}

enum class OptionHighlightState {
    DEFAULT,
    CORRECT,
    INCORRECT
}

data class OptionPresentationState(
    val text: String,
    val highlightState: OptionHighlightState
)

enum class QsoStage(val stepNumber: Int, val title: String) {
    CQ_CALL(1, "Stage 1/3: Calling CQ"),
    MAIN_EXCHANGE(2, "Stage 2/3: Main Exchange"),
    FINAL_SIGN_OFF(3, "Stage 3/3: Technical & 73")
}

enum class QsoExchangeFieldType(val label: String) {
    CALLSIGN("Station Callsign"),
    RST("Signal Report (RST)"),
    QTH("Location (QTH)"),
    OP_NAME("Operator Name"),
    CONDITION("Band Condition"),
    RIG("Transceiver (Rig)"),
    POWER("Transmit Power"),
    WEATHER("Weather (WX)")
}

data class QsoFieldChallenge(
    val fieldType: QsoExchangeFieldType,
    val correctValue: String,
    val options: List<String>
)

data class QsoStageChallenge(
    val stage: QsoStage,
    val transmitText: String,
    val fields: List<QsoFieldChallenge>
)

data class FullQsoSession(
    val dxCallsign: String,
    val rst: String,
    val qth: String,
    val opName: String,
    val condition: String,
    val rig: String,
    val power: String,
    val weather: String,
    val stages: List<QsoStageChallenge>
) {
    fun stageChallenge(stage: QsoStage): QsoStageChallenge =
        stages.first { it.stage == stage }
}

data class QsoFieldPresentation(
    val fieldType: QsoExchangeFieldType,
    val label: String,
    val selectedOption: String?,
    val correctValue: String,
    val isCorrect: Boolean?,
    val options: List<OptionPresentationState>
)

data class FullQsoLogbookSummary(
    val callsign: String,
    val rst: String,
    val qth: String,
    val opName: String,
    val condition: String,
    val rig: String,
    val power: String,
    val weather: String
)

data class FullQsoUiModel(
    val stage: QsoStage,
    val stageTitle: String,
    val completedFullQsoCount: Int,
    val logbook: FullQsoLogbookSummary,
    val fields: List<QsoFieldPresentation>,
    val areFieldOptionsEnabled: Boolean,
    val canSubmitStage: Boolean,
    val isNextStageEnabled: Boolean,
    val nextActionLabel: String,
    val isStageCleanCopy: Boolean?,
    val correctFieldCount: Int,
    val totalFieldCount: Int,
    val feedbackText: String,
    val revealedTransmitText: String,
    val revealedMorsePattern: String
)

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
    val weakItems: List<WeakItemStat>,
    val completedFullQsoCount: Int = 0,
    val bestCallsignWpm: Int = 0,
    val bestCallsignWpmText: String = "—",
    val completedCallsignCount: Int = 0
)
