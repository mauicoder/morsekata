package net.maui.morselab.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class MorseSoundGeneratorTest {

    private val generator = MorseSoundGenerator()
    private val sampleRate = 44100
    private val frequency = 700

    @Test
    fun `generate returns empty array when input is empty or whitespace or unsupported characters`() {
        assertEquals(0, generator.generate("", 20, 20, frequency, sampleRate).size)
        assertEquals(0, generator.generate("    ", 20, 20, frequency, sampleRate).size)
        assertEquals(0, generator.generate("#%^*~", 20, 20, frequency, sampleRate).size)
    }

    @Test
    fun `generate applies 75 percent peak amplitude headroom and reaches sustain target`() {
        val samples = generator.generate("T", 20, 20, frequency, sampleRate)
        val maxMagnitude = samples.maxOf { abs(it.toInt()) }

        // 0.75 * 32767 = 24575
        assertTrue("Expected max magnitude <= 24575, got $maxMagnitude", maxMagnitude <= 24575)
        assertTrue("Expected max magnitude >= 24000 during sustain, got $maxMagnitude", maxMagnitude >= 24000)
    }

    @Test
    fun `generate applies 20ms leading and trailing silence and fixed 5ms raised-cosine envelope`() {
        val wpm = 20
        val dotSamples = (1200 / wpm) * sampleRate / 1000 // 60ms = 2646 samples
        val padSamples = 20 * sampleRate / 1000 // 20ms = 882 samples
        val fadeSamples = 5 * sampleRate / 1000 // 5ms = 220 samples

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

        // Verify envelope gradually increases over the 5ms attack and reaches full sustain after fadeSamples
        val earlyPeak = (0 until fadeSamples / 4).maxOf { abs(samples[padSamples + it].toInt()) }
        val midFadePeak = (fadeSamples / 4 until fadeSamples).maxOf { abs(samples[padSamples + it].toInt()) }
        val sustainPeak = (fadeSamples until (dotSamples - fadeSamples)).maxOf { abs(samples[padSamples + it].toInt()) }

        assertTrue("Early attack ($earlyPeak) should be smaller than mid-attack ($midFadePeak)", earlyPeak < midFadePeak)
        assertTrue("Mid-attack ($midFadePeak) should be smaller than sustain ($sustainPeak)", midFadePeak <= sustainPeak)
        assertTrue("Sustain ($sustainPeak) should reach ~75% headroom (>= 24000)", sustainPeak >= 24000)

        // Verify dot ("E") and dash ("T") have identical 5ms attack samples
        val dashSamplesArray = generator.generate("T", wpm, wpm, frequency, sampleRate)
        for (i in 0 until fadeSamples) {
            assertEquals(
                "Dot and dash should have identical 5ms attack envelope at sample $i",
                samples[padSamples + i].toInt(),
                dashSamplesArray[padSamples + i].toInt()
            )
        }

        // Verify bounded sample-to-sample step (no phase/amplitude discontinuity)
        // Theoretical max step for 700Hz at 44100Hz and 24575 amplitude is ~24575 * 2 * pi * 700 / 44100 ≈ 2450
        val maxStep = (1 until samples.size).maxOf { abs(samples[it].toInt() - samples[it - 1].toInt()) }
        assertTrue("Expected bounded sample-to-sample step <= 2600, got $maxStep", maxStep <= 2600)
    }

    @Test
    fun `generate clamps fade samples to 40 percent of tone duration at extreme WPM`() {
        // At 120 WPM, dotDuration = 10ms, so 40% is 4ms (< 5ms)
        val wpm = 120
        val dotSamples = (1200 / wpm) * sampleRate / 1000 // 10ms = 441 samples
        val padSamples = 20 * sampleRate / 1000 // 882 samples
        val samples = generator.generate("E", wpm, wpm, frequency, sampleRate)

        assertEquals(padSamples + dotSamples + padSamples, samples.size)
        // Middle 20% of the pulse is unattenuated sustain
        val sustainStart = padSamples + (dotSamples * 0.40).toInt()
        val sustainEnd = padSamples + dotSamples - (dotSamples * 0.40).toInt()
        val sustainPeak = (sustainStart until sustainEnd).maxOf { abs(samples[it].toInt()) }
        assertTrue("Expected sustain peak >= 24000 at 120 WPM, got $sustainPeak", sustainPeak >= 24000)
    }

    @Test
    fun `generate uses mutually exclusive intra-char, inter-char, and inter-word pauses with Farnsworth timing`() {
        val wpm = 20 // charDot = 60ms
        val farnsworthWpm = 10 // farnDot = 120ms

        val charDotSamples = (1200 / wpm) * sampleRate / 1000 // 2646
        val dashSamples = 3 * charDotSamples // 7938
        val intraCharPauseSamples = charDotSamples // 2646

        val farnDotSamples = (1200 / farnsworthWpm) * sampleRate / 1000 // 5292
        val interCharPauseSamples = 3 * farnDotSamples // 15876
        val interWordPauseSamples = 7 * farnDotSamples // 37044
        val padSamples = 20 * sampleRate / 1000 // 882

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

    @Test
    fun generateSosWavFileForAnalysis() {
        val text = "SOS"
        val wpm = 20
        val farnsworthWpm = 20

        val waveData = generator.generateWave(
            text,
            wpm,
            farnsworthWpm,
            frequency,
            sampleRate
        )

        val outputFile = File("build/sos_output.wav")
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(waveData)

        println("WAV file generated for analysis at: ${outputFile.absolutePath}")
    }
}
