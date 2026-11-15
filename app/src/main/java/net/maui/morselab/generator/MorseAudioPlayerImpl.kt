package net.maui.morselab.generator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.maui.morselab.data.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class MorseAudioPlayerImpl(
    private val morseSoundGenerator: MorseSoundGenerator,
    private val audioTrackFactory: AudioTrackFactory,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : MorseAudioPlayer {

    @Inject
    constructor() : this(
        morseSoundGenerator = MorseSoundGenerator(),
        audioTrackFactory = AndroidAudioTrackFactory,
        dispatcher = Dispatchers.Default
    )

    private val sampleRate = 44100
    private val tag = "MorseAudioPlayerImpl"
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var playJob: Job? = null

    override fun play(text: String, preferences: UserPreferences) {
        stop()

        playJob = scope.launch {
            try {
                val soundData = morseSoundGenerator.generate(
                    text,
                    preferences.wpm,
                    preferences.farnsworthWpm,
                    preferences.frequency,
                    sampleRate
                )
                playSound(soundData, sampleRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error in playback coroutine", e)
            }
        }
    }

    override fun stop() {
        playJob?.cancel()
        playJob = null
    }

    private suspend fun playSound(soundData: ShortArray, sampleRate: Int) {
        if (soundData.isEmpty()) return

        val (audioTrack, chunkSizeInShorts) = audioTrackFactory.create(sampleRate, soundData.size)
        var started = false
        var completedNaturally = false

        try {
            var offset = 0
            // Prime the buffer with the initial chunk before starting playback to prevent underrun clicks
            val initialShorts = minOf(soundData.size, chunkSizeInShorts)
            val initialWritten = audioTrack.write(soundData, 0, initialShorts)
            if (initialWritten > 0) {
                offset += initialWritten
            }

            audioTrack.play()
            started = true

            while (offset < soundData.size && coroutineContext.isActive) {
                val shortsToWrite = minOf(soundData.size - offset, chunkSizeInShorts)
                val result = audioTrack.write(soundData, offset, shortsToWrite)
                if (result <= 0) break
                offset += result
                yield()
            }

            // Wait for the hardware to finish playing
            while (audioTrack.playbackHeadPosition < soundData.size && coroutineContext.isActive) {
                delay(50)
            }
            if (coroutineContext.isActive) {
                completedNaturally = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error during AudioTrack playback", e)
        } finally {
            withContext(NonCancellable) {
                try {
                    if (started && !completedNaturally) {
                        for (gain in FADE_OUT_GAIN_STEPS) {
                            audioTrack.setVolume(gain)
                            delay(FADE_OUT_STEP_DELAY_MS)
                        }
                        audioTrack.pause()
                        audioTrack.flush()
                    }
                    audioTrack.stop()
                } catch (_: Exception) {
                } finally {
                    audioTrack.release()
                }
            }
        }
    }

    companion object {
        private val FADE_OUT_GAIN_STEPS = floatArrayOf(0.5f, 0.2f, 0f)
        private const val FADE_OUT_STEP_DELAY_MS = 3L
    }

    private object AndroidAudioTrackFactory : AudioTrackFactory {
        override fun create(sampleRate: Int, totalShorts: Int): AudioTrackSession {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val chunkSizeInShorts = (minBufferSize / 2).coerceAtLeast(512)
            val chunkSizeInBytes = chunkSizeInShorts * 2
            val bufferSizeInBytes = maxOf(minBufferSize, chunkSizeInBytes * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val sink = object : AudioTrackSink {
                override val playbackHeadPosition: Int
                    get() = track.playbackHeadPosition

                override fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int =
                    track.write(audioData, offsetInShorts, sizeInShorts)

                override fun play() = track.play()

                override fun setVolume(gain: Float) {
                    track.setVolume(gain)
                }

                override fun pause() = track.pause()

                override fun flush() = track.flush()

                override fun stop() = track.stop()

                override fun release() = track.release()
            }
            return AudioTrackSession(sink = sink, chunkSizeInShorts = chunkSizeInShorts)
        }
    }
}
