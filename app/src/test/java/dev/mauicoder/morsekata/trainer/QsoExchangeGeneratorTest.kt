package dev.mauicoder.morsekata.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QsoExchangeGeneratorTest {

    @Test
    fun `generateSession produces Stage 1 CQ_CALL with repeated callsign and 4 distinct look-alike callsign options`() {
        val session = QsoExchangeGenerator.generateSession(random = Random(42), excludeCallsign = "W1AW")

        assertTrue(session.dxCallsign.isNotBlank())
        assertNotEquals("W1AW", session.dxCallsign)
        assertEquals(
            listOf(QsoStage.CQ_CALL, QsoStage.MAIN_EXCHANGE, QsoStage.FINAL_SIGN_OFF),
            session.stages.map { it.stage }
        )

        val stage1 = session.stageChallenge(QsoStage.CQ_CALL)
        assertEquals("CQ CQ CQ DE ${session.dxCallsign} ${session.dxCallsign} PSE K", stage1.transmitText)
        assertEquals(1, stage1.fields.size)

        val callsignField = stage1.fields.single()
        assertEquals(QsoExchangeFieldType.CALLSIGN, callsignField.fieldType)
        assertEquals(session.dxCallsign, callsignField.correctValue)
        assertEquals(4, callsignField.options.size)
        assertEquals(4, callsignField.options.toSet().size)
        assertTrue(callsignField.options.contains(session.dxCallsign))
        assertTrue(callsignField.options.all { it.matches(Regex("[A-Z0-9]{4,7}")) })
    }

    @Test
    fun `generateSession produces Stage 2 MAIN_EXCHANGE and Stage 3 FINAL_SIGN_OFF with exact CW templates and 4 distinct options per field`() {
        val session = QsoExchangeGenerator.generateSession(random = Random(99))

        val stage2 = session.stageChallenge(QsoStage.MAIN_EXCHANGE)
        assertEquals(
            "DE ${session.dxCallsign} GM TU FER CALL UR RST ${session.rst} ${session.rst} QTH ${session.qth} ${session.qth} NAME ${session.opName} ${session.opName} ${session.condition} BTU DE ${session.dxCallsign} KN",
            stage2.transmitText
        )
        assertEquals(
            listOf(
                QsoExchangeFieldType.RST,
                QsoExchangeFieldType.QTH,
                QsoExchangeFieldType.OP_NAME,
                QsoExchangeFieldType.CONDITION
            ),
            stage2.fields.map { it.fieldType }
        )
        assertEquals(session.rst, stage2.fields[0].correctValue)
        assertEquals(session.qth, stage2.fields[1].correctValue)
        assertEquals(session.opName, stage2.fields[2].correctValue)
        assertEquals(session.condition, stage2.fields[3].correctValue)
        stage2.fields.forEach { field ->
            assertEquals(4, field.options.size)
            assertEquals(4, field.options.toSet().size)
            assertTrue(field.options.contains(field.correctValue))
        }

        val stage3 = session.stageChallenge(QsoStage.FINAL_SIGN_OFF)
        assertEquals(
            "R R FB TU RIG ${session.rig} PWR ${session.power} WX ${session.weather} ${session.weather} 73 TU DE ${session.dxCallsign} SK",
            stage3.transmitText
        )
        assertEquals(
            listOf(
                QsoExchangeFieldType.RIG,
                QsoExchangeFieldType.POWER,
                QsoExchangeFieldType.WEATHER
            ),
            stage3.fields.map { it.fieldType }
        )
        assertEquals(session.rig, stage3.fields[0].correctValue)
        assertEquals(session.power, stage3.fields[1].correctValue)
        assertEquals(session.weather, stage3.fields[2].correctValue)
        stage3.fields.forEach { field ->
            assertEquals(4, field.options.size)
            assertEquals(4, field.options.toSet().size)
            assertTrue(field.options.contains(field.correctValue))
        }
    }

    @Test
    fun `generateSession honors excludeCallsign and produces valid 4-option fields across many seeds`() {
        var previousCallsign: String? = null
        repeat(25) { seed ->
            val session = QsoExchangeGenerator.generateSession(
                random = Random(seed),
                excludeCallsign = previousCallsign
            )
            if (previousCallsign != null) {
                assertNotEquals(previousCallsign, session.dxCallsign)
            }
            session.stages.flatMap { it.fields }.forEach { field ->
                assertEquals(4, field.options.size)
                assertEquals(4, field.options.toSet().size)
                assertTrue(field.options.contains(field.correctValue))
            }
            previousCallsign = session.dxCallsign
        }
    }

    @Test
    fun `fillActionLabel returns expected standard procedural repeat prosign for every field type`() {
        val expectedLabels = mapOf(
            QsoExchangeFieldType.CALLSIGN to "AGN PSE CALL",
            QsoExchangeFieldType.RST to "AGN PSE RST",
            QsoExchangeFieldType.QTH to "AGN PSE QTH",
            QsoExchangeFieldType.OP_NAME to "AGN PSE NAME",
            QsoExchangeFieldType.CONDITION to "AGN PSE COND",
            QsoExchangeFieldType.RIG to "AGN PSE RIG",
            QsoExchangeFieldType.POWER to "AGN PSE PWR",
            QsoExchangeFieldType.WEATHER to "AGN PSE WX"
        )

        assertEquals(8, QsoExchangeFieldType.entries.size)
        for (fieldType in QsoExchangeFieldType.entries) {
            assertEquals(
                "Mismatched fill action label for field $fieldType",
                expectedLabels[fieldType],
                fieldType.fillActionLabel
            )
        }
    }

    @Test
    fun `fillTransmissionTextFor returns standard doubled value with CW prefix and BK prosign for all field types`() {
        val session = QsoExchangeGenerator.generateSession(random = Random(1234))

        val expectedFills = mapOf(
            QsoExchangeFieldType.CALLSIGN to "CALL ${session.dxCallsign} ${session.dxCallsign} BK",
            QsoExchangeFieldType.RST to "UR RST ${session.rst} ${session.rst} BK",
            QsoExchangeFieldType.QTH to "QTH ${session.qth} ${session.qth} BK",
            QsoExchangeFieldType.OP_NAME to "OP ${session.opName} ${session.opName} BK",
            QsoExchangeFieldType.CONDITION to "SIG ${session.condition} ${session.condition} BK",
            QsoExchangeFieldType.RIG to "RIG ${session.rig} ${session.rig} BK",
            QsoExchangeFieldType.POWER to "PWR ${session.power} ${session.power} BK",
            QsoExchangeFieldType.WEATHER to "WX ${session.weather} ${session.weather} BK"
        )

        assertEquals(8, QsoExchangeFieldType.entries.size)
        for (fieldType in QsoExchangeFieldType.entries) {
            val fillText = session.fillTransmissionTextFor(fieldType)
            assertEquals(
                "Mismatched fill transmission text for field $fieldType",
                expectedFills[fieldType],
                fillText
            )
        }
    }
}
