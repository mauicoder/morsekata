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
    val scoreText: String,
    val streakText: String,
    val areOptionsEnabled: Boolean,
    val isNextEnabled: Boolean,
    val feedbackText: String,
    val meaningText: String,
    val optionStates: List<OptionPresentationState>
)

object QsoTrainerUiMapper {
    fun map(state: QsoTrainerUiState): QsoTrainerUiModel {
        val isAnswered = state.selectedOption != null
        val targetCode = state.currentEntry.codeOrPhrase

        val feedbackText = when (state.isCorrect) {
            true -> "Correct!"
            false -> "Incorrect — Correct answer: $targetCode"
            null -> ""
        }

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
            scoreText = "Score: ${state.correctCount} / ${state.totalAnswered}",
            streakText = "Streak: ${state.streak}",
            areOptionsEnabled = !isAnswered,
            isNextEnabled = isAnswered,
            feedbackText = feedbackText,
            meaningText = meaningText,
            optionStates = optionStates
        )
    }
}
