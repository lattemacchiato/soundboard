package de.lattemacchiato.soundboardng.data

import java.io.File
import java.net.URI

/**
 * Identifies unreferenced files in app-private backup imports.
 *
 * This class does not delete anything.
 */
internal class InternalAudioCleaner(
    private val backupAudioRoot: File,
) {
    internal data class Candidates(
        val files: List<File>,
        val emptyDirectories: List<File>,
    )

    /**
     * Deletes unreferenced files in managed import directories.
     *
     * The caller must ensure that no concurrent operation can change
     * audio references or create imported audio while this runs.
     *
     * Returns the number of successfully deleted files.
     */
    fun clean(library: SoundLibrary): Int {
        val candidates = findOrphans(library)
        var deletedFiles = 0

        for (file in candidates.files) {
            if (file.delete()) {
                deletedFiles++
            }
        }

        for (directory in candidates.emptyDirectories) {
            directory.delete()
        }

        return deletedFiles
    }

    fun findOrphans(library: SoundLibrary): Candidates {
        val root = backupAudioRoot.canonicalFile

        if (!root.isDirectory) {
            return Candidates(emptyList(), emptyList())
        }

        val protectedPaths =
            library.boards
                .flatMap { it.sounds }
                .mapNotNull { sound ->
                    val uri =
                        runCatching { URI(sound.uri) }
                            .getOrNull()
                            ?: return@mapNotNull null

                    if (uri.scheme != "file") {
                        return@mapNotNull null
                    }

                    val file =
                        runCatching { File(uri).canonicalFile }
                            .getOrNull()
                            ?: return@mapNotNull null

                    if (!file.toPath().startsWith(root.toPath())) {
                        return@mapNotNull null
                    }

                    file to sound.type
                }

        val orphanFiles = mutableListOf<File>()
        val emptyDirectories = mutableListOf<File>()

        val imports =
            root
                .listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("import-") }
                .orEmpty()

        for (importDirectory in imports) {
            val importPath = importDirectory.canonicalFile.toPath()

            if (importPath.parent != root.toPath()) {
                continue
            }

            importDirectory.walkBottomUp().forEach { entry ->
                val entryPath = entry.canonicalFile.toPath()

                if (!entryPath.startsWith(importPath)) {
                    return@forEach
                }

                val isProtected =
                    protectedPaths.any { (protected, type) ->
                        val protectedPath = protected.toPath()

                        when (type) {
                            SoundType.FILE ->
                                entryPath == protectedPath ||
                                    protectedPath.startsWith(entryPath)

                            SoundType.FOLDER ->
                                entryPath.startsWith(protectedPath) ||
                                    protectedPath.startsWith(entryPath)
                        }
                    }

                if (!isProtected) {
                    if (entry.isFile) {
                        orphanFiles += entry
                    } else if (entry.isDirectory) {
                        emptyDirectories += entry
                    }
                }
            }
        }

        return Candidates(
            files = orphanFiles,
            emptyDirectories = emptyDirectories,
        )
    }
}
