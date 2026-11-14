package net.maui.morselab.generator

import net.maui.morselab.utils.MorseCodeMaps
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class MorseSoundGenerator {

    companion object {
        const val PEAK_AMPLITUDE_FACTOR = 0.75
        const val MAX_AMPLITUDE = (Short.MAX_VALUE * PEAK_AMPLITUDE_FACTOR).toInt() // 24575
        const val FADE_DURATION_MS = 5
        const val MAX_FADE_RATIO = 0.40
        const val PADDING_SILENCE_MS = 20
    }

    /**
     * Generates a 16-bit PCM Mono audio stream for the given text.
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
        val intraCharacterPause = createSilence(dotDuration * sampleRate / 1000)

        val farnsworthDotDuration = morseTiming(farnsworthWpm)
        val interCharacterPause = createSilence(3 * farnsworthDotDuration * sampleRate / 1000)
        val interWordPause = createSilence(7 * farnsworthDotDuration * sampleRate / 1000)
        val paddingSilence = createSilence(PADDING_SILENCE_MS * sampleRate / 1000)

        val dotWave = generateSineWave(frequency, dotDuration, sampleRate)
        val dashWave = generateSineWave(frequency, dashDuration, sampleRate)

        val morseSoundList = mutableListOf<ShortArray>()
        morseSoundList.add(paddingSilence)

        validWords.forEachIndexed { wordIndex, wordCharacters ->
            if (wordIndex > 0) {
                morseSoundList.add(interWordPause)
            }
            wordCharacters.forEachIndexed { charIndex, morseCode ->
                if (charIndex > 0) {
                    morseSoundList.add(interCharacterPause)
                }
                morseCode.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) {
                        morseSoundList.add(intraCharacterPause)
                    }
                    when (symbol) {
                        '.' -> morseSoundList.add(dotWave)
                        '-' -> morseSoundList.add(dashWave)
                    }
                }
            }
        }

        morseSoundList.add(paddingSilence)

        return concatenateSounds(*morseSoundList.toTypedArray())
    }

    private fun createSilence(numSamples: Int): ShortArray {
        return ShortArray(numSamples)
    }

    private fun generateSineWave(
        frequency: Int,
        durationMs: Int,
        sampleRate: Int
    ): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        if (numSamples <= 0) return ShortArray(0)

        val targetFadeSamples = FADE_DURATION_MS * sampleRate / 1000
        val maxFadeSamples = (numSamples * MAX_FADE_RATIO).toInt()
        val fadeSamples = minOf(targetFadeSamples, maxFadeSamples)

        val sample = ShortArray(numSamples)

        for (i in sample.indices) {
            val angle = 2.0 * Math.PI * i * frequency / sampleRate
            var amplitude = sin(angle) * MAX_AMPLITUDE

            val envelope = when {
                fadeSamples > 0 && i < fadeSamples ->
                    (1.0 - cos(i * Math.PI / fadeSamples)) / 2.0
                fadeSamples > 0 && i >= numSamples - fadeSamples ->
                    (1.0 - cos(((numSamples - 1) - i) * Math.PI / fadeSamples)) / 2.0
                else -> 1.0
            }
            amplitude *= envelope

            sample[i] = amplitude.toInt().toShort()
        }
        return sample
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

    private fun concatenateSounds(vararg sounds: ShortArray): ShortArray {
        val totalLength = sounds.sumOf { it.size }
        val result = ShortArray(totalLength)
        var offset = 0
        for (sound in sounds) {
            System.arraycopy(sound, 0, result, offset, sound.size)
            offset += sound.size
        }
        return result
    }

    private fun morseTiming(wpm: Int): Int {
        return 1200 / wpm
    }
}
