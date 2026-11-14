package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFolderManifestTest {
    private val folderPath = "folders/00000"
    private val audioPath = "$folderPath/00000.mp3"

    private fun manifest(
        files: List<BackupFolderFile> =
            listOf(
                BackupFolderFile(audioPath, "Original.mp3"),
            ),
    ) = BackupFolderManifest(
        folders = listOf(BackupFolder(folderPath, files)),
    )

    @Test
    fun validFolderManifest() {
        BackupFolderValidator.validate(manifest(), setOf(audioPath))
    }

    @Test
    fun emptyFolderIsAllowed() {
        BackupFolderValidator.validate(manifest(emptyList()), emptySet())
    }

    @Test
    fun missingAudioFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(manifest(), emptySet())
        }
    }

    @Test
    fun unexpectedAudioFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest(),
                setOf(audioPath, "$folderPath/00001.mp3"),
            )
        }
    }

    @Test
    fun duplicateFolderIsRejected() {
        val folder = BackupFolder(folderPath, emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                BackupFolderManifest(listOf(folder, folder)),
                emptySet(),
            )
        }
    }

    @Test
    fun duplicateAudioPathIsRejected() {
        val file = BackupFolderFile(audioPath, "Original.mp3")
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest(listOf(file, file)),
                setOf(audioPath),
            )
        }
    }

    @Test
    fun pathTraversalIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest(
                    listOf(
                        BackupFolderFile(audioPath, "../outside.mp3"),
                    ),
                ),
                setOf(audioPath),
            )
        }
    }

    @Test
    fun invalidOriginalFilenamesAreRejected() {
        val invalidNames =
            listOf(
                "",
                " ",
                ".",
                "..",
                "folder/audio.mp3",
                "folder\\audio.mp3",
                "audio\u0000.mp3",
            )

        invalidNames.forEach { name ->
            assertThrows("Filename: $name", IllegalArgumentException::class.java) {
                BackupFolderValidator.validate(
                    manifest(
                        listOf(
                            BackupFolderFile(audioPath, name),
                        ),
                    ),
                    setOf(audioPath),
                )
            }
        }
    }

    @Test
    fun audioFileOutsideFolderIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest(
                    listOf(
                        BackupFolderFile(
                            "folders/00001/00000.mp3",
                            "Original.mp3",
                        ),
                    ),
                ),
                setOf("folders/00001/00000.mp3"),
            )
        }
    }

    @Test
    fun originalFilenameIsPreserved() {
        val file =
            manifest()
                .folders
                .single()
                .files
                .single()
        assertEquals("Original.mp3", file.name)
    }

    @Test
    fun duplicateOriginalFilenameIgnoringCaseIsAllowed() {
        val files =
            listOf(
                BackupFolderFile("$folderPath/00000.mp3", "Ton.mp3"),
                BackupFolderFile("$folderPath/00001.mp3", "ton.mp3"),
            )

        BackupFolderValidator.validate(
            manifest(files),
            files.map { it.path }.toSet(),
        )
    }
}
