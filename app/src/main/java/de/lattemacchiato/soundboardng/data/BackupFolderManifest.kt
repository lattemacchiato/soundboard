package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.Serializable

@Serializable
internal data class BackupFolderManifest(
    val folders: List<BackupFolder> = emptyList(),
    val audioFiles: List<BackupFolderFile> = emptyList(),
)

@Serializable
internal data class BackupFolder(
    val folderPath: String,
    val files: List<BackupFolderFile>,
)

@Serializable
internal data class BackupFolderFile(
    val path: String,
    val name: String,
)

internal object BackupFolderValidator {
    private val folderPattern = Regex("folders/[0-9]{5}")
    private val filePattern = Regex("folders/[0-9]{5}/[0-9]{5}\\.[a-zA-Z0-9]{1,8}")
    private val audioFilePattern = Regex("audio/[0-9]{5}\\.[a-zA-Z0-9]{1,8}")

    private fun requireValidFilename(name: String) {
        require(
            name.isNotBlank() &&
                name != "." &&
                name != ".." &&
                '/' !in name &&
                '\\' !in name &&
                name.none { it.isISOControl() },
        ) {
            "Invalid original audio filename"
        }
    }

    fun validate(
        manifest: BackupFolderManifest,
        zipEntries: Set<String>,
        version: Int = 2,
    ) {
        val folderPaths = manifest.folders.map { it.folderPath }
        require(folderPaths.distinct().size == folderPaths.size) {
            "Duplicate backup folder"
        }

        val filePaths = mutableSetOf<String>()

        for ((folderPath, files) in manifest.folders) {
            require(folderPattern.matches(folderPath)) {
                "Invalid backup folder path"
            }

            for ((path, name) in files) {
                require(filePattern.matches(path)) {
                    "Invalid backup audio path"
                }
                require(path.startsWith("$folderPath/")) {
                    "Audio file outside backup folder"
                }
                require(filePaths.add(path)) {
                    "Duplicate backup audio file"
                }
                require(path in zipEntries) {
                    "Missing backup audio file"
                }
                requireValidFilename(name)
            }
        }

        require(zipEntries.filter { it.startsWith("folders/") }.toSet() == filePaths) {
            "Unexpected backup folder entries"
        }
        if (version >= 3) {
            val audioPaths = mutableSetOf<String>()

            for ((path, name) in manifest.audioFiles) {
                require(audioFilePattern.matches(path)) {
                    "Invalid backup audio path"
                }
                require(audioPaths.add(path)) {
                    "Duplicate backup audio file"
                }
                require(path in zipEntries) {
                    "Missing backup audio file"
                }
                requireValidFilename(name)
            }

            require(
                zipEntries.filter { it.startsWith("audio/") }.toSet() == audioPaths,
            ) {
                "Unexpected or missing backup audio manifest entries"
            }
        } else {
            require(manifest.audioFiles.isEmpty()) {
                "Audio filename manifest is not supported in ZIP backup version $version"
            }
        }
    }
}
