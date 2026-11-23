package dev.mauicoder.morsekata.generator

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindMorseAudioPlayer(
        morseAudioPlayerImpl: MorseAudioPlayerImpl
    ): MorseAudioPlayer
}
