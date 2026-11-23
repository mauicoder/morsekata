package dev.mauicoder.morsekata.data

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainerProgressRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<TrainerProgress>
) : TrainerProgressRepository {

    private val TAG: String = "TrainerProgressRepo"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val trainerProgress: StateFlow<TrainerProgress> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading trainer progress.", exception)
                emit(TrainerProgress())
            } else {
                Log.e(TAG, "Other exception:", exception)
                throw exception
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = TrainerProgress()
        )

    override suspend fun updateProgress(transform: (TrainerProgress) -> TrainerProgress) {
        dataStore.updateData(transform)
    }
}
