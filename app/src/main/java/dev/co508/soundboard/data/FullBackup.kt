package dev.co508.soundboard.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class FullBackupEnvelope(
    val format: String,
    val version: Int,
    val library: SoundLibrary,
)

data class FullBackupPreview(
    val source: Uri,
    val boardCount: Int,
    val soundCount: Int,
    val audioCount: Int,
    val audioBytes: Long,
)

/** Self-contained backup: ZIP with configuration.json and audio/ files. */
class FullBackup(private val context: Context, private val repository: SoundRepository) {
    private val resolver get() = context.contentResolver
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val library = repository.snapshot()
        val paths = linkedMapOf<String, String>()
        library.boards.flatMap { it.sounds }.forEach { sound ->
            if (!paths.containsKey(sound.uri)) {
                val type = resolver.getType(Uri.parse(sound.uri))
                val ext = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }
                    ?: Uri.parse(sound.uri).lastPathSegment?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }
                    ?: "bin"
                paths[sound.uri] = "audio/${paths.size.toString().padStart(5, '0')}.$ext"
            }
        }
        require(paths.size <= MAX_FILES) { "Too many audio files" }
        val portable = library.copy(boards = library.boards.map { board ->
            board.copy(sounds = board.sounds.map { it.copy(uri = paths.getValue(it.uri)) })
        })
        val config = json.encodeToString(FullBackupEnvelope.serializer(),
            FullBackupEnvelope(FORMAT, VERSION, portable)).toByteArray(Charsets.UTF_8)
        require(config.size <= MAX_CONFIG_BYTES) { "Configuration is too large" }
        resolver.openOutputStream(destination, "wt")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(CONFIG_ENTRY))
                zip.write(config)
                zip.closeEntry()
                var total = 0L
                for ((source, path) in paths) {
                    zip.putNextEntry(ZipEntry(path))
                    val input = resolver.openInputStream(Uri.parse(source))
                        ?: error("Cannot read audio: $source")
                    input.use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= MAX_AUDIO_BYTES) { "Backup exceeds audio size limit" }
                            zip.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot create backup")
    }

    suspend fun inspect(source: Uri): FullBackupPreview = withContext(Dispatchers.IO) {
        val scan = scan(source, null)
        FullBackupPreview(source, scan.library.boards.size,
            scan.library.boards.sumOf { it.sounds.size }, scan.audioCount, scan.audioBytes)
    }

    /** Validate and extract to a private staging directory, then switch the DataStore. */
    suspend fun restore(preview: FullBackupPreview) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "backup-audio").apply { mkdirs() }
        val stage = File(root, "stage-${UUID.randomUUID()}")
        val destination = File(root, "import-${UUID.randomUUID()}")
        require(stage.mkdirs()) { "Cannot create staging directory" }
        var committed = false
        try {
            val scan = scan(preview.source, stage)
            require(scan.audioCount == preview.audioCount && scan.audioBytes == preview.audioBytes) {
                "Backup changed since preview"
            }
            require(stage.renameTo(destination)) { "Cannot finalize imported audio" }
            val restored = scan.library.copy(boards = scan.library.boards.map { board ->
                board.copy(sounds = board.sounds.map { sound ->
                    val file = File(destination, sound.uri.removePrefix("audio/"))
                    sound.copy(uri = Uri.fromFile(file).toString())
                })
            })
            val previous = repository.snapshot()
            val recovery = File(context.filesDir, "soundboard-before-import.json")
            val tmp = File(context.filesDir, "soundboard-before-import.json.tmp")
            try {
                tmp.writeText(json.encodeToString(
                    ConfigurationRecoveryEnvelope.serializer(),
                    ConfigurationRecoveryEnvelope("dev.co508.soundboard.configuration", 1, previous)))
                require(tmp.renameTo(recovery)) { "Cannot save recovery configuration" }
            } finally { tmp.delete() }
            repository.restore(restored)
            committed = true
        } finally {
            stage.deleteRecursively()
            if (!committed) destination.deleteRecursively()
        }
    }

    private data class Scan(val library: SoundLibrary, val audioCount: Int, val audioBytes: Long)

    private fun scan(source: Uri, extractInto: File?): Scan {
        var config: ByteArray? = null
        val names = hashSetOf<String>()
        var count = 0
        var bytes = 0L
        resolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name)) { "Duplicate or invalid ZIP entry" }
                    val isConfig = entry.name == CONFIG_ENTRY
                    val isAudio = entry.name.matches(Regex("audio/[0-9]{5}\\.[a-zA-Z0-9]{1,8}"))
                    require(isConfig || isAudio) { "Unexpected ZIP entry: ${entry.name}" }
                    if (isAudio) {
                        count++
                        require(count <= MAX_FILES) { "Too many audio files" }
                    }
                    val output = if (isAudio && extractInto != null)
                        FileOutputStream(File(extractInto, entry.name.removePrefix("audio/"))) else null
                    val configBuffer = if (isConfig) java.io.ByteArrayOutputStream() else null
                    try {
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            if (isConfig) {
                                require(configBuffer!!.size() + n <= MAX_CONFIG_BYTES) { "Configuration too large" }
                                configBuffer.write(buffer, 0, n)
                            } else {
                                bytes += n
                                require(bytes <= MAX_AUDIO_BYTES) { "Audio data too large" }
                                output?.write(buffer, 0, n)
                            }
                        }
                    } finally { output?.close() }
                    if (isConfig) config = configBuffer!!.toByteArray()
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open ZIP backup")
        val envelope = try {
            json.decodeFromString(FullBackupEnvelope.serializer(),
                (config ?: error("Missing configuration.json")).toString(Charsets.UTF_8))
        } catch (e: Exception) { throw IllegalArgumentException("Invalid ZIP configuration: ${e.message}", e) }
        require(envelope.format == FORMAT && envelope.version == VERSION) { "Unsupported ZIP backup version" }
        val library = envelope.library
        require(library.boards.isNotEmpty() && library.boards.size <= 1000 &&
            library.boards.map { it.id }.distinct().size == library.boards.size &&
            library.boards.all { it.id.isNotBlank() && it.name.isNotBlank() && it.sounds.size <= 10000 } &&
            library.boards.any { it.id == library.activeBoardId } &&
            library.defaultFadeInMs in 0..5000 && library.defaultFadeOutMs in 0..5000) { "Invalid board configuration" }
        val refs = library.boards.flatMap { it.sounds }
        require(refs.all { it.id.isNotBlank() && it.name.isNotBlank() &&
            it.volumePercent in 0..100 && it.fadeInMs in 0..5000 && it.fadeOutMs in 0..5000 &&
            it.uri in names && it.uri.startsWith("audio/") } &&
            library.boards.all { board -> board.sounds.map { it.id }.distinct().size == board.sounds.size } &&
            names.filter { it.startsWith("audio/") }.toSet() == refs.map { it.uri }.toSet()) {
            "Audio files do not match configuration"
        }
        return Scan(library, count, bytes)
    }

    companion object {
        private const val FORMAT = "dev.co508.soundboard.full-backup"
        private const val VERSION = 1
        private const val CONFIG_ENTRY = "configuration.json"
        private const val MAX_FILES = 10000
        private const val MAX_CONFIG_BYTES = 10 * 1024 * 1024
        private const val MAX_AUDIO_BYTES = 2L * 1024 * 1024 * 1024
    }
}

@Serializable
private data class ConfigurationRecoveryEnvelope(
    val format: String,
    val version: Int,
    val library: SoundLibrary,
)
