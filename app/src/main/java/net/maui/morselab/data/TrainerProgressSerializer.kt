package net.maui.morselab.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object TrainerProgressSerializer : Serializer<TrainerProgress> {

    override val defaultValue: TrainerProgress = TrainerProgress()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): TrainerProgress {
        try {
            return json.decodeFromString(
                TrainerProgress.serializer(),
                input.readBytes().decodeToString()
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read TrainerProgress", serialization)
        }
    }

    override suspend fun writeTo(t: TrainerProgress, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                json.encodeToString(TrainerProgress.serializer(), t).encodeToByteArray()
            )
        }
    }
}
