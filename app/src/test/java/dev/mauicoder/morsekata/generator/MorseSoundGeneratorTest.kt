package dev.mauicoder.morsekata.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class MorseSoundGeneratorTest {

    private val generator = MorseSoundGenerator()
    private val sampleRate = 44100
    private val frequency = 700

    private fun msToSamples(ms: Int): Int = ms * sampleRate / 1000
    private fun dotSamplesForWpm(wpm: Int): Int = msToSamples(1200 / wpm)

    @Test
    fun `generate returns empty array when input is empty or whitespace or unsupported characters`() {
        assertEquals(0, generator.generate("", 20, 20, frequency, sampleRate).size)
        assertEquals(0, generator.generate("    ", 20, 20, frequency, sampleRate).size)
        assertEquals(0, generator.generate("#%^*~", 20, 20, frequency, sampleRate).size)
    }

    @Test
    fun `generate applies 35 percent sidetone peak amplitude headroom and reaches sustain target`() {
        val samples = generator.generate("T", 20, 20, frequency, sampleRate)
        val maxMagnitude = samples.maxOf { abs(it.toInt()) }

        // 0.35 * 32767 = 11468
        assertTrue("Expected max magnitude <= 11468, got $maxMagnitude", maxMagnitude <= 11468)
        assertTrue("Expected max magnitude >= 11000 during sustain, got $maxMagnitude", maxMagnitude >= 11000)
    }

    @Test
    fun `generate applies 20ms padding and asymmetric 10ms attack 14ms release Blackman-Harris envelope`() {
        val wpm = 20
        val dotSamples = dotSamplesForWpm(wpm) // 60ms = 2646 samples
        val padSamples = msToSamples(20) // 20ms = 882 samples
        val attackSamples = msToSamples(10) // 10ms = 441 samples
        val releaseSamples = msToSamples(14) // 14ms = 617 samples

        // 'E' is a single dot "."
        val samples = generator.generate("E", wpm, wpm, frequency, sampleRate)
        assertEquals(padSamples + dotSamples + padSamples, samples.size)

        // Verify leading 20ms silence
        for (i in 0 until padSamples) {
            assertEquals("Leading silence sample $i should be 0", 0, samples[i].toInt())
        }

        // Verify trailing 20ms silence
        for (i in (padSamples + dotSamples) until samples.size) {
            assertEquals("Trailing silence sample $i should be 0", 0, samples[i].toInt())
        }

        // Verify tone starts and ends at 0 amplitude (no boundary click)
        assertEquals(0, samples[padSamples].toInt())
        assertEquals(0, samples[padSamples + dotSamples - 1].toInt())

        // Verify ultra-soft Blackman-Harris initial slope: first 20% of attack stays very gentle (< 15% of max amplitude)
        val earlyAttackPeak = (0 until attackSamples / 5).maxOf { abs(samples[padSamples + it].toInt()) }
        val midAttackPeak = (attackSamples / 5 until attackSamples).maxOf { abs(samples[padSamples + it].toInt()) }
        val sustainPeak = (attackSamples until (dotSamples - releaseSamples)).maxOf { abs(samples[padSamples + it].toInt()) }

        assertTrue("Blackman-Harris early attack ($earlyAttackPeak) should be < 1500 (~13% of peak)", earlyAttackPeak < 1500)
        assertTrue("Early attack ($earlyAttackPeak) < mid-attack ($midAttackPeak)", earlyAttackPeak < midAttackPeak)
        assertTrue("Sustain ($sustainPeak) should reach ~35% headroom (>= 11000)", sustainPeak >= 11000)

        // Verify asymmetric release (14ms) is longer than attack (10ms):
        // At 11ms before the end of the tone (inside the 14ms release window), amplitude is already attenuated below sustain
        val elevenMsSamples = msToSamples(11)
        val releaseTailWindowStart = padSamples + dotSamples - elevenMsSamples
        val releaseTailWindowEnd = padSamples + dotSamples - msToSamples(9)
        val releasePeakAt10msFromEnd = (releaseTailWindowStart until releaseTailWindowEnd).maxOf { abs(samples[it].toInt()) }
        assertTrue(
            "Expected 14ms release to already be attenuated at ~10ms before end ($releasePeakAt10msFromEnd < 11000)",
            releasePeakAt10msFromEnd < 11000
        )

        // Verify dot ("E") and dash ("T") have identical 10ms attack samples
        val dashSamplesArray = generator.generate("T", wpm, wpm, frequency, sampleRate)
        for (i in 0 until attackSamples) {
            assertEquals(
                "Dot and dash should have identical 10ms attack at sample $i",
                samples[padSamples + i].toInt(),
                dashSamplesArray[padSamples + i].toInt()
            )
        }

        // Verify bounded sample-to-sample step (no phase/amplitude discontinuity)
        // Theoretical max step for 700Hz at 44100Hz and 11468 amplitude is ~11468 * 2 * pi * 700 / 44100 ≈ 1144
        val maxStep = (1 until samples.size).maxOf { abs(samples[it].toInt() - samples[it - 1].toInt()) }
        assertTrue("Expected bounded sample-to-sample step <= 1250, got $maxStep", maxStep <= 1250)
    }

    @Test
    fun `generate maintains continuous oscillator phase across multiple symbols`() {
        // Use a frequency where phase at the start of the second symbol is distinctly different from phase = 0
        // At sampleRate = 44100, wpm = 20:
        // padSamples = 882, dotSamples = 2646, intraPause = 2646 -> second symbol starts at globalIndex = 6174
        // Pick frequency = 715 Hz so 6174 * 715 / 44100 = 100.10034 cycles (fractional phase != 0)
        // Whereas localIndex = 0 would have phase = 0.0
        val testFreq = 715
        val wpm = 20
        val padSamples = msToSamples(20) // 882
        val dotSamples = dotSamplesForWpm(wpm) // 2646
        val intraPause = dotSamples // 2646
        val attackSamples = msToSamples(10) // 441

        // "A" = ".-"
        val samples = generator.generate("A", wpm, wpm, testFreq, sampleRate)
        val secondSymbolStart = padSamples + dotSamples + intraPause
        // Inspect a sample well inside the sustain region of the dash (e.g., i = attackSamples + 50)
        val localIndex = attackSamples + 50
        val globalIndex = secondSymbolStart + localIndex

        val expectedContinuous = (sin(2.0 * Math.PI * globalIndex * testFreq / sampleRate) * MorseSoundGenerator.MAX_AMPLITUDE).toInt().toShort()
        val expectedResetPhase = (sin(2.0 * Math.PI * localIndex * testFreq / sampleRate) * MorseSoundGenerator.MAX_AMPLITUDE).toInt().toShort()

        // Ensure our chosen index distinguishes continuous phase from reset phase
        assertTrue("Test setup: continuous vs reset phase must differ", abs(expectedContinuous - expectedResetPhase) > 1000)
        assertEquals(
            "Second symbol should follow continuous master oscillator phase at globalIndex=$globalIndex",
            expectedContinuous.toInt(),
            samples[globalIndex].toInt()
        )
    }

    @Test
    fun `generate clamps asymmetric attack and release proportionally to 90 percent of tone at extreme WPM`() {
        // At 120 WPM, dotDuration = 10ms (< 10ms attack + 14ms release = 24ms)
        val wpm = 120
        val dotSamples = dotSamplesForWpm(wpm) // 10ms = 441 samples
        val padSamples = msToSamples(20) // 882 samples
        val samples = generator.generate("E", wpm, wpm, frequency, sampleRate)

        assertEquals(padSamples + dotSamples + padSamples, samples.size)
        // With 90% max envelope ratio and 10:14 attack:release ratio:
        // attack is ~37.5% of dotSamples, release is ~52.5% of dotSamples, sustain is between 37.5% and 47.5%
        val sustainStart = padSamples + (dotSamples * 0.38).toInt()
        val sustainEnd = padSamples + (dotSamples * 0.47).toInt()
        val sustainPeak = (sustainStart..sustainEnd).maxOf { abs(samples[it].toInt()) }
        assertTrue("Expected sustain peak >= 11000 at 120 WPM, got $sustainPeak", sustainPeak >= 11000)
    }

    @Test
    fun `generate uses mutually exclusive intra-char, inter-char, and inter-word pauses with Farnsworth timing`() {
        val wpm = 20 // charDot = 60ms
        val farnsworthWpm = 10 // farnDot = 120ms

        val charDotSamples = dotSamplesForWpm(wpm) // 2646
        val dashSamples = 3 * charDotSamples // 7938
        val intraCharPauseSamples = charDotSamples // 2646

        val farnDotSamples = dotSamplesForWpm(farnsworthWpm) // 5292
        val interCharPauseSamples = 3 * farnDotSamples // 15876
        val interWordPauseSamples = 7 * farnDotSamples // 37044
        val padSamples = msToSamples(20) // 882

        // "A" = ".-" -> pad + dot + intra + dash + pad
        val aSamples = generator.generate("A", wpm, farnsworthWpm, frequency, sampleRate)
        val expectedASize = padSamples + charDotSamples + intraCharPauseSamples + dashSamples + padSamples
        assertEquals(expectedASize, aSamples.size)

        // "ET" = "." and "-" -> pad + dot + interChar + dash + pad
        val etSamples = generator.generate("ET", wpm, farnsworthWpm, frequency, sampleRate)
        val expectedEtSize = padSamples + charDotSamples + interCharPauseSamples + dashSamples + padSamples
        assertEquals(expectedEtSize, etSamples.size)

        // "E T" (with extra spaces) -> pad + dot + interWord + dash + pad
        val eSpaceTSamples = generator.generate("  E   T  ", wpm, farnsworthWpm, frequency, sampleRate)
        val expectedESpaceTSize = padSamples + charDotSamples + interWordPauseSamples + dashSamples + padSamples
        assertEquals(expectedESpaceTSize, eSpaceTSamples.size)
    }
}
