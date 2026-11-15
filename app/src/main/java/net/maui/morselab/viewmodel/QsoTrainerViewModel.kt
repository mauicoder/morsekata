package net.maui.morselab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import net.maui.morselab.data.TrainerProgress
import net.maui.morselab.data.TrainerProgressRepository
import net.maui.morselab.data.UserPreferences
import net.maui.morselab.data.UserPreferencesRepository
import net.maui.morselab.generator.MorseAudioPlayer
import net.maui.morselab.trainer.ItemMastery
import net.maui.morselab.trainer.OperatorRank
import net.maui.morselab.trainer.QsoCatalog
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoTrainerUiState
import net.maui.morselab.trainer.XpAward
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class QsoTrainerViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trainerProgressRepository: TrainerProgressRepository,
    private val morseAudioPlayer: MorseAudioPlayer,
    private val clock: Clock
) : ViewModel() {

    private var random: Random = Random.Default

    internal constructor(
        userPreferencesRepository: UserPreferencesRepository,
        trainerProgressRepository: TrainerProgressRepository,
        morseAudioPlayer: MorseAudioPlayer,
        clock: Clock = Clock.systemDefaultZone(),
        random: Random = Random.Default
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        trainerProgressRepository = trainerProgressRepository,
        morseAudioPlayer = morseAudioPlayer,
        clock = clock
    ) {
        this.random = random
        _uiState.value = createInitialState(
            category = QsoCategory.SINGLE_CODES,
            progress = trainerProgressRepository.trainerProgress.value
        )
    }

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    private val _uiState = MutableStateFlow(
        createInitialState(
            category = QsoCategory.SINGLE_CODES,
            progress = trainerProgressRepository.trainerProgress.value
        )
    )
    val uiState: StateFlow<QsoTrainerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Unconfined) {
            trainerProgressRepository.trainerProgress.collect { progress ->
                _uiState.update { current ->
                    syncStateWithProgress(current, progress)
                }
            }
        }
    }

    fun selectCategory(category: QsoCategory) {
        morseAudioPlayer.stop()
        val progress = trainerProgressRepository.trainerProgress.value
        _uiState.update {
            createInitialState(category = category, progress = progress)
        }
    }

    fun selectOption(option: String) {
        val current = _uiState.value
        if (current.selectedOption != null) return

        val progress = trainerProgressRepository.trainerProgress.value
        val targetCode = current.currentEntry.codeOrPhrase
        val correct = option == targetCode

        val newAnswerStreak = if (correct) current.streak + 1 else 0
        val newBestStreak = maxOf(current.bestStreak, newAnswerStreak)
        val xpAward = XpAward.calculate(
            isCorrect = correct,
            newAnswerStreak = newAnswerStreak,
            preAnswerReplayCount = current.replayCount
        )

        val previousRank = OperatorRank.fromXp(progress.totalXp)
        val newTotalXp = progress.totalXp + xpAward.totalXpEarned
        val newRank = OperatorRank.fromXp(newTotalXp)
        val newRankProgressPercent = OperatorRank.progressPercent(newTotalXp)
        val rankedUpTo = newRank.takeIf { it != previousRank }

        val previousMastery = progress.itemMastery[targetCode] ?: ItemMastery()
        val updatedMastery = ItemMastery(
            attempts = previousMastery.attempts + 1,
            correct = if (correct) previousMastery.correct + 1 else previousMastery.correct,
            consecutiveCorrect = if (correct) previousMastery.consecutiveCorrect + 1 else 0
        )
        val updatedMasteryMap = progress.itemMastery + (targetCode to updatedMastery)

        val effectiveDay = effectiveEpochDay(progress)
        val (baseDayCount, activeDailyStreak) = resolveDailyBaseline(progress, effectiveDay)
        val newDayAnsweredCount = baseDayCount + 1
        val dailyGoalJustCompleted =
            newDayAnsweredCount >= current.dailyGoalTarget && progress.lastGoalCompletedEpochDay != effectiveDay
        val newDailyStreak = if (dailyGoalJustCompleted) {
            if (progress.lastGoalCompletedEpochDay != null && effectiveDay - progress.lastGoalCompletedEpochDay == 1L) {
                activeDailyStreak + 1
            } else {
                1
            }
        } else {
            activeDailyStreak
        }
        val newLastGoalCompletedEpochDay = if (dailyGoalJustCompleted) {
            effectiveDay
        } else {
            progress.lastGoalCompletedEpochDay
        }

        val updatedProgress = progress.copy(
            totalXp = newTotalXp,
            currentAnswerStreak = newAnswerStreak,
            bestAnswerStreak = newBestStreak,
            dailyStreak = newDailyStreak,
            currentDayEpochDay = effectiveDay,
            currentDayAnsweredCount = newDayAnsweredCount,
            lastGoalCompletedEpochDay = newLastGoalCompletedEpochDay,
            itemMastery = updatedMasteryMap
        )

        val (masteredInCategoryCount, totalInCategoryCount) =
            categoryMasteryCounts(current.selectedCategory, updatedMasteryMap)

        _uiState.update { state ->
            if (state.selectedOption != null) {
                state
            } else {
                state.copy(
                    selectedOption = option,
                    isCorrect = correct,
                    revealedMeaning = state.currentEntry.meaning,
                    revealedMorsePattern = QsoCatalog.formatMorsePattern(targetCode),
                    lastXpAward = xpAward,
                    rankedUpTo = rankedUpTo,
                    dailyGoalJustCompleted = dailyGoalJustCompleted,
                    correctCount = if (correct) state.correctCount + 1 else state.correctCount,
                    totalAnswered = state.totalAnswered + 1,
                    streak = newAnswerStreak,
                    bestStreak = newBestStreak,
                    totalXp = newTotalXp,
                    operatorRank = newRank,
                    rankProgressPercent = newRankProgressPercent,
                    dailyStreak = newDailyStreak,
                    dailyAnsweredCount = newDayAnsweredCount,
                    masteredInCategoryCount = masteredInCategoryCount,
                    totalInCategoryCount = totalInCategoryCount
                )
            }
        }

        viewModelScope.launch(Dispatchers.Unconfined) {
            trainerProgressRepository.updateProgress { updatedProgress }
        }
    }

    fun nextQuestion() {
        val masteryMap = trainerProgressRepository.trainerProgress.value.itemMastery
        val nextState = _uiState.updateAndGet { current ->
            val nextEntry = QsoCatalog.selectAdaptiveEntry(
                category = current.selectedCategory,
                masteryMap = masteryMap,
                exclude = current.currentEntry,
                random = random
            )
            current.copy(
                currentEntry = nextEntry,
                options = QsoCatalog.generateOptions(target = nextEntry, random = random),
                selectedOption = null,
                isCorrect = null,
                revealedMeaning = null,
                revealedMorsePattern = null,
                replayCount = 0,
                lastXpAward = null,
                rankedUpTo = null,
                dailyGoalJustCompleted = false
            )
        }
        morseAudioPlayer.play(nextState.currentEntry.codeOrPhrase, userPreferences.value)
    }

    fun replayAudio() {
        _uiState.update { current ->
            if (current.selectedOption == null) {
                current.copy(replayCount = current.replayCount + 1)
            } else {
                current
            }
        }
        morseAudioPlayer.play(_uiState.value.currentEntry.codeOrPhrase, userPreferences.value)
    }

    fun stopAudio() {
        morseAudioPlayer.stop()
    }

    private fun createInitialState(
        category: QsoCategory,
        progress: TrainerProgress
    ): QsoTrainerUiState {
        val entry = QsoCatalog.selectAdaptiveEntry(
            category = category,
            masteryMap = progress.itemMastery,
            random = random
        )
        val baseState = QsoTrainerUiState(
            selectedCategory = category,
            currentEntry = entry,
            options = QsoCatalog.generateOptions(target = entry, random = random),
            dailyGoalTarget = DAILY_GOAL_TARGET
        )
        return syncStateWithProgress(baseState, progress)
    }

    private fun syncStateWithProgress(
        current: QsoTrainerUiState,
        progress: TrainerProgress
    ): QsoTrainerUiState {
        val (masteredInCategoryCount, totalInCategoryCount) =
            categoryMasteryCounts(current.selectedCategory, progress.itemMastery)
        val effectiveDay = effectiveEpochDay(progress)
        val (dailyAnsweredCount, dailyStreak) = resolveDailyBaseline(progress, effectiveDay)

        return current.copy(
            streak = progress.currentAnswerStreak,
            bestStreak = progress.bestAnswerStreak,
            totalXp = progress.totalXp,
            operatorRank = OperatorRank.fromXp(progress.totalXp),
            rankProgressPercent = OperatorRank.progressPercent(progress.totalXp),
            dailyStreak = dailyStreak,
            dailyAnsweredCount = dailyAnsweredCount,
            masteredInCategoryCount = masteredInCategoryCount,
            totalInCategoryCount = totalInCategoryCount
        )
    }

    private fun effectiveEpochDay(progress: TrainerProgress): Long {
        val todayEpochDay = LocalDate.now(clock).toEpochDay()
        return if (progress.currentDayEpochDay != null && todayEpochDay < progress.currentDayEpochDay) {
            progress.currentDayEpochDay
        } else {
            todayEpochDay
        }
    }

    private fun resolveDailyBaseline(
        progress: TrainerProgress,
        effectiveDay: Long
    ): Pair<Int, Int> {
        val isNewCalendarDay = progress.currentDayEpochDay != effectiveDay
        val dailyAnsweredCount = if (isNewCalendarDay) 0 else progress.currentDayAnsweredCount
        val dailyStreak = if (
            isNewCalendarDay &&
            progress.lastGoalCompletedEpochDay != null &&
            effectiveDay - progress.lastGoalCompletedEpochDay > 1L
        ) {
            0
        } else {
            progress.dailyStreak
        }
        return dailyAnsweredCount to dailyStreak
    }

    private fun categoryMasteryCounts(
        category: QsoCategory,
        masteryMap: Map<String, ItemMastery>
    ): Pair<Int, Int> {
        val categoryEntries = QsoCatalog.entriesForCategory(category)
        val masteredCount = categoryEntries.count { item ->
            masteryMap[item.codeOrPhrase]?.isMastered == true
        }
        return masteredCount to categoryEntries.size
    }

    companion object {
        private const val DAILY_GOAL_TARGET = 5
    }
}
