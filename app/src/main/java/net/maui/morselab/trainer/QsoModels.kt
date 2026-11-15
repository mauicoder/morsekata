package net.maui.morselab.trainer

enum class QsoEntryType {
    SINGLE_CODE,
    FULL_PHRASE
}

enum class QsoCategory {
    SINGLE_CODES,
    FULL_PHRASES,
    MIXED
}

data class QsoEntry(
    val codeOrPhrase: String,
    val meaning: String,
    val type: QsoEntryType
)
