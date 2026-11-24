package dev.mauicoder.morsekata.trainer

data class CallsignTrainerUiState(
    val drillMode: CallsignDrillMode = CallsignDrillMode.CALLSIGNS_ONLY,
    val speedMode: CallsignSpeedMode = CallsignSpeedMode.ADAPTIVE,
    val useCutNumbers: Boolean = false,
    val currentWpm: Int = 20,
    val currentTarget: CallsignTarget? = null,
    val typedInput: String = "",
    val isSubmitted: Boolean = false,
    val isCorrect: Boolean = false,
    val diffResult: CallsignDiffResult? = null,
    val hasPlayedAudio: Boolean = false,
    val preAnswerReplayCount: Int = 0,
    val isPlayingAudio: Boolean = false,
    val bestWpmReached: Int = 0,
    val sessionAnsweredCount: Int = 0,
    val sessionCorrectCount: Int = 0,
    val autoAdvanceCountdownActive: Boolean = false,
    val earnedXp: Int = 0,
    val comboStreak: Int = 0
)
