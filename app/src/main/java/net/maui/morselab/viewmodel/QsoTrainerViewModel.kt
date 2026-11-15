package net.maui.morselab.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import net.maui.morselab.trainer.QsoCatalog
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoTrainerUiState
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class QsoTrainerViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val morseAudioPlayer: MorseAudioPlayer
) : ViewModel() {

    private var random: Random = Random.Default

    internal constructor(
        userPreferencesRepository: UserPreferencesRepository,
        morseAudioPlayer: MorseAudioPlayer,
        random: Random
    ) : this(userPreferencesRepository, morseAudioPlayer) {
        this.random = random
        _uiState.value = createInitialState(QsoCategory.SINGLE_CODES)
    }

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    private val _uiState = MutableStateFlow(createInitialState(QsoCategory.SINGLE_CODES))
    val uiState: StateFlow<QsoTrainerUiState> = _uiState.asStateFlow()

    fun selectCategory(category: QsoCategory) {
        _uiState.update {
            createInitialState(category)
        }
    }

    fun selectOption(option: String) {
        _uiState.update { current ->
            if (current.selectedOption != null) {
                current
            } else {
                val correct = option == current.currentEntry.codeOrPhrase
                current.copy(
                    selectedOption = option,
                    isCorrect = correct,
                    revealedMeaning = current.currentEntry.meaning,
                    correctCount = if (correct) current.correctCount + 1 else current.correctCount,
                    totalAnswered = current.totalAnswered + 1,
                    streak = if (correct) current.streak + 1 else 0
                )
            }
        }
    }

    fun nextQuestion() {
        val nextState = _uiState.updateAndGet { current ->
            val nextEntry = QsoCatalog.randomEntry(
                category = current.selectedCategory,
                exclude = current.currentEntry,
                random = random
            )
            current.copy(
                currentEntry = nextEntry,
                options = QsoCatalog.generateOptions(target = nextEntry, random = random),
                selectedOption = null,
                isCorrect = null,
                revealedMeaning = null
            )
        }
        morseAudioPlayer.play(nextState.currentEntry.codeOrPhrase, userPreferences.value)
    }

    fun replayAudio() {
        morseAudioPlayer.play(_uiState.value.currentEntry.codeOrPhrase, userPreferences.value)
    }

    fun stopAudio() {
        morseAudioPlayer.stop()
    }

    private fun createInitialState(category: QsoCategory): QsoTrainerUiState {
        val entry = QsoCatalog.randomEntry(category = category, random = random)
        return QsoTrainerUiState(
            selectedCategory = category,
            currentEntry = entry,
            options = QsoCatalog.generateOptions(target = entry, random = random),
            selectedOption = null,
            isCorrect = null,
            revealedMeaning = null,
            correctCount = 0,
            totalAnswered = 0,
            streak = 0
        )
    }
}
