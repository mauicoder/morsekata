package dev.mauicoder.morsekata.trainer

data class CallsignTrainerUiModel(
    val drillModeTitle: String,
    val speedText: String,
    val bestWpmText: String,
    val accuracyText: String,
    val streakText: String,
    val replayButtonText: String,
    val isSubmitEnabled: Boolean,
    val isFeedbackVisible: Boolean,
    val isCorrect: Boolean,
    val feedbackTitle: String,
    val earnedXpText: String?,
    val expectedText: String?,
    val morsePatternText: String?,
    val isAutoAdvancing: Boolean,
    val isNextButtonVisible: Boolean
)

object CallsignTrainerUiMapper {
    fun map(state: CallsignTrainerUiState): CallsignTrainerUiModel {
        val speedText = "${state.currentWpm} WPM"
        val bestWpmText = "Best: ${state.bestWpmReached} WPM"
        val accuracyText = if (state.sessionAnsweredCount > 0) {
            val pct = (state.sessionCorrectCount * 100) / state.sessionAnsweredCount
            "Accuracy: $pct% (${state.sessionCorrectCount}/${state.sessionAnsweredCount})"
        } else {
            "Accuracy: 100%"
        }
        val streakText = "Streak: ${state.comboStreak}"
        val replayButtonText = if (state.preAnswerReplayCount > 0) {
            "Replay Audio (${state.preAnswerReplayCount})"
        } else {
            "Replay Audio"
        }
        val isSubmitEnabled = state.typedInput.isNotBlank() && !state.isSubmitted
        val isFeedbackVisible = state.isSubmitted
        val feedbackTitle = when {
            !state.isSubmitted -> ""
            state.isCorrect -> "Correct!"
            else -> "Incorrect"
        }
        val earnedXpText = if (state.isSubmitted && state.isCorrect) "+${state.earnedXp} XP" else null
        val expectedText = if (state.isSubmitted && !state.isCorrect) state.currentTarget?.fullTransmissionText else null
        val morsePatternText = if (state.isSubmitted) state.currentTarget?.morsePattern else null
        val isAutoAdvancing = state.isSubmitted && state.isCorrect && state.autoAdvanceCountdownActive
        val isNextButtonVisible = state.isSubmitted && !state.isCorrect

        return CallsignTrainerUiModel(
            drillModeTitle = when (state.drillMode) {
                CallsignDrillMode.CALLSIGNS_ONLY -> "Callsigns Only"
                CallsignDrillMode.CONTEST_EXCHANGE -> "Contest Exchange"
            },
            speedText = speedText,
            bestWpmText = bestWpmText,
            accuracyText = accuracyText,
            streakText = streakText,
            replayButtonText = replayButtonText,
            isSubmitEnabled = isSubmitEnabled,
            isFeedbackVisible = isFeedbackVisible,
            isCorrect = state.isCorrect,
            feedbackTitle = feedbackTitle,
            earnedXpText = earnedXpText,
            expectedText = expectedText,
            morsePatternText = morsePatternText,
            isAutoAdvancing = isAutoAdvancing,
            isNextButtonVisible = isNextButtonVisible
        )
    }
}
