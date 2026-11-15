package net.maui.morselab.ui.trainer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.maui.morselab.trainer.CategoryMasteryStat
import net.maui.morselab.trainer.DayActivityStatus
import net.maui.morselab.trainer.DayStreakCell
import net.maui.morselab.trainer.OperatorRank
import net.maui.morselab.trainer.OptionHighlightState
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoStatsUiModel
import net.maui.morselab.trainer.QsoTrainerTab
import net.maui.morselab.trainer.QsoTrainerUiMapper
import net.maui.morselab.trainer.QsoTrainerUiModel
import net.maui.morselab.trainer.QsoTrainerUiState
import net.maui.morselab.trainer.WeakItemStat

private val CorrectGreen = Color(0xFF2E7D32)
private val IncorrectRed = Color(0xFFC62828)
private val PracticedAmber = Color(0xFFF57C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QsoTrainerScreen(
    state: QsoTrainerUiState,
    onSelectTab: (QsoTrainerTab) -> Unit,
    onSelectCategory: (QsoCategory) -> Unit,
    onSelectOption: (String) -> Unit,
    onReplayAudio: () -> Unit,
    onNextQuestion: () -> Unit,
    onPracticeCategory: (QsoCategory) -> Unit,
    onPreviewAudio: (String) -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiModel = remember(state) { QsoTrainerUiMapper.map(state) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = uiModel.selectedTab.ordinal) {
                Tab(
                    selected = uiModel.selectedTab == QsoTrainerTab.PRACTICE,
                    onClick = { onSelectTab(QsoTrainerTab.PRACTICE) },
                    text = { Text("Practice") }
                )
                Tab(
                    selected = uiModel.selectedTab == QsoTrainerTab.STATS,
                    onClick = { onSelectTab(QsoTrainerTab.STATS) },
                    text = { Text("Stats & Streaks") }
                )
            }

            when (uiModel.selectedTab) {
                QsoTrainerTab.PRACTICE -> PracticeTabContent(
                    selectedCategory = state.selectedCategory,
                    uiModel = uiModel,
                    isCorrect = state.isCorrect,
                    onSelectCategory = onSelectCategory,
                    onSelectOption = onSelectOption,
                    onReplayAudio = onReplayAudio,
                    onNextQuestion = onNextQuestion
                )
                QsoTrainerTab.STATS -> StatsAndStreaksTabContent(
                    stats = uiModel.stats,
                    onPracticeCategory = onPracticeCategory,
                    onPreviewAudio = onPreviewAudio,
                    onResetProgress = onResetProgress
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeTabContent(
    selectedCategory: QsoCategory,
    uiModel: QsoTrainerUiModel,
    isCorrect: Boolean?,
    onSelectCategory: (QsoCategory) -> Unit,
    onSelectOption: (String) -> Unit,
    onReplayAudio: () -> Unit,
    onNextQuestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val categories = QsoCategory.entries

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            categories.forEachIndexed { index, category ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
                    onClick = {
                        if (selectedCategory != category) {
                            onSelectCategory(category)
                        }
                    },
                    selected = selectedCategory == category
                ) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiModel.rankText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiModel.xpText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { uiModel.stats.rankProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiModel.scoreText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = uiModel.streakText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiModel.dailyStreakText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiModel.masteryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = onReplayAudio,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Replay Audio")
        }

        if (uiModel.morsePatternText.isNotEmpty() || uiModel.meaningText.isNotEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiModel.morsePatternText.isNotEmpty()) {
                        Text(
                            text = uiModel.morsePatternText,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (uiModel.meaningText.isNotEmpty()) {
                        Text(
                            text = uiModel.meaningText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiModel.optionStates.forEach { optionState ->
                val highlightColor = when (optionState.highlightState) {
                    OptionHighlightState.CORRECT -> CorrectGreen
                    OptionHighlightState.INCORRECT -> IncorrectRed
                    OptionHighlightState.DEFAULT -> null
                }

                val buttonColors = if (highlightColor != null) {
                    ButtonDefaults.buttonColors(
                        containerColor = highlightColor,
                        contentColor = Color.White,
                        disabledContainerColor = highlightColor,
                        disabledContentColor = Color.White
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onSelectOption(optionState.text)
                    },
                    enabled = uiModel.areOptionsEnabled,
                    colors = buttonColors,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = optionState.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (uiModel.feedbackText.isNotEmpty()) {
            val bannerColor = when (isCorrect) {
                true -> CorrectGreen.copy(alpha = 0.14f)
                false -> IncorrectRed.copy(alpha = 0.14f)
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
            val bannerTextColor = when (isCorrect) {
                true -> CorrectGreen
                false -> IncorrectRed
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = bannerColor
            ) {
                Text(
                    text = uiModel.feedbackText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = bannerTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedButton(
            onClick = onNextQuestion,
            enabled = uiModel.isNextEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next Question")
        }
    }
}

@Composable
fun StatsAndStreaksTabContent(
    stats: QsoStatsUiModel,
    onPracticeCategory: (QsoCategory) -> Unit,
    onPreviewAudio: (String) -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DailyStreakAndSevenDayCard(stats = stats)
        OperatorRankProgressionCard(stats = stats)
        LifetimePerformanceCard(stats = stats)
        MasteryAndWeakItemsCard(
            stats = stats,
            onPracticeCategory = onPracticeCategory,
            onPreviewAudio = onPreviewAudio
        )

        OutlinedButton(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Progress")
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Trainer Progress?") },
            text = {
                Text(
                    "This will permanently clear your XP, operator rank, answer streaks, daily practice history, and QSO item mastery."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetProgress()
                    }
                ) {
                    Text(
                        text = "Reset",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DailyStreakAndSevenDayCard(stats: QsoStatsUiModel) {
    val cappedToday = minOf(stats.todayAnsweredCount, stats.dailyGoalTarget)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Practice Streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Today's Goal: $cappedToday / ${stats.dailyGoalTarget} questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { stats.todayGoalProgressFraction },
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 4.dp
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${stats.dailyStreak}d",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { stats.todayGoalProgressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stats.trailingSevenDays.forEach { cell ->
                    DayStreakCellItem(cell = cell)
                }
            }
        }
    }
}

@Composable
private fun DayStreakCellItem(cell: DayStreakCell) {
    val badgeColor = when (cell.status) {
        DayActivityStatus.GOAL_MET -> CorrectGreen
        DayActivityStatus.PRACTICED -> PracticedAmber
        DayActivityStatus.MISSED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeTextColor = when (cell.status) {
        DayActivityStatus.GOAL_MET, DayActivityStatus.PRACTICED -> Color.White
        DayActivityStatus.MISSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusSymbol = when (cell.status) {
        DayActivityStatus.GOAL_MET -> "✓"
        DayActivityStatus.PRACTICED -> "${cell.answeredCount}"
        DayActivityStatus.MISSED -> "0"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = cell.dayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (cell.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .then(
                    if (cell.isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = statusSymbol,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor
            )
        }
        Text(
            text = "${cell.answeredCount}q",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OperatorRankProgressionCard(stats: QsoStatsUiModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Operator License Class",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stats.operatorRank.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${stats.totalXp} XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { stats.rankProgressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Text(
                text = stats.xpToNextRankText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OperatorRank.entries.forEach { rank ->
                    val isUnlocked = stats.totalXp >= rank.minXp
                    val isCurrent = stats.operatorRank == rank
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCurrent) "▶ ${rank.displayName}" else rank.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "${rank.minXp} XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifetimePerformanceCard(stats: QsoStatsUiModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lifetime Performance & Combo Streaks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatGridTile(
                    label = "Current Streak",
                    value = "${stats.currentAnswerStreak}",
                    modifier = Modifier.weight(1f)
                )
                StatGridTile(
                    label = "Best Streak",
                    value = "${stats.bestAnswerStreak}",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatGridTile(
                    label = "Lifetime Answered",
                    value = "${stats.lifetimeAnswered}",
                    modifier = Modifier.weight(1f)
                )
                StatGridTile(
                    label = "Lifetime Accuracy",
                    value = "${stats.lifetimeAccuracyPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatGridTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MasteryAndWeakItemsCard(
    stats: QsoStatsUiModel,
    onPracticeCategory: (QsoCategory) -> Unit,
    onPreviewAudio: (String) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "QSO Mastery Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            CategoryMasteryRow(stat = stats.singleCodesMastery)
            CategoryMasteryRow(stat = stats.fullPhrasesMastery)

            Button(
                onClick = { onPracticeCategory(stats.recommendedCategory) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Practice Weakest Category (${stats.recommendedCategory.displayName})")
            }

            HorizontalDivider()

            Text(
                text = "Needs Review (Tap to Preview Audio)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (stats.weakItems.isEmpty()) {
                Text(
                    text = "No weak items right now — answer more questions in Practice mode to identify items for review!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.weakItems.forEach { item ->
                        WeakItemRow(
                            item = item,
                            onClick = { onPreviewAudio(item.codeOrPhrase) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMasteryRow(stat: CategoryMasteryStat) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${stat.masteredCount} / ${stat.totalCount} mastered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { stat.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun WeakItemRow(
    item: WeakItemStat,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.codeOrPhrase,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.morsePattern,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = item.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.accuracyPercent}% (${item.correct}/${item.attempts})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "▶ Listen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
