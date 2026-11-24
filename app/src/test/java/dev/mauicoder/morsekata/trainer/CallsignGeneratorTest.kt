package dev.mauicoder.morsekata.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CallsignGeneratorTest {

    private val callsignRegex = Regex("^[A-Z0-9]{1,3}[0-9][A-Z]{1,3}$")

    @Test
    fun `generateCallsign generates callsign conforming to amateur radio ITU structure`() {
        val random = Random(42)
        repeat(100) {
            val call = CallsignGenerator.generateCallsign(random)
            assertTrue(
                "Callsign '$call' must match standard prefix-digit-suffix regex",
                callsignRegex.matches(call)
            )
        }
    }

    @Test
    fun `generateCallsign covers North America, Europe, Asia Pacific, and South America Africa`() {
        val random = Random(12345)
        val generated = (1..500).map { CallsignGenerator.generateCallsign(random) }

        val hasNorthAmerica = generated.any { it.startsWith("W") || it.startsWith("K") || it.startsWith("VE") }
        val hasEurope = generated.any { it.startsWith("DL") || it.startsWith("G") || it.startsWith("F") || it.startsWith("SP") }
        val hasAsiaPacific = generated.any { it.startsWith("JA") || it.startsWith("VK") || it.startsWith("ZL") || it.startsWith("BY") }
        val hasAmericasAfrica = generated.any { it.startsWith("PY") || it.startsWith("LU") || it.startsWith("ZS") }

        assertTrue("Must generate North America callsigns", hasNorthAmerica)
        assertTrue("Must generate Europe callsigns", hasEurope)
        assertTrue("Must generate Asia/Pacific callsigns", hasAsiaPacific)
        assertTrue("Must generate Americas/Africa callsigns", hasAmericasAfrica)
    }

    @Test
    fun `generateCallsign produces 1, 2, and 3 letter suffixes`() {
        val random = Random(999)
        val suffixes = (1..500).map {
            val call = CallsignGenerator.generateCallsign(random)
            val match = Regex("[0-9]([A-Z]+)$").find(call)
            match?.groupValues?.get(1) ?: ""
        }

        assertTrue("Must produce 1-letter suffixes", suffixes.any { it.length == 1 })
        assertTrue("Must produce 2-letter suffixes", suffixes.any { it.length == 2 })
        assertTrue("Must produce 3-letter suffixes", suffixes.any { it.length == 3 })
    }

    @Test
    fun `generateCallsign is deterministic with seeded Random`() {
        val call1 = CallsignGenerator.generateCallsign(Random(100))
        val call2 = CallsignGenerator.generateCallsign(Random(100))
        assertEquals(call1, call2)
    }

    @Test
    fun `applyCutNumberToRst maps 9 to N`() {
        assertEquals("5NN", CallsignGenerator.applyCutNumberToRst("599"))
        assertEquals("57N", CallsignGenerator.applyCutNumberToRst("579"))
        assertEquals("588", CallsignGenerator.applyCutNumberToRst("588"))
    }

    @Test
    fun `applyCutNumberToSerialOrZone maps leading zeros to T and 9 to N`() {
        assertEquals("TT1", CallsignGenerator.applyCutNumberToSerialOrZone("001"))
        assertEquals("T42", CallsignGenerator.applyCutNumberToSerialOrZone("042"))
        assertEquals("100", CallsignGenerator.applyCutNumberToSerialOrZone("100"))
        assertEquals("TNN", CallsignGenerator.applyCutNumberToSerialOrZone("099"))
        assertEquals("T5", CallsignGenerator.applyCutNumberToSerialOrZone("05"))
        assertEquals("14", CallsignGenerator.applyCutNumberToSerialOrZone("14"))
        assertEquals("TTT", CallsignGenerator.applyCutNumberToSerialOrZone("000"))
    }

    @Test
    fun `generateContestExchange formats full transmission as call rst serialOrZone without cut numbers`() {
        val exchange = CallsignGenerator.generateContestExchange(
            useCutNumbers = false,
            random = Random(777),
            callsign = "W1AW"
        )

        assertEquals("W1AW", exchange.callsign)
        assertEquals("599", exchange.rst)
        assertEquals("W1AW ${exchange.rst} ${exchange.serialOrZone}", exchange.fullTransmissionText)
        assertTrue(exchange.serialOrZone.all { it.isDigit() })
    }

    @Test
    fun `generateContestExchange applies cut numbers when enabled`() {
        val exchange = CallsignGenerator.generateContestExchange(
            useCutNumbers = true,
            random = Random(777),
            callsign = "DL1ABC"
        )

        assertEquals("DL1ABC", exchange.callsign)
        assertEquals("5NN", exchange.rst)
        assertEquals("DL1ABC 5NN ${exchange.serialOrZone}", exchange.fullTransmissionText)
        assertFalse("RST should not contain 9 when cut numbers enabled", exchange.rst.contains('9'))
        assertFalse("Serial should not have leading 0 when cut numbers enabled", exchange.serialOrZone.startsWith('0'))
    }

    @Test
    fun `formatMorsePattern formats characters and word boundaries correctly`() {
        assertEquals(".-- .---- .- .--", CallsignGenerator.formatMorsePattern("W1AW"))
        assertEquals(
            ".-- .---- .- .-- / ..... ----. ----. / ----- ----- .----",
            CallsignGenerator.formatMorsePattern("W1AW 599 001")
        )
        assertEquals(
            ".-- .---- .- .-- / ..... -. -. / - - .----",
            CallsignGenerator.formatMorsePattern("W1AW 5NN TT1")
        )
    }

    @Test
    fun `generateTarget in CALLSIGNS_ONLY mode produces CallsignTarget without contestExchange`() {
        val target = CallsignGenerator.generateTarget(
            mode = CallsignDrillMode.CALLSIGNS_ONLY,
            random = Random(42)
        )

        assertNull(target.contestExchange)
        assertEquals(target.callsign, target.fullTransmissionText)
        assertEquals(CallsignGenerator.formatMorsePattern(target.callsign), target.morsePattern)
    }

    @Test
    fun `generateTarget in CONTEST_EXCHANGE mode produces CallsignTarget with contestExchange`() {
        val target = CallsignGenerator.generateTarget(
            mode = CallsignDrillMode.CONTEST_EXCHANGE,
            useCutNumbers = true,
            random = Random(42)
        )

        assertNotNull(target.contestExchange)
        assertEquals(target.contestExchange!!.fullTransmissionText, target.fullTransmissionText)
        assertEquals(CallsignGenerator.formatMorsePattern(target.fullTransmissionText), target.morsePattern)
    }
}
