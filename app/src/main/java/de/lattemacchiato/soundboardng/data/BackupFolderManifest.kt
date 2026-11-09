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

        for (folder in manifest.folders) {
            require(folderPattern.matches(folder.folderPath)) {
                "Invalid backup folder path"
            }

            for (file in folder.files) {
                require(filePattern.matches(file.path)) {
                    "Invalid backup audio path"
                }
                require(file.path.startsWith("${folder.folderPath}/")) {
                    "Audio file outside backup folder"
                }
                require(filePaths.add(file.path)) {
                    "Duplicate backup audio file"
                }
                require(file.path in zipEntries) {
                    "Missing backup audio file"
                }
                require(
                    file.name.isNotBlank() &&
                        file.name != "." &&
                        file.name != ".." &&
                        '/' !in file.name &&
                        '\\' !in file.name &&
                        '\u0000' !in file.name,
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
