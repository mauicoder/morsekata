package dev.mauicoder.morsekata.trainer

import java.time.DayOfWeek
import java.time.LocalDate

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
    val optionStates: List<OptionPresentationState>,
    val selectedTab: QsoTrainerTab,
    val stats: QsoStatsUiModel,
    val fullQso: FullQsoUiModel
)

object QsoTrainerUiMapper {
    fun tabTitle(tab: QsoTrainerTab): String = when (tab) {
        QsoTrainerTab.PRACTICE -> "Code Drill"
        QsoTrainerTab.CALLSIGNS -> "Callsigns"
        QsoTrainerTab.FULL_QSO -> "Full QSO"
        QsoTrainerTab.STATS -> "Stats"
    }

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
            optionStates = optionStates,
            selectedTab = state.selectedTab,
            stats = mapStats(state),
            fullQso = mapFullQso(state)
        )
    }

    fun mapFullQso(state: QsoTrainerUiState): FullQsoUiModel {
        val session = state.fullQsoSession
        val stage = state.currentQsoStage
        val isSubmitted = state.isQsoStageSubmitted
        val activeStage = session.stageChallenge(stage)

        val revealStage1 = stage != QsoStage.CQ_CALL || isSubmitted
        val revealStage2 = stage == QsoStage.FINAL_SIGN_OFF || (stage == QsoStage.MAIN_EXCHANGE && isSubmitted)
        val revealStage3 = stage == QsoStage.FINAL_SIGN_OFF && isSubmitted

        val logbook = FullQsoLogbookSummary(
            callsign = if (revealStage1) session.dxCallsign else "???",
            rst = if (revealStage2) session.rst else "—",
            qth = if (revealStage2) session.qth else "—",
            opName = if (revealStage2) session.opName else "—",
            condition = if (revealStage2) session.condition else "—",
            rig = if (revealStage3) session.rig else "—",
            power = if (revealStage3) session.power else "—",
            weather = if (revealStage3) session.weather else "—"
        )

        val areFieldOptionsEnabled = !isSubmitted

        val fields = activeStage.fields.map { challenge ->
            val selectedOption = state.selectedQsoFieldOptions[challenge.fieldType]
            val fieldCorrect = if (isSubmitted) selectedOption == challenge.correctValue else null
            val optionStates = challenge.options.map { optionText ->
                val highlight = when {
                    !isSubmitted -> OptionHighlightState.DEFAULT
                    optionText == challenge.correctValue -> OptionHighlightState.CORRECT
                    optionText == selectedOption -> OptionHighlightState.INCORRECT
                    else -> OptionHighlightState.DEFAULT
                }
                OptionPresentationState(text = optionText, highlightState = highlight)
            }
            QsoFieldPresentation(
                fieldType = challenge.fieldType,
                label = challenge.fieldType.label,
                selectedOption = selectedOption,
                correctValue = challenge.correctValue,
                isCorrect = fieldCorrect,
                options = optionStates,
                isFillActionEnabled = areFieldOptionsEnabled && !isSubmitted
            )
        }

        val totalFieldCount = activeStage.fields.size
        val correctFieldCount = if (isSubmitted) {
            activeStage.fields.count { state.selectedQsoFieldOptions[it.fieldType] == it.correctValue }
        } else {
            0
        }
        val isStageCleanCopy = if (isSubmitted) {
            totalFieldCount > 0 && correctFieldCount == totalFieldCount
        } else {
            null
        }

        val nextActionLabel = when (stage) {
            QsoStage.CQ_CALL -> "Next Over (Stage 2/3)"
            QsoStage.MAIN_EXCHANGE -> "Next Over (Stage 3/3)"
            QsoStage.FINAL_SIGN_OFF -> "Start New QSO Contact"
        }

        val feedbackText = if (isSubmitted) {
            val cleanCopy = isStageCleanCopy == true
            val copyHeader = if (cleanCopy) {
                "Clean Copy! ($correctFieldCount/$totalFieldCount fields)"
            } else {
                "Partial Copy ($correctFieldCount/$totalFieldCount fields)"
            }
            val defaultBaseXp = correctFieldCount * 5 + if (stage == QsoStage.FINAL_SIGN_OFF) 25 else 0
            val award = state.qsoStageXpAward ?: XpAward(defaultBaseXp, 0, 0)
            val xpParts = mutableListOf("${correctFieldCount * 5} Field")
            if (stage == QsoStage.FINAL_SIGN_OFF) {
                xpParts.add("+25 Full QSO Logged")
            }
            if (award.streakBonusXp > 0) {
                xpParts.add("${award.streakBonusXp} Streak")
            }
            if (award.firstCopyBonusXp > 0) {
                xpParts.add("${award.firstCopyBonusXp} First-Copy")
            }
            var text = "$copyHeader • +${award.totalXpEarned} XP (${xpParts.joinToString(" + ")})"
            if (state.qsoStageRankedUpTo != null) {
                text += " • Rank Up: ${state.qsoStageRankedUpTo.displayName}!"
            }
            if (state.qsoStageDailyGoalJustCompleted) {
                text += " • Daily Goal Complete!"
            }
            text
        } else {
            ""
        }

        return FullQsoUiModel(
            stage = stage,
            stageTitle = stage.title,
            completedFullQsoCount = state.completedFullQsoCount,
            logbook = logbook,
            fields = fields,
            areFieldOptionsEnabled = areFieldOptionsEnabled,
            canSubmitStage = !isSubmitted && activeStage.fields.all { it.fieldType in state.selectedQsoFieldOptions },
            isNextStageEnabled = isSubmitted,
            nextActionLabel = nextActionLabel,
            isStageCleanCopy = isStageCleanCopy,
            correctFieldCount = correctFieldCount,
            totalFieldCount = totalFieldCount,
            feedbackText = feedbackText,
            revealedTransmitText = if (isSubmitted) activeStage.transmitText else "",
            revealedMorsePattern = if (isSubmitted) QsoCatalog.formatMorsePattern(activeStage.transmitText) else ""
        )
    }

    fun mapStats(state: QsoTrainerUiState): QsoStatsUiModel {
        val todayAnsweredCount = state.dailyHistory[state.todayEpochDay] ?: state.dailyAnsweredCount
        val todayGoalProgressFraction = if (state.dailyGoalTarget > 0) {
            (todayAnsweredCount.toFloat() / state.dailyGoalTarget.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val trailingSevenDays = ((state.todayEpochDay - 6L)..state.todayEpochDay).map { day ->
            val answeredCount = state.dailyHistory[day]
                ?: if (day == state.todayEpochDay) state.dailyAnsweredCount else 0
            val status = when {
                answeredCount >= state.dailyGoalTarget -> DayActivityStatus.GOAL_MET
                answeredCount in 1 until state.dailyGoalTarget -> DayActivityStatus.PRACTICED
                else -> DayActivityStatus.MISSED
            }
            DayStreakCell(
                epochDay = day,
                dayLabel = formatDayOfWeekLabel(LocalDate.ofEpochDay(day).dayOfWeek),
                answeredCount = answeredCount,
                status = status,
                isToday = day == state.todayEpochDay
            )
        }

        val nextRankXp = state.operatorRank.nextRankXp
        val xpToNextRankText = if (nextRankXp != null) {
            val nextRank = OperatorRank.entries[state.operatorRank.ordinal + 1]
            "${(nextRankXp - state.totalXp).coerceAtLeast(0)} XP to ${nextRank.displayName}"
        } else {
            "Highest License Class Achieved"
        }

        val lifetimeAnswered = state.itemMastery.values.sumOf { it.attempts }
        val lifetimeCorrect = state.itemMastery.values.sumOf { it.correct }
        val lifetimeAccuracyPercent = if (lifetimeAnswered > 0) {
            (lifetimeCorrect * 100) / lifetimeAnswered
        } else {
            0
        }

        val singleCodesMastery = buildCategoryMastery(
            category = QsoCategory.SINGLE_CODES,
            title = QsoCategory.SINGLE_CODES.displayName,
            itemMastery = state.itemMastery
        )
        val fullPhrasesMastery = buildCategoryMastery(
            category = QsoCategory.FULL_PHRASES,
            title = QsoCategory.FULL_PHRASES.displayName,
            itemMastery = state.itemMastery
        )

        val recommendedCategory = if (fullPhrasesMastery.progressFraction < singleCodesMastery.progressFraction) {
            QsoCategory.FULL_PHRASES
        } else {
            QsoCategory.SINGLE_CODES
        }

        val weakItems = QsoCatalog.entries
            .mapNotNull { entry ->
                val mastery = state.itemMastery[entry.codeOrPhrase]
                if (mastery != null && mastery.attempts > 0 && !mastery.isMastered) {
                    WeakItemStat(
                        codeOrPhrase = entry.codeOrPhrase,
                        meaning = entry.meaning,
                        morsePattern = QsoCatalog.formatMorsePattern(entry.codeOrPhrase),
                        attempts = mastery.attempts,
                        correct = mastery.correct,
                        accuracyPercent = (mastery.correct * 100) / mastery.attempts,
                        consecutiveCorrect = mastery.consecutiveCorrect
                    )
                } else {
                    null
                }
            }
            .sortedWith(
                compareBy<WeakItemStat> { it.consecutiveCorrect }
                    .thenBy { it.accuracyPercent }
                    .thenByDescending { it.attempts }
            )
            .take(5)

        return QsoStatsUiModel(
            dailyStreak = state.dailyStreak,
            todayAnsweredCount = todayAnsweredCount,
            dailyGoalTarget = state.dailyGoalTarget,
            todayGoalProgressFraction = todayGoalProgressFraction,
            trailingSevenDays = trailingSevenDays,
            operatorRank = state.operatorRank,
            totalXp = state.totalXp,
            rankProgressPercent = state.rankProgressPercent,
            rankProgressFraction = (state.rankProgressPercent.toFloat() / 100f).coerceIn(0f, 1f),
            xpToNextRankText = xpToNextRankText,
            currentAnswerStreak = state.streak,
            bestAnswerStreak = state.bestStreak,
            lifetimeAnswered = lifetimeAnswered,
            lifetimeCorrect = lifetimeCorrect,
            lifetimeAccuracyPercent = lifetimeAccuracyPercent,
            singleCodesMastery = singleCodesMastery,
            fullPhrasesMastery = fullPhrasesMastery,
            recommendedCategory = recommendedCategory,
            weakItems = weakItems,
            completedFullQsoCount = state.completedFullQsoCount,
            bestCallsignWpm = state.bestCallsignWpm,
            bestCallsignWpmText = if (state.bestCallsignWpm > 0) "${state.bestCallsignWpm} WPM" else "—",
            completedCallsignCount = state.completedCallsignCount
        )
    }

    private fun buildCategoryMastery(
        category: QsoCategory,
        title: String,
        itemMastery: Map<String, ItemMastery>
    ): CategoryMasteryStat {
        val entries = QsoCatalog.entriesForCategory(category)
        val masteredCount = entries.count { itemMastery[it.codeOrPhrase]?.isMastered == true }
        val totalCount = entries.size
        val progressFraction = if (totalCount > 0) {
            (masteredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return CategoryMasteryStat(
            category = category,
            title = title,
            masteredCount = masteredCount,
            totalCount = totalCount,
            progressFraction = progressFraction
        )
    }

    private fun formatDayOfWeekLabel(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
        }
    }
}
