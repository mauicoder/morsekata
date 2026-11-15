package net.maui.morselab.trainer

import kotlin.random.Random

object QsoCatalog {

    val entries: List<QsoEntry> = listOf(
        // Single Codes: Q-codes
        QsoEntry("QTH", "My location is...", QsoEntryType.SINGLE_CODE),
        QsoEntry("QSL", "I confirm receipt / Can you acknowledge receipt?", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRZ", "Who is calling me?", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRM", "Man-made interference", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRN", "Natural static or atmospheric noise", QsoEntryType.SINGLE_CODE),
        QsoEntry("QSY", "Change frequency to...", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRP", "Low power operation / Reduce power", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRT", "Stop transmitting / Closing station", QsoEntryType.SINGLE_CODE),
        QsoEntry("QRV", "I am ready to receive", QsoEntryType.SINGLE_CODE),
        QsoEntry("QSB", "Signal is fading", QsoEntryType.SINGLE_CODE),

        // Single Codes: Procedure signals, abbreviations, and prosigns
        QsoEntry("CQ", "Calling any station", QsoEntryType.SINGLE_CODE),
        QsoEntry("DE", "From (this is)", QsoEntryType.SINGLE_CODE),
        QsoEntry("K", "Go ahead / Over (invitation to transmit)", QsoEntryType.SINGLE_CODE),
        QsoEntry("R", "Received / Roger (all copied)", QsoEntryType.SINGLE_CODE),
        QsoEntry("AR", "End of message", QsoEntryType.SINGLE_CODE),
        QsoEntry("SK", "End of contact / Signing off", QsoEntryType.SINGLE_CODE),
        QsoEntry("RST", "Readability, Signal strength, and Tone report", QsoEntryType.SINGLE_CODE),
        QsoEntry("73", "Best regards", QsoEntryType.SINGLE_CODE),
        QsoEntry("88", "Love and kisses", QsoEntryType.SINGLE_CODE),
        QsoEntry("PSE", "Please", QsoEntryType.SINGLE_CODE),
        QsoEntry("TNX", "Thanks", QsoEntryType.SINGLE_CODE),
        QsoEntry("FER", "For", QsoEntryType.SINGLE_CODE),
        QsoEntry("UR", "Your / You are", QsoEntryType.SINGLE_CODE),
        QsoEntry("ES", "And", QsoEntryType.SINGLE_CODE),
        QsoEntry("GL", "Good luck", QsoEntryType.SINGLE_CODE),
        QsoEntry("OM", "Old man (fellow male operator)", QsoEntryType.SINGLE_CODE),
        QsoEntry("YL", "Young lady (female operator)", QsoEntryType.SINGLE_CODE),
        QsoEntry("FB", "Fine business (excellent)", QsoEntryType.SINGLE_CODE),
        QsoEntry("HW?", "How do you copy?", QsoEntryType.SINGLE_CODE),

        // Full Phrases
        QsoEntry("CQ CQ CQ DE", "General call to any station from...", QsoEntryType.FULL_PHRASE),
        QsoEntry("UR RST 599", "Your signal report is 599 (perfectly readable, strong, pure tone)", QsoEntryType.FULL_PHRASE),
        QsoEntry("TNX FER QSO", "Thanks for the contact", QsoEntryType.FULL_PHRASE),
        QsoEntry("QTH IS", "My location is...", QsoEntryType.FULL_PHRASE),
        QsoEntry("73 ES GL", "Best regards and good luck", QsoEntryType.FULL_PHRASE),
        QsoEntry("QRZ DE", "Who is calling me, from...", QsoEntryType.FULL_PHRASE),
        QsoEntry("PSE K", "Please go ahead and transmit", QsoEntryType.FULL_PHRASE),
        QsoEntry("HW CPY?", "How do you copy my transmission?", QsoEntryType.FULL_PHRASE),
        QsoEntry("FB OM", "Fine business (excellent), old man", QsoEntryType.FULL_PHRASE),
        QsoEntry("TU ES 73", "Thank you and best regards", QsoEntryType.FULL_PHRASE),
        QsoEntry("WX HR IS", "Weather here is...", QsoEntryType.FULL_PHRASE),
        QsoEntry("RIG HR IS", "Station transceiver here is...", QsoEntryType.FULL_PHRASE),
        QsoEntry("ANT IS", "Antenna is...", QsoEntryType.FULL_PHRASE),
        QsoEntry("NAME HR IS", "My name here is...", QsoEntryType.FULL_PHRASE),
        QsoEntry("QSL VIA BURO", "Confirm contact via the QSL bureau", QsoEntryType.FULL_PHRASE),
        QsoEntry("SRI QRM", "Sorry for the interference", QsoEntryType.FULL_PHRASE)
    )

    fun entriesForCategory(category: QsoCategory): List<QsoEntry> {
        return when (category) {
            QsoCategory.SINGLE_CODES -> entries.filter { it.type == QsoEntryType.SINGLE_CODE }
            QsoCategory.FULL_PHRASES -> entries.filter { it.type == QsoEntryType.FULL_PHRASE }
            QsoCategory.MIXED -> entries
        }
    }

    fun randomEntry(
        category: QsoCategory,
        exclude: QsoEntry? = null,
        random: Random = Random.Default
    ): QsoEntry {
        val pool = entriesForCategory(category)
        val candidates = if (exclude != null && pool.size > 1) {
            pool.filter { it != exclude }.ifEmpty { pool }
        } else {
            pool
        }
        return candidates.random(random)
    }

    fun generateOptions(
        target: QsoEntry,
        random: Random = Random.Default
    ): List<String> {
        val distractors = entries
            .filter { it.type == target.type && it.codeOrPhrase != target.codeOrPhrase }
            .shuffled(random)
            .take(3)
            .map { it.codeOrPhrase }
        return (distractors + target.codeOrPhrase).shuffled(random)
    }
}
