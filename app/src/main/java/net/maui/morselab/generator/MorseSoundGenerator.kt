package net.maui.morselab.generator

import net.maui.morselab.utils.MorseCodeMaps
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class MorseSoundGenerator {

    companion object {
        const val PEAK_AMPLITUDE_FACTOR = 0.35
        const val MAX_AMPLITUDE = (Short.MAX_VALUE * PEAK_AMPLITUDE_FACTOR).toInt() // 11468
        const val ATTACK_DURATION_MS = 10
        const val RELEASE_DURATION_MS = 14
        const val MAX_ENVELOPE_RATIO = 0.90
        const val PADDING_SILENCE_MS = 20

        // 4-term Blackman-Harris window coefficients
        private const val BH_A0 = 0.35875
        private const val BH_A1 = 0.48829
        private const val BH_A2 = 0.14128
        private const val BH_A3 = 0.01168
        private const val BH_MIN = BH_A0 - BH_A1 + BH_A2 - BH_A3 // 0.00006
    }

    /**
     * Generates a 16-bit PCM Mono audio stream for the given text using a single-pass
     * continuous-phase oscillator and an asymmetric 4-term Blackman-Harris envelope.
     */
    fun generate(
        text: String,
        wpm: Int,
        farnsworthWpm: Int,
        frequency: Int,
        sampleRate: Int
    ): ShortArray {
        val validWords = text.uppercase()
            .trim()
            .split(Regex("\\s+"))
            .map { word -> word.mapNotNull { char -> MorseCodeMaps.asciiToMorse[char] } }
            .filter { it.isNotEmpty() }

        if (validWords.isEmpty()) {
            return ShortArray(0)
        }

        val dotDuration = morseTiming(wpm)
        val dashDuration = 3 * dotDuration
        val dotSamples = dotDuration * sampleRate / 1000
        val dashSamples = dashDuration * sampleRate / 1000
        val intraCharacterPauseSamples = dotSamples

        val farnsworthDotDuration = morseTiming(farnsworthWpm)
        val interCharacterPauseSamples = 3 * farnsworthDotDuration * sampleRate / 1000
        val interWordPauseSamples = 7 * farnsworthDotDuration * sampleRate / 1000
        val paddingSilenceSamples = PADDING_SILENCE_MS * sampleRate / 1000

        // Calculate exact total sample length for single-pass buffer allocation
        var totalSamples = paddingSilenceSamples * 2
        validWords.forEachIndexed { wordIndex, wordCharacters ->
            if (wordIndex > 0) {
                totalSamples += interWordPauseSamples
            }
            wordCharacters.forEachIndexed { charIndex, morseCode ->
                if (charIndex > 0) {
                    totalSamples += interCharacterPauseSamples
                }
                morseCode.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) {
                        totalSamples += intraCharacterPauseSamples
                    }
                    totalSamples += when (symbol) {
                        '.' -> dotSamples
                        '-' -> dashSamples
                        else -> 0
                    }
                }
            }
        }

        val output = ShortArray(totalSamples)
        var cursor = paddingSilenceSamples

        validWords.forEachIndexed { wordIndex, wordCharacters ->
            if (wordIndex > 0) {
                cursor += interWordPauseSamples
            }
            wordCharacters.forEachIndexed { charIndex, morseCode ->
                if (charIndex > 0) {
                    cursor += interCharacterPauseSamples
                }
                morseCode.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) {
                        cursor += intraCharacterPauseSamples
                    }
                    val toneSamples = when (symbol) {
                        '.' -> dotSamples
                        '-' -> dashSamples
                        else -> 0
                    }
                    if (toneSamples > 0) {
                        writeContinuousPhaseTone(
                            buffer = output,
                            startSampleIndex = cursor,
                            numSamples = toneSamples,
                            frequency = frequency,
                            sampleRate = sampleRate
                        )
                        cursor += toneSamples
                    }
                }
            }
        }

        return output
    }

    private fun writeContinuousPhaseTone(
        buffer: ShortArray,
        startSampleIndex: Int,
        numSamples: Int,
        frequency: Int,
        sampleRate: Int
    ) {
        val targetAttackSamples = ATTACK_DURATION_MS * sampleRate / 1000
        val targetReleaseSamples = RELEASE_DURATION_MS * sampleRate / 1000
        val maxEnvelopeSamples = (numSamples * MAX_ENVELOPE_RATIO).toInt()
        val totalTargetEnvelope = targetAttackSamples + targetReleaseSamples

        val attackSamples: Int
        val releaseSamples: Int
        if (totalTargetEnvelope > maxEnvelopeSamples && totalTargetEnvelope > 0) {
            attackSamples = maxEnvelopeSamples * targetAttackSamples / totalTargetEnvelope
            releaseSamples = maxEnvelopeSamples - attackSamples
        } else {
            attackSamples = targetAttackSamples
            releaseSamples = targetReleaseSamples
        }

        val phaseIncrement = 2.0 * Math.PI * frequency / sampleRate

        for (i in 0 until numSamples) {
            val globalIndex = startSampleIndex + i
            val angle = globalIndex * phaseIncrement
            val envelope = when {
                attackSamples > 0 && i < attackSamples ->
                    blackmanHarrisRamp(i.toDouble() / attackSamples)
                releaseSamples > 0 && i >= numSamples - releaseSamples ->
                    blackmanHarrisRamp(((numSamples - 1) - i).toDouble() / releaseSamples)
                else -> 1.0
            }
            val amplitude = sin(angle) * MAX_AMPLITUDE * envelope
            buffer[globalIndex] = amplitude.toInt().toShort()
        }
    }

    private fun blackmanHarrisRamp(phase: Double): Double {
        if (phase <= 0.0) return 0.0
        if (phase >= 1.0) return 1.0
        val raw = BH_A0 -
            BH_A1 * cos(Math.PI * phase) +
            BH_A2 * cos(2.0 * Math.PI * phase) -
            BH_A3 * cos(3.0 * Math.PI * phase)
        return ((raw - BH_MIN) / (1.0 - BH_MIN)).coerceIn(0.0, 1.0)
    }

    /**
     * Generates a complete .WAV file (header + 16-bit PCM data)
     */
    fun generateWave(
        text: String,
        wpm: Int,
        farnsworthWpm: Int,
        frequency: Int,
        sampleRate: Int
    ): ByteArray {
        val morseCodeShorts = generate(text, wpm, farnsworthWpm, frequency, sampleRate)
        val dataSizeInBytes = morseCodeShorts.size * 2

        val byteBuffer = ByteBuffer.allocate(44 + dataSizeInBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Add WAV Header
        createWavHeader(byteBuffer, dataSizeInBytes, sampleRate)

        // Add PCM Data
        for (s in morseCodeShorts) {
            byteBuffer.putShort(s)
        }

        return byteBuffer.array()
    }

    private fun createWavHeader(buffer: ByteBuffer, dataSize: Int, sampleRate: Int) {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * 1 * 16 / 8 // sampleRate * numChannels * bitsPerSample/8

        buffer.put("RIFF".toByteArray())             // RIFF header
        buffer.putInt(totalDataLen)                  // Total size of the file minus 8 bytes
        buffer.put("WAVE".toByteArray())             // WAVE type
        buffer.put("fmt ".toByteArray())             // Format chunk marker
        buffer.putInt(16)                            // Length of format data
        buffer.putShort(1)                           // PCM format
        buffer.putShort(1)                           // Number of channels (1 for mono)
        buffer.putInt(sampleRate)                    // Sample rate
        buffer.putInt(byteRate)                      // Byte rate
        buffer.putShort(2)                           // Block align (numChannels * bitsPerSample/8)
        buffer.putShort(16)                          // Bits per sample (16 bits)

        buffer.put("data".toByteArray())             // Data chunk header
        buffer.putInt(dataSize)                      // Size of data section
    }

    private fun morseTiming(wpm: Int): Int {
        return 1200 / wpm
    }
}
