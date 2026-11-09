package de.lattemacchiato.soundboardng.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import de.lattemacchiato.soundboardng.audio.FolderAudioResolver
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
    val folderManifest: BackupFolderManifest? = null,
)

data class FullBackupPreview(
    val source: Uri,
    val boardCount: Int,
    val soundCount: Int,
    val audioCount: Int,
    val audioBytes: Long,
)

/** Self-contained backup: ZIP with configuration.json and audio/ files. */
class FullBackup(
    private val context: Context,
    private val repository: SoundRepository,
) {
    private val resolver get() = context.contentResolver
    private val folderResolver = FolderAudioResolver(context.contentResolver)
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    private data class BackupAudioSource(
        val source: Uri,
        val path: String,
    )

    suspend fun export(destination: Uri) =
        withContext(Dispatchers.IO) {
            val library = repository.snapshot()
            val paths = linkedMapOf<Pair<SoundType, String>, String>()
            val audioSources = mutableListOf<BackupAudioSource>()
            val folders = mutableListOf<BackupFolder>()
            val usedPaths = mutableSetOf<String>()

            fun extensionOf(
                uri: Uri,
                originalName: String? = null,
            ): String {
                val extensionFromName =
                    originalName
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }

                val extensionFromUri =
                    uri.lastPathSegment
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }

                if (extensionFromName != null) return extensionFromName
                if (extensionFromUri != null) return extensionFromUri

                return resolver
                    .getType(uri)
                    ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }
                    ?: "bin"
            }

            fun addAudio(
                source: Uri,
                path: String,
            ) {
                require(usedPaths.add(path)) { "Duplicate backup audio path" }
                require(audioSources.size < MAX_FILES) { "Too many audio files" }
                audioSources += BackupAudioSource(source, path)
            }

            for (sound in library.boards.flatMap { it.sounds }) {
                val key = sound.type to sound.uri
                if (key in paths) continue

                val sourceUri = Uri.parse(sound.uri)

                when (sound.type) {
                    SoundType.FILE -> {
                        val path =
                            "audio/${audioSources.size.toString().padStart(5, '0')}.${extensionOf(sourceUri)}"

                        addAudio(sourceUri, path)
                        paths[key] = path
                    }

                    SoundType.FOLDER -> {
                        val folderPath =
                            "folders/${folders.size.toString().padStart(5, '0')}"

                        val files = folderResolver.resolve(sourceUri)

                        val manifestFiles =
                            files.mapIndexed { index, file ->
                                val path =
                                    "$folderPath/${index.toString().padStart(
                                        5,
                                        '0',
                                    )}.${extensionOf(file.uri, file.name)}"

                                addAudio(file.uri, path)

                                BackupFolderFile(
                                    path = path,
                                    name = file.name,
                                )
                            }

                        folders +=
                            BackupFolder(
                                folderPath = folderPath,
                                files = manifestFiles,
                            )

                        paths[key] = folderPath
                    }
                }
            }

            val manifest = BackupFolderManifest(folders)

            BackupFolderValidator.validate(
                manifest,
                audioSources.map { it.path }.toSet(),
            )

            val portable =
                library.copy(
                    boards =
                        library.boards.map { board ->
                            board.copy(
                                sounds =
                                    board.sounds.map { sound ->
                                        sound.copy(uri = paths.getValue(sound.type to sound.uri))
                                    },
                            )
                        },
                )

            val config =
                json
                    .encodeToString(
                        FullBackupEnvelope.serializer(),
                        FullBackupEnvelope(
                            format = FORMAT,
                            version = 2,
                            library = portable,
                            folderManifest = manifest,
                        ),
                    ).toByteArray(Charsets.UTF_8)

            require(config.size <= MAX_CONFIG_BYTES) {
                "Configuration is too large"
            }

            resolver.openOutputStream(destination, "wt")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry(CONFIG_ENTRY))
                    zip.write(config)
                    zip.closeEntry()

                    var total = 0L

                    for (audio in audioSources) {
                        zip.putNextEntry(ZipEntry(audio.path))

                        val input =
                            resolver.openInputStream(audio.source)
                                ?: error("Cannot read audio: ${audio.source}")

                        input.use { stream ->
                            val buffer = ByteArray(64 * 1024)

                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break

                                total += n
                                require(total <= MAX_AUDIO_BYTES) {
                                    "Backup exceeds audio size limit"
                                }

                                zip.write(buffer, 0, n)
                            }
                        }

                        zip.closeEntry()
                    }
                }
            } ?: error("Cannot create backup")
        }

    suspend fun inspect(source: Uri): FullBackupPreview =
        withContext(Dispatchers.IO) {
            val scan = scan(source, null)
            FullBackupPreview(
                source,
                scan.library.boards.size,
                scan.library.boards.sumOf { it.sounds.size },
                scan.audioCount,
                scan.audioBytes,
            )
        }

    /** Validate and extract to a private staging directory, then switch the DataStore. */
    suspend fun restore(preview: FullBackupPreview) =
        withContext(Dispatchers.IO) {
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

                scan.folderManifest?.let { manifest ->
                    restoreFolderNames(stage, manifest)
                }

                require(stage.renameTo(destination)) { "Cannot finalize imported audio" }
                val restored =
                    scan.library.copy(
                        boards =
                            scan.library.boards.map { board ->
                                board.copy(
                                    sounds =
                                        board.sounds.map { sound ->
                                            val target =
                                                when (sound.type) {
                                                    SoundType.FILE ->
                                                        File(destination, sound.uri.removePrefix("audio/"))

                                                    SoundType.FOLDER ->
                                                        File(destination, sound.uri)
                                                }

                                            sound.copy(uri = Uri.fromFile(target).toString())
                                        },
                                )
                            },
                    )
                val previous = repository.snapshot()
                val recovery = File(context.filesDir, "soundboard-before-import.json")
                val tmp = File(context.filesDir, "soundboard-before-import.json.tmp")
                try {
                    tmp.writeText(
                        json.encodeToString(
                            ConfigurationRecoveryEnvelope.serializer(),
                            ConfigurationRecoveryEnvelope("dev.co508.soundboard.configuration", 1, previous),
                        ),
                    )
                    require(tmp.renameTo(recovery)) { "Cannot save recovery configuration" }
                } finally {
                    tmp.delete()
                }
                repository.restore(restored)
                committed = true
            } finally {
                stage.deleteRecursively()
                if (!committed) destination.deleteRecursively()
            }
        }

    private fun restoreFolderNames(
        stage: File,
        manifest: BackupFolderManifest,
    ) {
        for (folder in manifest.folders) {
            val directory = File(stage, folder.folderPath)
            require(directory.isDirectory || directory.mkdirs()) {
                "Cannot create restored folder"
            }

            val usedNames = mutableSetOf<String>()
            val renames = mutableListOf<Pair<File, File>>()

            for (file in folder.files) {
                val source = File(stage, file.path)
                require(source.isFile) {
                    "Missing extracted folder audio: ${file.path}"
                }

                val original = file.name
                val dot = original.lastIndexOf('.')
                val base = if (dot > 0) original.substring(0, dot) else original
                val extension = if (dot > 0) original.substring(dot) else ""

                var candidate = original
                var suffix = 2

                while (!usedNames.add(candidate.lowercase(java.util.Locale.ROOT))) {
                    candidate = "$base ($suffix)$extension"
                    suffix++
                }

                renames += source to File(directory, candidate)
            }

            // Move all extracted files out of the target directory first.
            val temporaryDirectory =
                File(directory, ".restore-${UUID.randomUUID()}")

            require(temporaryDirectory.mkdir()) {
                "Cannot create temporary restore directory"
            }

            val temporary =
                renames.mapIndexed { index, (source, target) ->
                    val temp = File(temporaryDirectory, index.toString())

                    require(source.renameTo(temp)) {
                        "Cannot prepare restored audio file"
                    }

                    temp to target
                }

            for ((source, target) in temporary) {
                require(!target.exists()) {
                    "Restored audio filename already exists: ${target.name}"
                }

                require(source.renameTo(target)) {
                    "Cannot restore original audio filename"
                }
            }

            require(temporaryDirectory.delete()) {
                "Cannot remove temporary restore directory"
            }
        }
    }

    private data class Scan(
        val library: SoundLibrary,
        val audioCount: Int,
        val audioBytes: Long,
        val folderManifest: BackupFolderManifest? = null,
    )

    private fun scan(
        source: Uri,
        extractInto: File?,
    ): Scan {
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
                    val isAudio =
                        entry.name.matches(
                            Regex("audio/[0-9]{5}\\.[a-zA-Z0-9]{1,8}"),
                        )
                    val isFolderAudio =
                        entry.name.matches(
                            Regex("folders/[0-9]{5}/[0-9]{5}\\.[a-zA-Z0-9]{1,8}"),
                        )

                    require(isConfig || isAudio || isFolderAudio) {
                        "Unexpected ZIP entry: ${entry.name}"
                    }

                    if (isAudio || isFolderAudio) {
                        count++
                        require(count <= MAX_FILES) { "Too many audio files" }
                    }
                    val output =
                        if ((isAudio || isFolderAudio) && extractInto != null) {
                            val relativePath =
                                if (isAudio) {
                                    entry.name.removePrefix("audio/")
                                } else {
                                    entry.name
                                }

                            val target = File(extractInto, relativePath)

                            require(
                                target.parentFile?.isDirectory == true ||
                                    target.parentFile?.mkdirs() == true,
                            ) {
                                "Cannot create backup extraction directory"
                            }

                            FileOutputStream(target)
                        } else {
                            null
                        }
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
                    } finally {
                        output?.close()
                    }
                    if (isConfig) config = configBuffer!!.toByteArray()
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open ZIP backup")
        val envelope =
            try {
                json.decodeFromString(
                    FullBackupEnvelope.serializer(),
                    (config ?: error("Missing configuration.json")).toString(Charsets.UTF_8),
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid ZIP configuration: ${e.message}", e)
            }
        require(
            envelope.format == FORMAT &&
                envelope.version in 1..VERSION,
        ) {
            "Unsupported ZIP backup version"
        }

        if (envelope.version == 2) {
            require(envelope.folderManifest != null) {
                "Missing folder manifest in ZIP backup"
            }
        }

        if (envelope.version == 2) {
            BackupFolderValidator.validate(
                manifest = requireNotNull(envelope.folderManifest),
                zipEntries = names,
            )
        } else {
            require(names.none { it.startsWith("folders/") }) {
                "Folder entries are not supported in ZIP backup version 1"
            }
        }

        val library = envelope.library
        require(
            library.boards.isNotEmpty() &&
                library.boards.size <= 1000 &&
                library.boards
                    .map { it.id }
                    .distinct()
                    .size == library.boards.size &&
                library.boards.all { it.id.isNotBlank() && it.name.isNotBlank() && it.sounds.size <= 10000 } &&
                library.boards.any { it.id == library.activeBoardId } &&
                library.defaultFadeInMs in 0..5000 &&
                library.defaultFadeOutMs in 0..5000,
        ) { "Invalid board configuration" }

        val folderPaths =
            envelope.folderManifest
                ?.folders
                ?.map { it.folderPath }
                ?.toSet()
                ?: emptySet()

        val refs = library.boards.flatMap { it.sounds }
        require(
            refs.all {
                it.id.isNotBlank() &&
                    it.name.isNotBlank() &&
                    it.volumePercent in 0..100 &&
                    it.fadeInMs in 0..5000 &&
                    it.fadeOutMs in 0..5000 &&
                    when (it.type) {
                        SoundType.FILE ->
                            it.uri in names && it.uri.startsWith("audio/")

                        SoundType.FOLDER ->
                            envelope.version == 2 && it.uri in folderPaths
                    }
            } &&
                library.boards.all { board ->
                    board.sounds
                        .map { it.id }
                        .distinct()
                        .size == board.sounds.size
                } &&
                names.filter { it.startsWith("audio/") }.toSet() ==
                refs.filter { it.type == SoundType.FILE }.map { it.uri }.toSet() &&
                folderPaths ==
                refs.filter { it.type == SoundType.FOLDER }.map { it.uri }.toSet(),
        ) {
            "Audio files do not match configuration"
        }
        return Scan(
            library = library,
            audioCount = count,
            audioBytes = bytes,
            folderManifest = envelope.folderManifest,
        )
    }

    companion object {
        private const val FORMAT = "dev.co508.soundboard.full-backup"
        private const val VERSION = 2
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
