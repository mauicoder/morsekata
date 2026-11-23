package dev.mauicoder.morsekata.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

private const val DATA_STORE_FILE_NAME = "user_prefs.json"
private const val TRAINER_PROGRESS_FILE_NAME = "trainer_progress.json"

@InstallIn(SingletonComponent::class)
@Module
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindTrainerProgressRepository(
        trainerProgressRepositoryImpl: TrainerProgressRepositoryImpl
    ): TrainerProgressRepository

    companion object {
        @Singleton
        @Provides
        fun provideDataStore(@ApplicationContext appContext: Context): DataStore<UserPreferences> {
            return DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                produceFile = { appContext.dataStoreFile(DATA_STORE_FILE_NAME) },
                corruptionHandler = null,
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            )
        }

        @Singleton
        @Provides
        fun provideTrainerProgressDataStore(@ApplicationContext appContext: Context): DataStore<TrainerProgress> {
            return DataStoreFactory.create(
                serializer = TrainerProgressSerializer,
                produceFile = { appContext.dataStoreFile(TRAINER_PROGRESS_FILE_NAME) },
                corruptionHandler = ReplaceFileCorruptionHandler { TrainerProgress() },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            )
        }

        @Singleton
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
