package dev.mauicoder.morsekata.generator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import dev.mauicoder.morsekata.data.UserPreferences
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

    private sealed interface PlayerCommand {
        data class Play(val text: String, val preferences: UserPreferences) : PlayerCommand
        data object Stop : PlayerCommand
    }

    private val commandChannel = Channel<PlayerCommand>(capacity = Channel.UNLIMITED)

    init {
        scope.launch {
            commandChannel.receiveAsFlow().collectLatest { command ->
                when (command) {
                    is PlayerCommand.Play -> {
                        executePlayback(command.text, command.preferences)
                    }
                    is PlayerCommand.Stop -> {
                        Log.d(tag, "Playback idle (stopped)")
                    }
                }
            }
        }
    }

    override fun play(text: String, preferences: UserPreferences) {
        Log.d(tag, "play() requested: '$text' (wpm=${preferences.wpm}, freq=${preferences.frequency})")
        commandChannel.trySend(PlayerCommand.Play(text, preferences))
    }

    override fun stop() {
        Log.d(tag, "stop() requested")
        commandChannel.trySend(PlayerCommand.Stop)
    }

    private suspend fun executePlayback(text: String, preferences: UserPreferences) {
        try {
            val soundData = morseSoundGenerator.generate(
                text = text,
                wpm = preferences.wpm,
                farnsworthWpm = preferences.farnsworthWpm,
                frequency = preferences.frequency,
                sampleRate = sampleRate
            )
            playSound(soundData, sampleRate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error in playback coroutine for '$text'", e)
        }
    }

    private suspend fun playSound(soundData: ShortArray, sampleRate: Int) {
        if (soundData.isEmpty()) return

        val (audioTrack, chunkSizeInShorts) = audioTrackFactory.create(sampleRate, soundData.size)
        var started = false
        var completedNaturally = false

        try {
            var offset = 0
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

            val expectedDurationMs = (soundData.size * 1000L) / sampleRate
            val deadlineMs = System.currentTimeMillis() + expectedDurationMs + DRAIN_BUFFER_TIMEOUT_MARGIN_MS

            while (audioTrack.playbackHeadPosition < soundData.size && coroutineContext.isActive) {
                if (System.currentTimeMillis() >= deadlineMs) {
                    Log.w(tag, "Drain buffer timed out! headPos=${audioTrack.playbackHeadPosition}, total=${soundData.size}")
                    break
                }
                delay(DRAIN_POLL_INTERVAL_MS)
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
                } catch (e: Exception) {
                    Log.w(tag, "Error during AudioTrack teardown: ${e.message}")
                } finally {
                    audioTrack.release()
                }
            }
        }
    }

    companion object {
        private val FADE_OUT_GAIN_STEPS = floatArrayOf(0.5f, 0.2f, 0f)
        private const val FADE_OUT_STEP_DELAY_MS = 3L
        private const val DRAIN_BUFFER_TIMEOUT_MARGIN_MS = 2500L
        private const val DRAIN_POLL_INTERVAL_MS = 20L
        private const val MIN_CHUNK_SIZE_IN_SHORTS = 2048
        private const val MAX_BUFFER_MULTIPLIER = 4
    }

    private object AndroidAudioTrackFactory : AudioTrackFactory {
        override fun create(sampleRate: Int, totalShorts: Int): AudioTrackSession {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val totalBytes = totalShorts * 2
            val chunkSizeInShorts = minOf(totalShorts, maxOf(minBufferSize / 2, MIN_CHUNK_SIZE_IN_SHORTS))
            // Buffer must be at least minBufferSize. For short sounds, do not exceed totalBytes so the
            // buffer fills completely and triggers playback immediately on older APIs.
            // For longer sounds, allow up to minBufferSize * MAX_BUFFER_MULTIPLIER (~160ms) to ensure smooth streaming.
            val bufferSizeInBytes = maxOf(minBufferSize, minOf(totalBytes, minBufferSize * MAX_BUFFER_MULTIPLIER))

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

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val startThreshold = minOf(totalShorts, chunkSizeInShorts)
                track.setStartThresholdInFrames(startThreshold)
            }

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
