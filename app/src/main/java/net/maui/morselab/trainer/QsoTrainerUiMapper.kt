package net.maui.morselab.trainer

enum class OptionHighlightState {
    DEFAULT,
    CORRECT,
    INCORRECT
}

data class OptionPresentationState(
    val text: String,
    val highlightState: OptionHighlightState
)

data class QsoTrainerUiModel(
    val rankText: String,
    val xpText: String,
    val rankProgressPercent: Int,
    val scoreText: String,
    val streakText: String,
    val dailyStreakText: String,
    val masteryText: String,
    val areOptionsEnabled: Boolean,
    val isNextEnabled: Boolean,
    val feedbackText: String,
    val morsePatternText: String,
    val meaningText: String,
    val optionStates: List<OptionPresentationState>
)

object QsoTrainerUiMapper {
    fun map(state: QsoTrainerUiState): QsoTrainerUiModel {
        val isAnswered = state.selectedOption != null
        val targetCode = state.currentEntry.codeOrPhrase

        val rankText = "Rank: ${state.operatorRank.displayName}"
        val xpText = if (state.operatorRank.nextRankXp != null) {
            "XP: ${state.totalXp} / ${state.operatorRank.nextRankXp}"
        } else {
            "XP: ${state.totalXp} (Max Rank)"
        }

        val streakText = "Streak: ${state.streak} (Best: ${state.bestStreak})"
        val dailyStreakText = "Daily: ${state.dailyStreak}d (${minOf(state.dailyAnsweredCount, state.dailyGoalTarget)}/${state.dailyGoalTarget})"
        val masteryText = "Mastered: ${state.masteredInCategoryCount} / ${state.totalInCategoryCount}"

        val feedbackText = when (state.isCorrect) {
            true -> {
                val award = state.lastXpAward ?: XpAward(10, 0, 0)
                val parts = mutableListOf("10 Base")
                if (award.streakBonusXp > 0) parts.add("${award.streakBonusXp} Streak")
                if (award.firstCopyBonusXp > 0) parts.add("${award.firstCopyBonusXp} First-Copy")
                var base = "Correct! +${award.totalXpEarned} XP (${parts.joinToString(" + ")})"
                if (state.rankedUpTo != null) {
                    base += " • Rank Up: ${state.rankedUpTo.displayName}!"
                }
                if (state.dailyGoalJustCompleted) {
                    base += " • Daily Goal Complete!"
                }
                base
            }
            false -> "Incorrect — Correct answer: $targetCode"
            null -> ""
        }

        val morsePatternText = state.revealedMorsePattern ?: ""

        val meaningText = state.revealedMeaning?.let { meaning ->
            "$targetCode — $meaning"
        } ?: ""

        val optionStates = state.options.map { option ->
            val highlight = when {
                !isAnswered -> OptionHighlightState.DEFAULT
                option == targetCode -> OptionHighlightState.CORRECT
                option == state.selectedOption -> OptionHighlightState.INCORRECT
                else -> OptionHighlightState.DEFAULT
            }
            OptionPresentationState(text = option, highlightState = highlight)
        }

        return QsoTrainerUiModel(
            rankText = rankText,
            xpText = xpText,
            rankProgressPercent = state.rankProgressPercent,
            scoreText = "Score: ${state.correctCount} / ${state.totalAnswered}",
            streakText = streakText,
            dailyStreakText = dailyStreakText,
            masteryText = masteryText,
            areOptionsEnabled = !isAnswered,
            isNextEnabled = isAnswered,
            feedbackText = feedbackText,
            morsePatternText = morsePatternText,
            meaningText = meaningText,
            optionStates = optionStates
        )
    }
}
