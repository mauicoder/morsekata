package dev.mauicoder.morsekata.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import dev.mauicoder.morsekata.data.TrainerProgress
import dev.mauicoder.morsekata.data.TrainerProgressRepository
import dev.mauicoder.morsekata.data.UserPreferences
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.generator.MorseAudioPlayer
import dev.mauicoder.morsekata.trainer.CallsignDiffCalculator
import dev.mauicoder.morsekata.trainer.CallsignDiffResult
import dev.mauicoder.morsekata.trainer.CallsignDrillMode
import dev.mauicoder.morsekata.trainer.CallsignGenerator
import dev.mauicoder.morsekata.trainer.CallsignSpeedMode
import dev.mauicoder.morsekata.trainer.CallsignTarget
import dev.mauicoder.morsekata.trainer.CallsignTrainerUiState
import dev.mauicoder.morsekata.trainer.OperatorRank
import dev.mauicoder.morsekata.trainer.QsoCatalog
import dev.mauicoder.morsekata.trainer.QsoCategory
import dev.mauicoder.morsekata.trainer.QsoExchangeFieldType
import dev.mauicoder.morsekata.trainer.QsoExchangeGenerator
import dev.mauicoder.morsekata.trainer.QsoStage
import dev.mauicoder.morsekata.trainer.QsoTrainerTab
import dev.mauicoder.morsekata.trainer.QsoTrainerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random

@HiltViewModel
class QsoTrainerViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trainerProgressRepository: TrainerProgressRepository,
    private val morseAudioPlayer: MorseAudioPlayer,
    private val clock: Clock
) : ViewModel() {

    private var random: Random = Random.Default
    private var coroutineDispatcher: CoroutineDispatcher? = null
    private var autoAdvanceDelayMs: Long = 1200L
    private var autoAdvanceJob: Job? = null

    internal constructor(
        userPreferencesRepository: UserPreferencesRepository,
        trainerProgressRepository: TrainerProgressRepository,
        morseAudioPlayer: MorseAudioPlayer,
        clock: Clock = Clock.systemDefaultZone(),
        random: Random = Random.Default,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        autoAdvanceDelayMs: Long = 1200L
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        trainerProgressRepository = trainerProgressRepository,
        morseAudioPlayer = morseAudioPlayer,
        clock = clock
    ) {
        this.random = random
        this.coroutineDispatcher = dispatcher
        this.autoAdvanceDelayMs = autoAdvanceDelayMs
        _uiState.value = createInitialState(
            category = QsoCategory.SINGLE_CODES,
            progress = trainerProgressRepository.trainerProgress.value
        )
        val initialProgress = trainerProgressRepository.trainerProgress.value
        _callsignUiState.value = CallsignTrainerUiState(
            currentWpm = userPreferencesRepository.userPreferences.value.wpm,
            bestWpmReached = initialProgress.bestCallsignWpm,
            comboStreak = initialProgress.currentAnswerStreak
        )
        observeTrainerProgress()
    }

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    private val _uiState = MutableStateFlow(
        createInitialState(
            category = QsoCategory.SINGLE_CODES,
            progress = trainerProgressRepository.trainerProgress.value
        )
    )
    val uiState: StateFlow<QsoTrainerUiState> = _uiState.asStateFlow()

    private val _callsignUiState = MutableStateFlow(
        CallsignTrainerUiState(
            currentWpm = userPreferencesRepository.userPreferences.value.wpm,
            bestWpmReached = trainerProgressRepository.trainerProgress.value.bestCallsignWpm,
            comboStreak = trainerProgressRepository.trainerProgress.value.currentAnswerStreak
        )
    )
    val callsignUiState: StateFlow<CallsignTrainerUiState> = _callsignUiState.asStateFlow()

    private var progressObserverStarted = false

    init {
        try {
            observeTrainerProgress()
        } catch (_: IllegalStateException) {
            // In JVM unit tests without Dispatchers.Main initialized, the internal constructor
            // sets coroutineDispatcher = Dispatchers.Unconfined and starts observation.
        }
    }

    private fun observeTrainerProgress() {
        if (progressObserverStarted) return
        val context = coroutineDispatcher ?: EmptyCoroutineContext
        viewModelScope.launch(context) {
            progressObserverStarted = true
            trainerProgressRepository.trainerProgress.collect { progress ->
                _uiState.update { current ->
                    syncStateWithProgress(current, progress)
                }
                _callsignUiState.update { current ->
                    current.copy(
                        bestWpmReached = maxOf(current.bestWpmReached, progress.bestCallsignWpm),
                        comboStreak = progress.currentAnswerStreak
                    )
                }
            }
        }
    }

    fun selectCategory(category: QsoCategory) {
        morseAudioPlayer.stop()
        val progress = trainerProgressRepository.trainerProgress.value
        _uiState.update { current ->
            createInitialState(
                category = category,
                progress = progress,
                selectedTab = current.selectedTab
            )
        }
    }

    fun selectTab(tab: QsoTrainerTab) {
        _uiState.update { current ->
            current.copy(selectedTab = tab)
        }
        if (tab == QsoTrainerTab.CALLSIGNS) {
            if (_callsignUiState.value.currentTarget == null) {
                onNextCallsign()
            }
        }
    }

    fun practiceWeakestCategory(category: QsoCategory) {
        selectCategory(category)
        _uiState.update { current ->
            current.copy(selectedTab = QsoTrainerTab.PRACTICE)
        }
    }

    fun previewItemAudio(codeOrPhrase: String) {
        morseAudioPlayer.play(codeOrPhrase, userPreferences.value)
    }

    fun resetProgress() {
        morseAudioPlayer.stop()
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        val defaultProgress = TrainerProgress()
        _uiState.update { current ->
            createInitialState(
                category = current.selectedCategory,
                progress = defaultProgress,
                selectedTab = current.selectedTab
            )
        }
        _callsignUiState.update { current ->
            current.copy(
                bestWpmReached = 0,
                sessionAnsweredCount = 0,
                sessionCorrectCount = 0,
                currentWpm = userPreferences.value.wpm,
                typedInput = "",
                isSubmitted = false,
                isCorrect = false,
                diffResult = null,
                preAnswerReplayCount = 0,
                autoAdvanceCountdownActive = false,
                earnedXp = 0
            )
        }
        val context = coroutineDispatcher ?: EmptyCoroutineContext
        viewModelScope.launch(context) {
            trainerProgressRepository.updateProgress { defaultProgress }
        }
    }

    fun onTypeCallsignInput(text: String) {
        if (_callsignUiState.value.isSubmitted) return
        _callsignUiState.update { it.copy(typedInput = text.uppercase()) }
    }

    fun onSubmitCallsignInput() {
        val current = _callsignUiState.value
        val target = current.currentTarget ?: return
        if (current.isSubmitted) return

        val typed = current.typedInput.trim()
        val expected = target.fullTransmissionText.trim()
        val isCorrect = typed.equals(expected, ignoreCase = true)
        val todayEpochDay = LocalDate.now(clock).toEpochDay()
        val currentProgress = trainerProgressRepository.trainerProgress.value

        val isContest = current.drillMode == CallsignDrillMode.CONTEST_EXCHANGE
        val diff = CallsignDiffCalculator.calculate(typed, expected)

        if (isCorrect) {
            val newWpm = if (current.speedMode == CallsignSpeedMode.ADAPTIVE && current.preAnswerReplayCount == 0) {
                minOf(current.currentWpm + 1, 50)
            } else {
                current.currentWpm
            }
            val newBestWpm = maxOf(current.bestWpmReached, newWpm)

            val transition = currentProgress.recordCallsignAnswer(
                isCorrect = true,
                isContestExchange = isContest,
                preAnswerReplayCount = current.preAnswerReplayCount,
                achievedWpm = newWpm,
                todayEpochDay = todayEpochDay,
                dailyGoalTarget = _uiState.value.dailyGoalTarget
            )

            _callsignUiState.update {
                it.copy(
                    isSubmitted = true,
                    isCorrect = true,
                    diffResult = diff,
                    earnedXp = transition.xpAward.totalXpEarned,
                    comboStreak = transition.updatedProgress.currentAnswerStreak,
                    currentWpm = newWpm,
                    bestWpmReached = newBestWpm,
                    sessionAnsweredCount = it.sessionAnsweredCount + 1,
                    sessionCorrectCount = it.sessionCorrectCount + 1,
                    autoAdvanceCountdownActive = true
                )
            }

            val context = coroutineDispatcher ?: EmptyCoroutineContext
            viewModelScope.launch(context) {
                trainerProgressRepository.updateProgress { transition.updatedProgress }
            }

            autoAdvanceJob?.cancel()
            autoAdvanceJob = viewModelScope.launch(context) {
                if (autoAdvanceDelayMs > 0L) {
                    delay(autoAdvanceDelayMs)
                }
                onNextCallsign()
            }
        } else {
            val newWpm = if (current.speedMode == CallsignSpeedMode.ADAPTIVE) {
                maxOf(current.currentWpm - 1, 5)
            } else {
                current.currentWpm
            }

            val transition = currentProgress.recordCallsignAnswer(
                isCorrect = false,
                isContestExchange = isContest,
                preAnswerReplayCount = current.preAnswerReplayCount,
                achievedWpm = newWpm,
                todayEpochDay = todayEpochDay,
                dailyGoalTarget = _uiState.value.dailyGoalTarget
            )

            _callsignUiState.update {
                it.copy(
                    isSubmitted = true,
                    isCorrect = false,
                    diffResult = diff,
                    earnedXp = 0,
                    comboStreak = transition.updatedProgress.currentAnswerStreak,
                    currentWpm = newWpm,
                    sessionAnsweredCount = it.sessionAnsweredCount + 1,
                    autoAdvanceCountdownActive = false
                )
            }

            val context = coroutineDispatcher ?: EmptyCoroutineContext
            viewModelScope.launch(context) {
                trainerProgressRepository.updateProgress { transition.updatedProgress }
            }
        }
    }

    fun onReplayCallsignAudio() {
        val current = _callsignUiState.value
        val target = current.currentTarget ?: return
        if (!current.isSubmitted) {
            _callsignUiState.update { it.copy(preAnswerReplayCount = it.preAnswerReplayCount + 1) }
        }
        playCallsignAudio(target.fullTransmissionText, _callsignUiState.value.currentWpm)
    }

    fun onNextCallsign() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null

        val current = _callsignUiState.value
        val newTarget = CallsignGenerator.generateTarget(
            mode = current.drillMode,
            useCutNumbers = current.useCutNumbers,
            random = random
        )

        _callsignUiState.update {
            it.copy(
                currentTarget = newTarget,
                typedInput = "",
                isSubmitted = false,
                isCorrect = false,
                diffResult = null,
                preAnswerReplayCount = 0,
                autoAdvanceCountdownActive = false,
                earnedXp = 0,
                isPlayingAudio = false
            )
        }

        playCallsignAudio(newTarget.fullTransmissionText, _callsignUiState.value.currentWpm)
    }

    fun onSelectCallsignDrillMode(mode: CallsignDrillMode) {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        _callsignUiState.update { it.copy(drillMode = mode) }
        onNextCallsign()
    }

    fun onSelectCallsignSpeedMode(mode: CallsignSpeedMode) {
        _callsignUiState.update { current ->
            val wpm = if (mode == CallsignSpeedMode.FIXED) {
                userPreferences.value.wpm
            } else {
                current.currentWpm
            }
            current.copy(speedMode = mode, currentWpm = wpm)
        }
    }

    fun onToggleCallsignCutNumbers(enabled: Boolean) {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        _callsignUiState.update { it.copy(useCutNumbers = enabled) }
        if (_callsignUiState.value.drillMode == CallsignDrillMode.CONTEST_EXCHANGE) {
            onNextCallsign()
        }
    }

    private fun playCallsignAudio(text: String, wpm: Int) {
        morseAudioPlayer.stop()
        morseAudioPlayer.play(
            text,
            userPreferences.value.copy(
                wpm = wpm,
                farnsworthWpm = minOf(userPreferences.value.farnsworthWpm, wpm)
            )
        )
    }

    fun selectOption(option: String) {
        val current = _uiState.value
        if (current.selectedOption != null) return

        val progress = trainerProgressRepository.trainerProgress.value
        val targetCode = current.currentEntry.codeOrPhrase
        val correct = option == targetCode
        val todayEpochDay = LocalDate.now(clock).toEpochDay()

        val transition = progress.recordAnswer(
            codeOrPhrase = targetCode,
            isCorrect = correct,
            preAnswerReplayCount = current.replayCount,
            todayEpochDay = todayEpochDay,
            dailyGoalTarget = current.dailyGoalTarget
        )

        _uiState.update { state ->
            if (state.selectedOption != null) {
                state
            } else {
                val roundUpdated = state.copy(
                    selectedOption = option,
                    isCorrect = correct,
                    revealedMeaning = state.currentEntry.meaning,
                    revealedMorsePattern = QsoCatalog.formatMorsePattern(targetCode),
                    lastXpAward = transition.xpAward,
                    rankedUpTo = transition.rankedUpTo,
                    dailyGoalJustCompleted = transition.dailyGoalJustCompleted,
                    correctCount = if (correct) state.correctCount + 1 else state.correctCount,
                    totalAnswered = state.totalAnswered + 1
                )
                syncStateWithProgress(roundUpdated, transition.updatedProgress)
            }
        }

        val context = coroutineDispatcher ?: EmptyCoroutineContext
        viewModelScope.launch(context) {
            trainerProgressRepository.updateProgress { transition.updatedProgress }
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

    fun selectQsoFieldOption(fieldType: QsoExchangeFieldType, option: String) {
        _uiState.update { current ->
            val stageFields = current.fullQsoSession.stageChallenge(current.currentQsoStage).fields
            if (current.isQsoStageSubmitted || stageFields.none { it.fieldType == fieldType }) {
                current
            } else {
                current.copy(
                    selectedQsoFieldOptions = current.selectedQsoFieldOptions + (fieldType to option)
                )
            }
        }
    }

    fun submitQsoStage() {
        val current = _uiState.value
        if (current.isQsoStageSubmitted) return

        val activeStage = current.fullQsoSession.stageChallenge(current.currentQsoStage)
        if (!activeStage.fields.all { it.fieldType in current.selectedQsoFieldOptions }) return

        val correctFieldCount = activeStage.fields.count {
            current.selectedQsoFieldOptions[it.fieldType] == it.correctValue
        }
        val totalFieldCount = activeStage.fields.size
        val isFinalStage = current.currentQsoStage == QsoStage.FINAL_SIGN_OFF
        val todayEpochDay = LocalDate.now(clock).toEpochDay()
        val progress = trainerProgressRepository.trainerProgress.value

        val transition = progress.recordFullQsoStage(
            correctFieldCount = correctFieldCount,
            totalFieldCount = totalFieldCount,
            preStageReplayCount = current.qsoStageReplayCount,
            isFinalStage = isFinalStage,
            todayEpochDay = todayEpochDay,
            dailyGoalTarget = current.dailyGoalTarget
        )

        _uiState.update { state ->
            if (state.isQsoStageSubmitted) {
                state
            } else {
                val stageUpdated = state.copy(
                    isQsoStageSubmitted = true,
                    qsoStageXpAward = transition.xpAward,
                    qsoStageRankedUpTo = transition.rankedUpTo,
                    qsoStageDailyGoalJustCompleted = transition.dailyGoalJustCompleted
                )
                syncStateWithProgress(stageUpdated, transition.updatedProgress)
            }
        }

        val context = coroutineDispatcher ?: EmptyCoroutineContext
        viewModelScope.launch(context) {
            trainerProgressRepository.updateProgress { transition.updatedProgress }
        }
    }

    fun nextQsoStage() {
        if (!_uiState.value.isQsoStageSubmitted) return

        val nextState = _uiState.updateAndGet { current ->
            if (!current.isQsoStageSubmitted) {
                current
            } else {
                val (nextSession, nextStage) = when (current.currentQsoStage) {
                    QsoStage.CQ_CALL -> current.fullQsoSession to QsoStage.MAIN_EXCHANGE
                    QsoStage.MAIN_EXCHANGE -> current.fullQsoSession to QsoStage.FINAL_SIGN_OFF
                    QsoStage.FINAL_SIGN_OFF -> QsoExchangeGenerator.generateSession(
                        random = random,
                        excludeCallsign = current.fullQsoSession.dxCallsign
                    ) to QsoStage.CQ_CALL
                }
                current.copy(
                    fullQsoSession = nextSession,
                    currentQsoStage = nextStage,
                    selectedQsoFieldOptions = emptyMap(),
                    isQsoStageSubmitted = false,
                    qsoStageReplayCount = 0,
                    qsoStageXpAward = null,
                    qsoStageRankedUpTo = null,
                    qsoStageDailyGoalJustCompleted = false
                )
            }
        }
        val transmitText = nextState.fullQsoSession.stageChallenge(nextState.currentQsoStage).transmitText
        morseAudioPlayer.play(transmitText, userPreferences.value)
    }

    fun replayQsoStageAudio() {
        val state = _uiState.updateAndGet { current ->
            if (!current.isQsoStageSubmitted) {
                current.copy(qsoStageReplayCount = current.qsoStageReplayCount + 1)
            } else {
                current
            }
        }
        val transmitText = state.fullQsoSession.stageChallenge(state.currentQsoStage).transmitText
        morseAudioPlayer.play(transmitText, userPreferences.value)
    }

    private fun createInitialState(
        category: QsoCategory,
        progress: TrainerProgress,
        selectedTab: QsoTrainerTab = QsoTrainerTab.PRACTICE
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
            dailyGoalTarget = DAILY_GOAL_TARGET,
            selectedTab = selectedTab,
            fullQsoSession = QsoExchangeGenerator.generateSession(random = random)
        )
        return syncStateWithProgress(baseState, progress)
    }

    private fun syncStateWithProgress(
        current: QsoTrainerUiState,
        progress: TrainerProgress
    ): QsoTrainerUiState {
        val categoryEntries = QsoCatalog.entriesForCategory(current.selectedCategory)
        val masteredInCategoryCount = categoryEntries.count { item ->
            progress.itemMastery[item.codeOrPhrase]?.isMastered == true
        }
        val todayEpochDay = LocalDate.now(clock).toEpochDay()
        val effectiveTodayEpochDay = progress.effectiveEpochDay(todayEpochDay)

        return current.copy(
            streak = progress.currentAnswerStreak,
            bestStreak = progress.bestAnswerStreak,
            totalXp = progress.totalXp,
            operatorRank = OperatorRank.fromXp(progress.totalXp),
            rankProgressPercent = OperatorRank.progressPercent(progress.totalXp),
            dailyStreak = progress.activeDailyStreakFor(todayEpochDay),
            dailyAnsweredCount = progress.dailyAnsweredCountFor(todayEpochDay),
            masteredInCategoryCount = masteredInCategoryCount,
            totalInCategoryCount = categoryEntries.size,
            todayEpochDay = effectiveTodayEpochDay,
            dailyHistory = progress.dailyHistory,
            itemMastery = progress.itemMastery,
            completedFullQsoCount = progress.completedFullQsoCount,
            bestCallsignWpm = progress.bestCallsignWpm,
            completedCallsignCount = progress.completedCallsignCount
        )
    }

    companion object {
        private const val DAILY_GOAL_TARGET = 5
    }
}
