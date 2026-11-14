package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.Serializable

@Serializable
internal data class BackupFolderManifest(
    val folders: List<BackupFolder> = emptyList(),
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

    fun validate(
        manifest: BackupFolderManifest,
        zipEntries: Set<String>,
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
                require(
                    name.isNotBlank() &&
                        name != "." &&
                        name != ".." &&
                        '/' !in name &&
                        '\\' !in name &&
                        '\u0000' !in name,
                ) {
                    "Invalid original audio filename"
                }
            }
        }

        require(zipEntries.filter { it.startsWith("folders/") }.toSet() == filePaths) {
            "Unexpected backup folder entries"
        }
    }
}
