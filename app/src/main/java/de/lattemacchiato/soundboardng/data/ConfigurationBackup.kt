package de.lattemacchiato.soundboardng.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

data class UnavailableAudioReference(
    val boardId: String,
    val boardName: String,
    val soundName: String,
)

data class BackupPreview(
    val library: SoundLibrary,
    val unavailableSounds: List<UnavailableAudioReference>,
) {
    val boardCount: Int get() = library.boards.size
    val soundCount: Int get() = library.boards.sumOf { it.sounds.size }
}

/** JSON-only portable envelope. Audio is intentionally not included in this format. */
internal class ConfigurationBackup(
    private val context: Context,
    private val repository: SoundRepository,
) {
    private val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun export(
        destination: Uri,
        selectedBoardIds: Set<String>? = null,
    ) = withContext(Dispatchers.IO) {
        val exportedLibrary =
            BackupBoardSelector.select(
                library = repository.snapshot(),
                selectedBoardIds = selectedBoardIds,
            )

        ConfigurationValidator.validate(exportedLibrary)

        val payload =
            json.encodeToString(
                ConfigurationEnvelope.serializer(),
                ConfigurationEnvelope(FORMAT, VERSION, exportedLibrary),
            )

        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) {
            "Configuration exceeds size limit"
        }

        resolver.openOutputStream(destination, "wt")?.use {
            it.write(bytes)
        } ?: error("Cannot open export destination")
    }

    suspend fun inspect(source: Uri): BackupPreview =
        withContext(Dispatchers.IO) {
            val bytes =
                resolver.openInputStream(source)?.use { stream ->
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
            val envelope =
                try {
                    json.decodeFromString<ConfigurationEnvelope>(bytes.toString(Charsets.UTF_8))
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("Not a valid soundboard configuration backup", e)
                }
            require(envelope.format == FORMAT && envelope.version == VERSION) { "Unsupported backup format or version" }
            ConfigurationValidator.validate(envelope.library)
            val missing =
                envelope.library.boards.flatMap { board ->
                    board.sounds.mapNotNull { sound ->
                        val unavailable =
                            try {
                                val uri = Uri.parse(sound.uri)

                                when (sound.type) {
                                    SoundType.FILE ->
                                        when (uri.scheme) {
                                            "file" -> {
                                                val path = uri.path
                                                path == null || !File(path).isFile || !File(path).canRead()
                                            }
                                            "content" -> resolver.openFileDescriptor(uri, "r")?.use {} == null
                                            else -> true
                                        }

                                    SoundType.FOLDER ->
                                        when (uri.scheme) {
                                            "file" -> {
                                                val path = uri.path
                                                path == null || !File(path).isDirectory || !File(path).canRead()
                                            }
                                            "content" -> DocumentFile.fromTreeUri(context, uri)?.exists() != true
                                            else -> true
                                        }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                true
                            }

                        if (unavailable) {
                            UnavailableAudioReference(
                                boardId = board.id,
                                boardName = board.name,
                                soundName = sound.name,
                            )
                        } else {
                            null
                        }
                    }
                }
            BackupPreview(envelope.library, missing)
        }

    /**
     * Saves a private recovery copy before importing selected boards.
     * JSON imports never delete audio files.
     */
    suspend fun restore(
        preview: BackupPreview,
        selectedBoardIds: Set<String>,
        mode: BoardImportMerger.Mode,
    ) = withContext(Dispatchers.IO) {
        ConfigurationValidator.validate(preview.library)

        // Validate the selection before creating a recovery backup.
        BackupBoardSelector.select(
            library = preview.library,
            selectedBoardIds = selectedBoardIds,
        )

        repository.withAudioMutationLock {
            val previous = repository.snapshot()

            val recoveryPayload =
                json.encodeToString(
                    ConfigurationEnvelope.serializer(),
                    ConfigurationEnvelope(FORMAT, VERSION, previous),
                )

            ConfigurationRecovery.save(
                context = context,
                payload = recoveryPayload,
            )

            repository.importBoards(
                imported = preview.library,
                selectedBoardIds = selectedBoardIds,
                mode = mode,
            )
        }
    }

    companion object {
        private const val FORMAT = "dev.co508.soundboard.configuration"
        private const val VERSION = 1
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}
