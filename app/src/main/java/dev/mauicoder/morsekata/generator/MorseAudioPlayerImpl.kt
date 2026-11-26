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
                sampleRate = sampleRate,
                leadingSilenceMs = MorseSoundGenerator.PLAYBACK_LEADING_SILENCE_MS,
                trailingSilenceMs = MorseSoundGenerator.PLAYBACK_TRAILING_SILENCE_MS
            )
            Log.d(tag, "Generated soundData for '$text': size=${soundData.size} samples (${(soundData.size * 1000L) / sampleRate}ms)")
            playSound(soundData, sampleRate)
        } catch (e: CancellationException) {
            Log.d(tag, "Playback coroutine cancelled for '$text'")
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error in playback coroutine for '$text'", e)
        }
    }

    private suspend fun playSound(soundData: ShortArray, sampleRate: Int) {
        if (soundData.isEmpty()) {
            Log.w(tag, "playSound: soundData is empty, returning")
            return
        }

        val (audioTrack, chunkSizeInShorts) = audioTrackFactory.create(sampleRate, soundData.size)
        var started = false
        var completedNaturally = false

        try {
            var offset = 0
            val initialShorts = minOf(soundData.size, chunkSizeInShorts)
            val initialWritten = audioTrack.write(soundData, 0, initialShorts)
            Log.d(tag, "Initial write: requested=$initialShorts, written=$initialWritten")
            if (initialWritten > 0) {
                offset += initialWritten
            }

            Log.d(tag, "Calling audioTrack.play()")
            audioTrack.play()
            started = true

            while (offset < soundData.size && coroutineContext.isActive) {
                val shortsToWrite = minOf(soundData.size - offset, chunkSizeInShorts)
                val result = audioTrack.write(soundData, offset, shortsToWrite)
                if (result <= 0) {
                    Log.w(tag, "audioTrack.write returned non-positive: $result at offset $offset")
                    break
                }
                offset += result
                yield()
            }
            Log.d(tag, "Write loop finished: total written=$offset / ${soundData.size}")

            val expectedDurationMs = (soundData.size * 1000L) / sampleRate
            val timeoutMs = expectedDurationMs + DRAIN_BUFFER_TIMEOUT_MARGIN_MS
            val deadlineMs = System.currentTimeMillis() + timeoutMs

            val fastForwardDelayMs = (expectedDurationMs - 150L).coerceAtLeast(0L)
            if (fastForwardDelayMs > 0) {
                delay(fastForwardDelayMs)
            }

            while (audioTrack.playbackHeadPosition < soundData.size && coroutineContext.isActive) {
                if (System.currentTimeMillis() >= deadlineMs) {
                    Log.w(tag, "Drain buffer timed out! headPos=${audioTrack.playbackHeadPosition}, total=${soundData.size}")
                    break
                }
                delay(20)
            }
            if (coroutineContext.isActive) {
                completedNaturally = true
                Log.d(tag, "Playback completed naturally. headPos=${audioTrack.playbackHeadPosition}")
            }
        } catch (e: CancellationException) {
            Log.d(tag, "playSound cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error during AudioTrack playback", e)
        } finally {
            Log.d(tag, "Entering finally block: started=$started, completedNaturally=$completedNaturally")
            withContext(NonCancellable) {
                try {
                    if (started && !completedNaturally) {
                        Log.d(tag, "Fading out AudioTrack before release")
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
                    Log.d(tag, "AudioTrack released.")
                }
            }
        }
    }

    companion object {
        private val FADE_OUT_GAIN_STEPS = floatArrayOf(0.5f, 0.2f, 0f)
        private const val FADE_OUT_STEP_DELAY_MS = 3L
        private const val DRAIN_BUFFER_TIMEOUT_MARGIN_MS = 500L
    }

    private object AndroidAudioTrackFactory : AudioTrackFactory {
        override fun create(sampleRate: Int, totalShorts: Int): AudioTrackSession {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Ensure a robust buffer for Bluetooth A2DP & modern Android audio pipelines (at least 64KB / ~740ms)
            val bufferSizeInBytes = maxOf(minBufferSize * 8, 65536)
            val chunkSizeInShorts = (minBufferSize * 2).coerceAtLeast(4096)

            Log.d("MorseAudioPlayerImpl", "Creating AudioTrack: sampleRate=$sampleRate, totalShorts=$totalShorts, minBufferSize=$minBufferSize, bufferSizeInBytes=$bufferSizeInBytes, chunkSizeInShorts=$chunkSizeInShorts")

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

            Log.d("MorseAudioPlayerImpl", "AudioTrack created: state=${track.state} (STATE_INITIALIZED=${AudioTrack.STATE_INITIALIZED})")

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
