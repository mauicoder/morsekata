package net.maui.morselab.generator

interface AudioTrackSink {
    val playbackHeadPosition: Int
    fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int
    fun play()
    fun setVolume(gain: Float)
    fun pause()
    fun flush()
    fun stop()
    fun release()
}

data class AudioTrackSession(
    val sink: AudioTrackSink,
    val chunkSizeInShorts: Int
)

fun interface AudioTrackFactory {
    fun create(sampleRate: Int, totalShorts: Int): AudioTrackSession
}
