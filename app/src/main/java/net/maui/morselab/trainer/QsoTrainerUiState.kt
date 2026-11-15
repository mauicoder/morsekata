package net.maui.morselab.trainer

data class QsoTrainerUiState(
    val selectedCategory: QsoCategory = QsoCategory.SINGLE_CODES,
    val currentEntry: QsoEntry,
    val options: List<String>,
    val selectedOption: String? = null,
    val isCorrect: Boolean? = null,
    val revealedMeaning: String? = null,
    val correctCount: Int = 0,
    val totalAnswered: Int = 0,
    val streak: Int = 0
)
