package dev.mauicoder.morsekata.generator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import dev.mauicoder.morsekata.data.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MorseAudioPlayerImplTest {

    private class FakeAudioTrackSink(
        private val autoAdvanceHead: Boolean = true,
        private val onPlayLatch: CountDownLatch? = null,
        private val onReleaseLatch: CountDownLatch? = null
    ) : AudioTrackSink {
        val events = CopyOnWriteArrayList<String>()
        val writtenSamples = CopyOnWriteArrayList<Short>()
        val volumeHistory = CopyOnWriteArrayList<Float>()
        @Volatile
        override var playbackHeadPosition: Int = 0

        override fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
            events.add("write:$sizeInShorts")
            for (i in offsetInShorts until (offsetInShorts + sizeInShorts)) {
                writtenSamples.add(audioData[i])
            }
            if (autoAdvanceHead) {
                playbackHeadPosition = writtenSamples.size
            }
            return sizeInShorts
        }

        override fun play() {
            events.add("play")
            onPlayLatch?.countDown()
        }

        override fun setVolume(gain: Float) {
            events.add("setVolume:$gain")
            volumeHistory.add(gain)
        }

        override fun pause() {
            events.add("pause")
        }

        override fun flush() {
            events.add("flush")
        }

        override fun stop() {
            events.add("stop")
        }

        override fun release() {
            events.add("release")
            onReleaseLatch?.countDown()
        }
    }

    @Test
    fun `play does not create AudioTrackSink when generated audio is empty`() = runBlocking {
        var factoryCalls = 0
        val player = MorseAudioPlayerImpl(
            morseSoundGenerator = MorseSoundGenerator(),
            audioTrackFactory = { _, _ ->
                factoryCalls++
                AudioTrackSession(FakeAudioTrackSink(), 1024)
            },
            dispatcher = Dispatchers.Unconfined
        )

        player.play("   ", UserPreferences())

        assertEquals(0, factoryCalls)
    }

    @Test
    fun `play primes buffer with initial write before calling play and writes all samples`() = runBlocking {
        val releaseLatch = CountDownLatch(1)
        val sink = FakeAudioTrackSink(autoAdvanceHead = true, onReleaseLatch = releaseLatch)
        val generator = MorseSoundGenerator()
        val prefs = UserPreferences(frequency = 700, wpm = 20, farnsworthWpm = 20)
        val expectedSamples = generator.generate("E", prefs.wpm, prefs.farnsworthWpm, prefs.frequency, 44100)

        val player = MorseAudioPlayerImpl(
            morseSoundGenerator = generator,
            audioTrackFactory = { _, _ -> AudioTrackSession(sink, 1024) },
            dispatcher = Dispatchers.Default
        )

        player.play("E", prefs)
        assertTrue("Expected playback to finish and release", releaseLatch.await(2, TimeUnit.SECONDS))

        val firstWriteIndex = sink.events.indexOfFirst { it.startsWith("write:") }
        val playIndex = sink.events.indexOf("play")

        assertTrue("Expected write to be called", firstWriteIndex >= 0)
        assertTrue("Expected play to be called", playIndex >= 0)
        assertTrue(
            "Expected buffer to be primed with write ($firstWriteIndex) before play ($playIndex), events=${sink.events}",
            firstWriteIndex < playIndex
        )
        assertEquals(expectedSamples.size, sink.writtenSamples.size)
        assertTrue("Expected stop and release at end of playback", sink.events.contains("stop") && sink.events.contains("release"))
    }

    @Test
    fun `stop mid-playback ramps volume down to 0f and pauses flushes stops and releases track`() = runBlocking {
        val playLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val sink = FakeAudioTrackSink(
            autoAdvanceHead = false,
            onPlayLatch = playLatch,
            onReleaseLatch = releaseLatch
        )
        val player = MorseAudioPlayerImpl(
            morseSoundGenerator = MorseSoundGenerator(),
            audioTrackFactory = { _, _ -> AudioTrackSession(sink, 2048) },
            dispatcher = Dispatchers.Default
        )

        player.play("SOS", UserPreferences())
        assertTrue("Expected play() to be reached", playLatch.await(2, TimeUnit.SECONDS))

        player.stop()
        assertTrue("Expected release() to be reached after stop()", releaseLatch.await(2, TimeUnit.SECONDS))

        assertTrue(
            "Expected volume ramp down to 0f on stop, got ${sink.volumeHistory}",
            sink.volumeHistory.isNotEmpty() && sink.volumeHistory.last() == 0f
        )
        val pauseIdx = sink.events.indexOf("pause")
        val flushIdx = sink.events.indexOf("flush")
        val stopIdx = sink.events.indexOf("stop")
        val releaseIdx = sink.events.indexOf("release")

        assertTrue("Expected pause before stop, events=${sink.events}", pauseIdx in 0 until stopIdx)
        assertTrue("Expected flush before release, events=${sink.events}", flushIdx in 0 until releaseIdx)
        assertTrue("Expected stop before release, events=${sink.events}", stopIdx in 0 until releaseIdx)
    }
}
