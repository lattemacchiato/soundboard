package de.lattemacchiato.soundboardng.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.InputStream
import java.io.OutputStream

object SoundLibrarySerializer : Serializer<SoundLibrary> {
    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue = SoundLibrary()

    override suspend fun readFrom(input: InputStream): SoundLibrary {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        try {
            val document =
                json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                    ?: throw SerializationException("Expected JSON object")
            // Legacy format: {"sounds":[...]}. Keep every sound and its original URI.
            if ("boards" !in document && "sounds" in document) {
                val sounds = json.decodeFromJsonElement<List<Sound>>(document.getValue("sounds"))
                return SoundLibrary(boards = listOf(SoundBoard(sounds = sounds)))
            }
            val library = json.decodeFromJsonElement<SoundLibrary>(document)
            if (library.boards.isEmpty() ||
                library.boards
                    .map { it.id }
                    .distinct()
                    .size != library.boards.size ||
                library.boards.any { it.id.isBlank() } ||
                library.boards.none { it.id == library.activeBoardId }
            ) {
                throw SerializationException("Invalid board structure")
            }
            return library
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw CorruptionException("Cannot read sound library; original file must be preserved", e)
        }
    }

    override suspend fun writeTo(
        t: SoundLibrary,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(SoundLibrary.serializer(), t).encodeToByteArray())
    }
}
