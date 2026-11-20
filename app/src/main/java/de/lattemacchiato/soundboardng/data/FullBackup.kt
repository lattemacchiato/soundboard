package de.lattemacchiato.soundboardng.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import de.lattemacchiato.soundboardng.audio.FolderAudioResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
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

    private fun originalFolderName(uri: Uri): String {
        val name =
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).name }
                "content" -> DocumentFile.fromTreeUri(context, uri)?.name
                else -> null
            }

        return name
            ?.takeIf {
                it.isNotBlank() &&
                    it != "." &&
                    it != ".." &&
                    '/' !in it &&
                    '\\' !in it &&
                    it.none { char -> char.isISOControl() }
            }
            ?: error("Cannot determine original audio folder name")
    }

    private fun requireExternalFolder(uri: Uri): DocumentFile {
        require(
            uri.scheme == "content" &&
                uri.authority == "com.android.externalstorage.documents",
        ) {
            "Only local device storage or SD cards are supported for audio import"
        }
        val folder =
            DocumentFile.fromTreeUri(context, uri)
                ?: error("Cannot access selected external folder")

        require(folder.isDirectory && folder.canRead() && folder.canWrite()) {
            "Selected external folder is not readable and writable"
        }

        return folder
    }

    private fun folderUriWithinTree(
        selectedTreeUri: Uri,
        folder: DocumentFile,
    ): Uri {
        val documentId = DocumentsContract.getDocumentId(folder.uri)

        return DocumentsContract.buildDocumentUriUsingTree(
            selectedTreeUri,
            documentId,
        )
    }

    private fun createExternalFolder(
        parent: DocumentFile,
        preferredName: String,
        createdFolders: MutableList<DocumentFile>,
    ): DocumentFile {
        val existingDocuments = parent.listFiles()

        val existing =
            existingDocuments.firstOrNull {
                it.name.equals(preferredName, ignoreCase = true)
            }

        if (existing != null) {
            require(existing.isDirectory) {
                "External storage entry is not a directory: $preferredName"
            }
            require(existing.canRead() && existing.canWrite()) {
                "Existing external audio folder is not readable and writable: $preferredName"
            }
            return existing
        }

        val existingIds =
            existingDocuments
                .map { DocumentsContract.getDocumentId(it.uri) }
                .toSet()

        val folder =
            parent.createDirectory(preferredName)
                ?: error("Cannot create external audio folder: $preferredName")

        check(DocumentsContract.getDocumentId(folder.uri) !in existingIds) {
            "Storage provider returned an existing directory instead of creating a new one"
        }

        createdFolders.add(folder)
        return folder
    }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }

        return digest.digest()
    }

    private fun hasSameContent(
        source: File,
        target: DocumentFile,
    ): Boolean {
        require(source.isFile) {
            "Missing staged audio file: ${source.name}"
        }

        require(target.isFile) {
            "Existing repository entry is not a file: ${target.name}"
        }

        val targetSize = target.length()

        // Some SAF providers report 0 when the file size is unknown.
        if (targetSize > 0L && source.length() != targetSize) {
            return false
        }

        val sourceHash = source.inputStream().use(::sha256)
        val targetHash =
            (
                resolver.openInputStream(target.uri)
                    ?: error("Cannot read existing audio: ${target.name}")
            ).use(::sha256)

        return sourceHash.contentEquals(targetHash)
    }

    private fun numberedFilename(
        original: String,
        suffix: Int,
    ): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""

        return "$base ($suffix)$extension"
    }

    private fun storeExternalAudio(
        source: File,
        directory: DocumentFile,
        originalFilename: String,
        createdFiles: MutableList<DocumentFile>,
    ): Uri {
        require(source.isFile) {
            "Missing staged audio file: ${source.name}"
        }

        var filename = originalFilename
        var suffix = 1

        while (true) {
            val existingDocuments = directory.listFiles()
            val existingIds =
                existingDocuments
                    .map { DocumentsContract.getDocumentId(it.uri) }
                    .toSet()

            val existing =
                existingDocuments.firstOrNull {
                    it.name.equals(filename, ignoreCase = true)
                }

            if (existing == null) {
                val extension = filename.substringAfterLast('.', "").lowercase()
                val mimeType =
                    android.webkit.MimeTypeMap
                        .getSingleton()
                        .getMimeTypeFromExtension(extension)
                        ?.takeIf { it.startsWith("audio/", ignoreCase = true) }
                        ?: "application/octet-stream"

                val target =
                    directory.createFile(
                        mimeType,
                        filename,
                    ) ?: error("Cannot create external audio file: $filename")

                check(DocumentsContract.getDocumentId(target.uri) !in existingIds) {
                    "Storage provider returned an existing document instead of creating a new file"
                }

                createdFiles.add(target)

                if (target.name != filename) {
                    val actualName = target.name

                    if (target.delete()) {
                        createdFiles.remove(target)
                    } else {
                        error("Cannot remove incorrectly named external audio file: $actualName")
                    }

                    error(
                        "External storage provider changed the audio filename: " +
                            "$filename -> $actualName",
                    )
                }

                try {
                    val output =
                        resolver.openOutputStream(target.uri, "w")
                            ?: error("Cannot write external audio file: $filename")

                    output.use { stream ->
                        source.inputStream().use { input ->
                            input.copyTo(stream)
                        }
                    }
                } catch (e: Exception) {
                    val deleted =
                        try {
                            target.delete()
                        } catch (deleteError: Exception) {
                            e.addSuppressed(deleteError)
                            false
                        }

                    if (deleted) {
                        createdFiles.remove(target)
                    } else {
                        throw IllegalStateException(
                            "Cannot remove incomplete external audio file: $filename",
                            e,
                        )
                    }

                    throw e
                }

                return target.uri
            }

            if (existing.isFile && hasSameContent(source, existing)) {
                return existing.uri
            }
            filename = numberedFilename(originalFilename, suffix++)
        }
    }

    private fun rollbackExternalAudio(
        createdFiles: List<DocumentFile>,
        createdFolders: List<DocumentFile>,
    ) {
        val failed = mutableListOf<String>()

        fun deleteSafDocument(document: DocumentFile) {
            val name = document.name ?: document.uri.toString()

            try {
                if (!document.delete()) {
                    failed += name
                }
            } catch (e: Exception) {
                failed += "$name (${e.message ?: e.javaClass.simpleName})"
            }
        }

        for (file in createdFiles.asReversed()) {
            deleteSafDocument(file)
        }

        for (folder in createdFolders.asReversed()) {
            deleteSafDocument(folder)
        }

        check(failed.isEmpty()) {
            "Cannot remove externally imported files or folders: ${failed.joinToString()}"
        }
    }

    private fun importedAudioFilename(
        zipPath: String,
        manifest: BackupFolderManifest?,
    ): String {
        val originalName =
            manifest
                ?.audioFiles
                ?.firstOrNull { it.path == zipPath }
                ?.name

        return originalName ?: zipPath.removePrefix("audio/")
    }

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
            val audioFiles = mutableListOf<BackupFolderFile>()

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

            fun originalFilename(uri: Uri): String {
                val name =
                    when (uri.scheme) {
                        "file" -> uri.path?.let { File(it).name }

                        "content" ->
                            resolver
                                .query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null,
                                    null,
                                    null,
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        cursor.getString(0)
                                    } else {
                                        null
                                    }
                                }

                        else -> null
                    }

                return name
                    ?.takeIf {
                        it.isNotBlank() &&
                            it != "." &&
                            it != ".." &&
                            '/' !in it &&
                            '\\' !in it &&
                            '\u0000' !in it
                    }
                    ?: "audio.${extensionOf(uri)}"
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

                val sourceUri = sound.uri.toUri()

                when (sound.type) {
                    SoundType.FILE -> {
                        val path =
                            "audio/${audioSources.size.toString().padStart(5, '0')}.${extensionOf(sourceUri)}"

                        addAudio(sourceUri, path)
                        audioFiles += BackupFolderFile(path = path, name = originalFilename(sourceUri))
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
                                name = originalFolderName(sourceUri),
                            )

                        paths[key] = folderPath
                    }
                }
            }

            val manifest =
                BackupFolderManifest(
                    folders = folders,
                    audioFiles = audioFiles,
                )

            BackupFolderValidator.validate(
                manifest = manifest,
                zipEntries = audioSources.map { it.path }.toSet(),
                version = VERSION,
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
                            version = VERSION,
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

                    for ((source, path) in audioSources) {
                        zip.putNextEntry(ZipEntry(path))

                        val input =
                            resolver.openInputStream(source)
                                ?: error("Cannot read audio: $source")

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
    suspend fun restore(
        preview: FullBackupPreview,
        externalFolderUri: Uri? = null,
    ) = withContext(Dispatchers.IO) {
        val externalFolder = externalFolderUri?.let(::requireExternalFolder)
        val createdExternalFiles = mutableListOf<DocumentFile>()
        val createdExternalFolders = mutableListOf<DocumentFile>()
        val externalAudioUris = mutableMapOf<String, Uri>()
        val externalFolderUris = mutableMapOf<String, Uri>()
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
            if (externalFolder != null) {
                val audioPaths =
                    scan.library.boards
                        .flatMap { it.sounds }
                        .filter { it.type == SoundType.FILE }
                        .map { it.uri }
                        .distinct()

                for (zipPath in audioPaths) {
                    val source = File(stage, zipPath.removePrefix("audio/"))
                    val filename = importedAudioFilename(zipPath, scan.folderManifest)

                    externalAudioUris[zipPath] =
                        storeExternalAudio(
                            source = source,
                            directory = externalFolder,
                            originalFilename = filename,
                            createdFiles = createdExternalFiles,
                        )
                }
                val folderPaths =
                    scan.library.boards
                        .flatMap { it.sounds }
                        .filter { it.type == SoundType.FOLDER }
                        .map { it.uri }
                        .distinct()

                for (folderPath in folderPaths) {
                    val preferredName =
                        scan.folderManifest
                            ?.folders
                            ?.firstOrNull { it.folderPath == folderPath }
                            ?.name
                            ?: folderPath.substringAfterLast('/')

                    val targetFolder =
                        createExternalFolder(
                            parent = externalFolder,
                            preferredName = preferredName,
                            createdFolders = createdExternalFolders,
                        )

                    externalFolderUris[folderPath] =
                        folderUriWithinTree(
                            selectedTreeUri = externalFolderUri,
                            folder = targetFolder,
                        )
                    val sourceFolder = File(stage, folderPath)
                    require(sourceFolder.isDirectory) {
                        "Missing staged audio folder: $folderPath"
                    }

                    val sourceFiles =
                        sourceFolder.listFiles()
                            ?: error("Cannot list staged audio folder: $folderPath")

                    for (sourceFile in sourceFiles) {
                        require(sourceFile.isFile) {
                            "Unexpected entry in staged audio folder: ${sourceFile.name}"
                        }

                        storeExternalAudio(
                            source = sourceFile,
                            directory = targetFolder,
                            originalFilename = sourceFile.name,
                            createdFiles = createdExternalFiles,
                        )
                    }
                }
            }

            if (externalFolder == null) {
                require(stage.renameTo(destination)) { "Cannot finalize imported audio" }
            }
            val restored =
                scan.library.copy(
                    boards =
                        scan.library.boards.map { board ->
                            board.copy(
                                sounds =
                                    board.sounds.map { sound ->
                                        val restoredUri =
                                            if (externalFolder != null) {
                                                when (sound.type) {
                                                    SoundType.FILE ->
                                                        requireNotNull(externalAudioUris[sound.uri]) {
                                                            "Missing external audio URI: ${sound.uri}"
                                                        }

                                                    SoundType.FOLDER ->
                                                        requireNotNull(externalFolderUris[sound.uri]) {
                                                            "Missing external folder URI: ${sound.uri}"
                                                        }
                                                }
                                            } else {
                                                val target =
                                                    when (sound.type) {
                                                        SoundType.FILE ->
                                                            File(destination, sound.uri.removePrefix("audio/"))

                                                        SoundType.FOLDER ->
                                                            File(destination, sound.uri)
                                                    }

                                                Uri.fromFile(target)
                                            }

                                        sound.copy(uri = restoredUri.toString())
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
        } catch (e: Exception) {
            if (!committed &&
                (createdExternalFiles.isNotEmpty() || createdExternalFolders.isNotEmpty())
            ) {
                try {
                    rollbackExternalAudio(
                        createdFiles = createdExternalFiles,
                        createdFolders = createdExternalFolders,
                    )
                } catch (rollbackError: Exception) {
                    e.addSuppressed(rollbackError)
                }
            }
            throw e
        } finally {
            stage.deleteRecursively()
            if (!committed) destination.deleteRecursively()
        }
    }

    private fun restoreFolderNames(
        stage: File,
        manifest: BackupFolderManifest,
    ) {
        for ((folderPath, files) in manifest.folders) {
            val directory = File(stage, folderPath)
            require(directory.isDirectory || directory.mkdirs()) {
                "Cannot create restored folder"
            }

            val usedNames = mutableSetOf<String>()
            val renames = mutableListOf<Pair<File, File>>()

            for ((path, original) in files) {
                val source = File(stage, path)
                require(source.isFile) {
                    "Missing extracted folder audio: $path"
                }

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
                    output.use { stream ->
                        val buffer = ByteArray(64 * 1024)

                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break

                            if (isConfig) {
                                require(configBuffer!!.size() + n <= MAX_CONFIG_BYTES) {
                                    "Configuration too large"
                                }
                                configBuffer.write(buffer, 0, n)
                            } else {
                                bytes += n
                                require(bytes <= MAX_AUDIO_BYTES) {
                                    "Audio data too large"
                                }
                                stream?.write(buffer, 0, n)
                            }
                        }
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

        if (envelope.version >= 2) {
            require(envelope.folderManifest != null) {
                "Missing folder manifest in ZIP backup"
            }
        }

        if (envelope.version >= 2) {
            BackupFolderValidator.validate(
                manifest = requireNotNull(envelope.folderManifest),
                zipEntries = names,
                version = envelope.version,
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
                            envelope.version >= 2 && it.uri in folderPaths
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
        private const val VERSION = 3
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
