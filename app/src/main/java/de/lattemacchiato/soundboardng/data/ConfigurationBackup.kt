package de.lattemacchiato.soundboardng.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ConfigurationEnvelope(
    val format: String,
    val version: Int,
    val library: SoundLibrary,
)

data class BackupPreview(val library: SoundLibrary, val unavailableSounds: List<String>) {
    val boardCount: Int get() = library.boards.size
    val soundCount: Int get() = library.boards.sumOf { it.sounds.size }
}

/** JSON-only portable envelope. Audio is intentionally not included in this format. */
class ConfigurationBackup(private val context: Context, private val repository: SoundRepository) {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            ConfigurationEnvelope.serializer(),
            ConfigurationEnvelope(FORMAT, VERSION, repository.snapshot()),
        )
        require(payload.toByteArray().size <= MAX_BYTES) { "Configuration exceeds size limit" }
        resolver.openOutputStream(destination, "wt")?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot open export destination")
    }

    suspend fun inspect(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(source)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                require(buffer.size() + count <= MAX_BYTES) { "Backup is too large" }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        } ?: error("Cannot open backup")
        val envelope = try {
            json.decodeFromString<ConfigurationEnvelope>(bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Not a valid soundboard configuration backup", e)
        }
        require(envelope.format == FORMAT && envelope.version == VERSION) { "Unsupported backup format or version" }
        ConfigurationValidator.validate(envelope.library)
        val missing = envelope.library.boards.flatMap { it.sounds }.filter { sound ->
            try {
                resolver.openFileDescriptor(Uri.parse(sound.uri), "r")?.use { } == null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { true }
        }.map { it.name }
        BackupPreview(envelope.library, missing)
    }

    /** Save a private recovery copy before replacing DataStore; retain it after success. */
    suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) {
        ConfigurationValidator.validate(preview.library)
        val previous = repository.snapshot()
        val recovery = File(context.filesDir, "soundboard-before-import.json")
        val tmp = File(context.filesDir, "soundboard-before-import.json.tmp")
        try {
            tmp.writeText(json.encodeToString(
                ConfigurationEnvelope.serializer(),
                ConfigurationEnvelope(FORMAT, VERSION, previous),
            ))
            if (!tmp.renameTo(recovery)) error("Cannot save pre-import recovery backup")
            repository.restore(preview.library)
        } finally { tmp.delete() }
    }

    companion object {
        private const val FORMAT = "dev.co508.soundboard.configuration"
        private const val VERSION = 1
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}
