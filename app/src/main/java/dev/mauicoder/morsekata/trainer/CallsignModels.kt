package dev.mauicoder.morsekata.trainer

enum class CallsignDrillMode {
    CALLSIGNS_ONLY,
    CONTEST_EXCHANGE
}

enum class CallsignSpeedMode {
    ADAPTIVE,
    FIXED
}

data class ContestExchange(
    val callsign: String,
    val rst: String,
    val serialOrZone: String,
    val fullTransmissionText: String
)

data class CallsignTarget(
    val callsign: String,
    val contestExchange: ContestExchange? = null,
    val fullTransmissionText: String,
    val morsePattern: String
)

data class CallsignDiffResult(
    val typed: String,
    val expected: String,
    val isMatch: Boolean,
    val diffSegments: List<DiffSegment>
)

data class DiffSegment(
    val char: Char,
    val isCorrect: Boolean
)
