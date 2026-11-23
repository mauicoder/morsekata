package dev.mauicoder.morsekata.trainer

import kotlin.random.Random

object QsoExchangeGenerator {
    private val callsignPool = listOf(
        "W1AW",
        "K6QRP",
        "VE3HAM",
        "DL2KMS",
        "IK2UVR",
        "F5ABC",
        "G4XYZ",
        "EA4DX",
        "JA1CW",
        "VK2OM",
        "HB9TKS",
        "SM5BTU"
    )

    private val rstPool = listOf("599", "589", "579", "559", "569", "539", "449", "598")
    private val qthPool = listOf("BOSTON", "MUNICH", "MILAN", "TOKYO", "SYDNEY", "ZURICH", "MADRID", "LONDON")
    private val opNamePool = listOf("JOHN", "MARCO", "HANS", "KEN", "DAVE", "CARLOS", "LUCA", "ERIK")
    private val conditionPool = listOf("FB SIG", "QRM", "QSB", "QRN", "QRP")
    private val rigPool = listOf("IC7300", "FT891", "KX3", "TS590", "IC705", "FTDX10")
    private val powerPool = listOf("100W", "50W", "5W", "10W", "25W", "400W")
    private val weatherPool = listOf("SUNNY", "RAIN", "CLEAR", "CLOUDY", "WARM", "COLD")

    fun generateSession(
        random: Random = Random.Default,
        excludeCallsign: String? = null
    ): FullQsoSession {
        val candidates = callsignPool.filter { it != excludeCallsign }.ifEmpty { callsignPool }
        val dxCallsign = candidates[random.nextInt(candidates.size)]
        val rst = rstPool[random.nextInt(rstPool.size)]
        val qth = qthPool[random.nextInt(qthPool.size)]
        val opName = opNamePool[random.nextInt(opNamePool.size)]
        val condition = conditionPool[random.nextInt(conditionPool.size)]
        val rig = rigPool[random.nextInt(rigPool.size)]
        val power = powerPool[random.nextInt(powerPool.size)]
        val weather = weatherPool[random.nextInt(weatherPool.size)]

        val stage1 = QsoStageChallenge(
            stage = QsoStage.CQ_CALL,
            transmitText = "CQ CQ CQ DE $dxCallsign $dxCallsign PSE K",
            fields = listOf(
                QsoFieldChallenge(
                    fieldType = QsoExchangeFieldType.CALLSIGN,
                    correctValue = dxCallsign,
                    options = buildCallsignOptions(dxCallsign, random)
                )
            )
        )

        val stage2 = QsoStageChallenge(
            stage = QsoStage.MAIN_EXCHANGE,
            transmitText = "DE $dxCallsign GM TU FER CALL UR RST $rst $rst QTH $qth $qth NAME $opName $opName $condition BTU DE $dxCallsign KN",
            fields = listOf(
                buildPoolFieldChallenge(QsoExchangeFieldType.RST, rst, rstPool, random),
                buildPoolFieldChallenge(QsoExchangeFieldType.QTH, qth, qthPool, random),
                buildPoolFieldChallenge(QsoExchangeFieldType.OP_NAME, opName, opNamePool, random),
                buildPoolFieldChallenge(QsoExchangeFieldType.CONDITION, condition, conditionPool, random)
            )
        )

        val stage3 = QsoStageChallenge(
            stage = QsoStage.FINAL_SIGN_OFF,
            transmitText = "R R FB TU RIG $rig PWR $power WX $weather $weather 73 TU DE $dxCallsign SK",
            fields = listOf(
                buildPoolFieldChallenge(QsoExchangeFieldType.RIG, rig, rigPool, random),
                buildPoolFieldChallenge(QsoExchangeFieldType.POWER, power, powerPool, random),
                buildPoolFieldChallenge(QsoExchangeFieldType.WEATHER, weather, weatherPool, random)
            )
        )

        return FullQsoSession(
            dxCallsign = dxCallsign,
            rst = rst,
            qth = qth,
            opName = opName,
            condition = condition,
            rig = rig,
            power = power,
            weather = weather,
            stages = listOf(stage1, stage2, stage3)
        )
    }

    private fun buildPoolFieldChallenge(
        fieldType: QsoExchangeFieldType,
        correctValue: String,
        pool: List<String>,
        random: Random
    ): QsoFieldChallenge {
        val distractors = pool
            .filter { it != correctValue }
            .shuffled(random)
            .take(3)
        val options = (listOf(correctValue) + distractors).shuffled(random)
        return QsoFieldChallenge(
            fieldType = fieldType,
            correctValue = correctValue,
            options = options
        )
    }

    private fun buildCallsignOptions(correctCallsign: String, random: Random): List<String> {
        val distractors = linkedSetOf<String>()

        // 1. Mutate last character in suffix
        val lastChar = correctCallsign.last()
        val replacementLastChars = listOf('A', 'K', 'M', 'R', 'S', 'T', 'W', 'X').filter { it != lastChar }
        val suffixMutated = correctCallsign.dropLast(1) + replacementLastChars[random.nextInt(replacementLastChars.size)]
        if (suffixMutated != correctCallsign) {
            distractors.add(suffixMutated)
        }

        // 2. Mutate call-area digit
        val digitIndex = correctCallsign.indexOfFirst { it.isDigit() }
        if (digitIndex >= 0) {
            val currentDigit = correctCallsign[digitIndex].digitToInt()
            val nextDigit = if (currentDigit == 9) 1 else currentDigit + 1
            val digitMutated = buildString {
                append(correctCallsign.substring(0, digitIndex))
                append(nextDigit)
                append(correctCallsign.substring(digitIndex + 1))
            }
            if (digitMutated != correctCallsign) {
                distractors.add(digitMutated)
            }
        }

        // 3. Mutate middle suffix character or prefix
        if (correctCallsign.length >= 4) {
            val midIdx = correctCallsign.length - 2
            val midChar = correctCallsign[midIdx]
            val altChars = listOf('B', 'C', 'D', 'F', 'L', 'N', 'P', 'V').filter { it != midChar }
            val midMutated = buildString {
                append(correctCallsign.substring(0, midIdx))
                append(altChars[random.nextInt(altChars.size)])
                append(correctCallsign.substring(midIdx + 1))
            }
            if (midMutated != correctCallsign) {
                distractors.add(midMutated)
            }
        }

        // Fallback from callsignPool if needed
        for (fallback in callsignPool.shuffled(random)) {
            if (distractors.size >= 3) break
            if (fallback != correctCallsign) {
                distractors.add(fallback)
            }
        }

        return (listOf(correctCallsign) + distractors.take(3)).shuffled(random)
    }
}
