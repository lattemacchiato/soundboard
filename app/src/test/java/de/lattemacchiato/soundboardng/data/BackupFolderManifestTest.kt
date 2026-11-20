package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.json.Json
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
    fun originalFolderNameIsPreserved() {
        val folder =
            BackupFolder(
                folderPath = folderPath,
                files = listOf(BackupFolderFile(audioPath, "Original.mp3")),
                name = "0002",
            )

        BackupFolderValidator.validate(
            manifest = BackupFolderManifest(folders = listOf(folder)),
            zipEntries = setOf(audioPath),
            version = 3,
        )

        assertEquals("0002", folder.name)
    }

    @Test
    fun legacyManifestWithoutFolderNameIsAccepted() {
        val legacyJson =
            """
            {
                "folders": [
                    {
                        "folderPath": "folders/00000",
                        "files": [
                            {
                                "path": "folders/00000/00000.mp3",
                                "name": "Original.mp3"
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()

        val decoded =
            Json.decodeFromString<BackupFolderManifest>(legacyJson)

        assertEquals(null, decoded.folders.single().name)

        BackupFolderValidator.validate(
            manifest = decoded,
            zipEntries = setOf(audioPath),
            version = 2,
        )
    }

    @Test
    fun invalidOriginalFolderNamesAreRejected() {
        val invalidNames =
            listOf(
                "",
                " ",
                ".",
                "..",
                "folder/audio",
                "folder\\audio",
                "audio\u0000folder",
                "audio\tfolder",
                "audio\nfolder",
            )

        invalidNames.forEach { name ->
            val folder =
                BackupFolder(
                    folderPath = folderPath,
                    files = emptyList(),
                    name = name,
                )

            assertThrows("Folder name: $name", IllegalArgumentException::class.java) {
                BackupFolderValidator.validate(
                    manifest = BackupFolderManifest(folders = listOf(folder)),
                    zipEntries = emptySet(),
                    version = 3,
                )
            }
        }
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
                "audio\tfile.mp3",
                "audio\nfile.mp3",
                "audio\u001F.mp3",
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

    @Test
    fun validVersion3AudioManifest() {
        val path = "audio/00000.mp3"
        val manifest =
            BackupFolderManifest(
                audioFiles = listOf(BackupFolderFile(path, "Originalaufnahme.mp3")),
            )

        BackupFolderValidator.validate(
            manifest = manifest,
            zipEntries = setOf(path),
            version = 3,
        )

        assertEquals("Originalaufnahme.mp3", manifest.audioFiles.single().name)
    }

    @Test
    fun version3RejectsMissingAudioManifestEntry() {
        val path = "audio/00000.mp3"

        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest = BackupFolderManifest(),
                zipEntries = setOf(path),
                version = 3,
            )
        }
    }

    @Test
    fun version3RejectsDuplicateAudioPaths() {
        val path = "audio/00000.mp3"
        val file = BackupFolderFile(path, "Original.mp3")

        assertThrows(IllegalArgumentException::class.java) {
            BackupFolderValidator.validate(
                manifest =
                    BackupFolderManifest(
                        audioFiles = listOf(file, file),
                    ),
                zipEntries = setOf(path),
                version = 3,
            )
        }
    }

    @Test
    fun version3RejectsInvalidOriginalFilenames() {
        val path = "audio/00000.mp3"
        val invalidNames =
            listOf(
                "",
                " ",
                ".",
                "..",
                "../outside.mp3",
                "folder/audio.mp3",
                "folder\\audio.mp3",
                "audio\u0000.mp3",
                "audio\tfile.mp3",
                "audio\nfile.mp3",
                "audio\u001F.mp3",
            )

        invalidNames.forEach { name ->
            assertThrows("Filename: $name", IllegalArgumentException::class.java) {
                BackupFolderValidator.validate(
                    manifest =
                        BackupFolderManifest(
                            audioFiles =
                                listOf(
                                    BackupFolderFile(path, name),
                                ),
                        ),
                    zipEntries = setOf(path),
                    version = 3,
                )
            }
        }
    }

    @Test
    fun version2AllowsManifestWithoutAudioFilenames() {
        val path = "audio/00000.mp3"

        BackupFolderValidator.validate(
            manifest = BackupFolderManifest(),
            zipEntries = setOf(path),
            version = 2,
        )
    }
}
