package dev.mauicoder.morsekata.trainer

import dev.mauicoder.morsekata.utils.MorseCodeMaps
import kotlin.random.Random

object CallsignGenerator {

    private val northAmericaPrefixes = listOf(
        "W", "K", "N", "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "VE", "VA", "XE"
    )
    private val europePrefixes = listOf(
        "DL", "DJ", "DK", "G", "M", "F", "I", "IK", "IZ", "EA", "SP", "OK", "SM", "OH", "OE", "HB"
    )
    private val asiaPacificPrefixes = listOf(
        "JA", "JH", "JR", "HL", "BV", "VK", "ZL", "BY", "VU"
    )
    private val americasAfricaPrefixes = listOf(
        "PY", "LU", "ZS", "OA", "CE", "CP", "CX"
    )
    private val specialPrefixes = listOf(
        "WP", "TM", "ZF"
    )

    fun generateCallsign(random: Random = Random.Default): String {
        val poolRoll = random.nextInt(100)
        val prefixList = when {
            poolRoll < 35 -> northAmericaPrefixes
            poolRoll < 65 -> europePrefixes
            poolRoll < 85 -> asiaPacificPrefixes
            poolRoll < 95 -> americasAfricaPrefixes
            else -> specialPrefixes
        }

        val prefix = prefixList[random.nextInt(prefixList.size)]
        val district = random.nextInt(10)

        val suffixRoll = random.nextInt(100)
        val suffixLength = when {
            suffixRoll < 10 -> 1
            suffixRoll < 60 -> 2
            else -> 3
        }

        val suffixChars = CharArray(suffixLength) {
            ('A'.code + random.nextInt(26)).toChar()
        }
        val suffix = String(suffixChars)

        return "$prefix$district$suffix"
    }

    fun applyCutNumberToRst(rst: String): String {
        return rst.replace('9', 'N')
    }

    fun applyCutNumberToSerialOrZone(serialOrZone: String): String {
        var inLeadingZeros = true
        val sb = StringBuilder(serialOrZone.length)
        for (ch in serialOrZone) {
            if (inLeadingZeros && ch == '0') {
                sb.append('T')
            } else {
                inLeadingZeros = false
                if (ch == '9') {
                    sb.append('N')
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    fun generateContestExchange(
        useCutNumbers: Boolean = false,
        random: Random = Random.Default,
        callsign: String? = null
    ): ContestExchange {
        val stationCall = callsign ?: generateCallsign(random)

        val rawRst = "599"
        val rst = if (useCutNumbers) applyCutNumberToRst(rawRst) else rawRst

        val isSerial = random.nextInt(100) < 70
        val rawSerialOrZone = if (isSerial) {
            String.format("%03d", random.nextInt(1, 1000))
        } else {
            String.format("%02d", random.nextInt(1, 41))
        }

        val serialOrZone = if (useCutNumbers) {
            applyCutNumberToSerialOrZone(rawSerialOrZone)
        } else {
            rawSerialOrZone
        }

        val fullText = "$stationCall $rst $serialOrZone"

        return ContestExchange(
            callsign = stationCall,
            rst = rst,
            serialOrZone = serialOrZone,
            fullTransmissionText = fullText
        )
    }

    fun formatMorsePattern(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" / ") { word ->
            word.mapNotNull { char ->
                MorseCodeMaps.asciiToMorse[char.uppercaseChar()]
            }.joinToString(" ")
        }
    }

    fun generateTarget(
        mode: CallsignDrillMode = CallsignDrillMode.CALLSIGNS_ONLY,
        useCutNumbers: Boolean = false,
        random: Random = Random.Default
    ): CallsignTarget {
        return when (mode) {
            CallsignDrillMode.CALLSIGNS_ONLY -> {
                val call = generateCallsign(random)
                CallsignTarget(
                    callsign = call,
                    contestExchange = null,
                    fullTransmissionText = call,
                    morsePattern = formatMorsePattern(call)
                )
            }
            CallsignDrillMode.CONTEST_EXCHANGE -> {
                val exchange = generateContestExchange(useCutNumbers = useCutNumbers, random = random)
                CallsignTarget(
                    callsign = exchange.callsign,
                    contestExchange = exchange,
                    fullTransmissionText = exchange.fullTransmissionText,
                    morsePattern = formatMorsePattern(exchange.fullTransmissionText)
                )
            }
        }
    }
}
