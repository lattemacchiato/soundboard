package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InternalAudioCleanerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createAudio(relativePath: String): File {
        val file = File(temporaryFolder.root, "backup-audio/$relativePath")
        requireNotNull(file.parentFile).mkdirs()
        file.writeText("test audio")
        return file
    }

    private fun libraryOf(vararg sounds: Sound): SoundLibrary =
        SoundLibrary(
            boards =
                listOf(
                    SoundBoard(
                        id = "board",
                        name = "Test",
                        sounds = sounds.toList(),
                    ),
                ),
            activeBoardId = "board",
        )

    private fun sound(
        id: String,
        file: File,
        type: SoundType = SoundType.FILE,
    ): Sound =
        Sound(
            id = id,
            uri = file.toURI().toString(),
            name = id,
            type = type,
        )

    private fun cleaner(): InternalAudioCleaner =
        InternalAudioCleaner(
            File(temporaryFolder.root, "backup-audio"),
        )

    @Test
    fun referencedFileIsNotOrphaned() {
        val file = createAudio("import-1/music.mp3")

        val candidates = cleaner().findOrphans(libraryOf(sound("one", file)))

        assertTrue(candidates.files.isEmpty())
        assertTrue(candidates.emptyDirectories.isEmpty())
    }

    @Test
    fun unreferencedFileIsOrphaned() {
        val file = createAudio("import-1/music.mp3")

        val candidates = cleaner().findOrphans(libraryOf())

        assertEquals(listOf(file), candidates.files)
        assertTrue(candidates.emptyDirectories.contains(requireNotNull(file.parentFile)))
    }

    @Test
    fun duplicateReferencesProtectSameFile() {
        val file = createAudio("import-1/music.mp3")

        val candidates =
            cleaner().findOrphans(
                libraryOf(
                    sound("one", file),
                    sound("two", file),
                ),
            )

        assertTrue(candidates.files.isEmpty())
    }

    @Test
    fun folderReferenceProtectsAllChildren() {
        val first = createAudio("import-1/music/one.mp3")
        val second = createAudio("import-1/music/two.mp3")
        val folder = requireNotNull(first.parentFile)

        val candidates =
            cleaner().findOrphans(
                libraryOf(sound("folder", folder, SoundType.FOLDER)),
            )

        assertFalse(candidates.files.contains(first))
        assertFalse(candidates.files.contains(second))
        assertFalse(candidates.emptyDirectories.contains(folder))
    }

    @Test
    fun referencedFileDoesNotProtectUnrelatedFile() {
        val retained = createAudio("import-1/retained.mp3")
        val orphan = createAudio("import-1/orphan.mp3")

        val candidates =
            cleaner().findOrphans(
                libraryOf(sound("retained", retained)),
            )

        assertFalse(candidates.files.contains(retained))
        assertTrue(candidates.files.contains(orphan))
        assertFalse(candidates.emptyDirectories.contains(requireNotNull(retained.parentFile)))
    }

    @Test
    fun stageDirectoryIsNeverConsidered() {
        createAudio("stage-1/temporary.mp3")

        val candidates = cleaner().findOrphans(libraryOf())

        assertTrue(candidates.files.isEmpty())
        assertTrue(candidates.emptyDirectories.isEmpty())
    }

    @Test
    fun externalSafReferenceDoesNotProtectInternalOrphan() {
        val orphan = createAudio("import-1/orphan.mp3")
        val external =
            Sound(
                id = "external",
                uri = "content://com.android.externalstorage.documents/document/test",
                name = "External",
            )

        val candidates = cleaner().findOrphans(libraryOf(external))

        assertTrue(candidates.files.contains(orphan))
    }

    @Test
    fun missingBackupDirectoryProducesNoCandidates() {
        val candidates = cleaner().findOrphans(libraryOf())

        assertTrue(candidates.files.isEmpty())
        assertTrue(candidates.emptyDirectories.isEmpty())
    }

    @Test
    fun fileOutsideManagedImportDirectoryIsNeverConsidered() {
        val externalFile =
            File(temporaryFolder.root, "other-private-data/important.txt")

        externalFile.parentFile?.mkdirs()
        externalFile.writeText("must remain untouched")

        val candidates = cleaner().findOrphans(libraryOf())

        assertFalse(candidates.files.contains(externalFile))
        assertTrue(externalFile.exists())
    }

    @Test
    fun referenceOnAnotherBoardProtectsAudioAfterBoardDeletion() {
        val sharedFile = createAudio("import-1/shared.mp3")

        val library =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "first",
                            name = "First",
                            sounds = listOf(sound("sound-1", sharedFile)),
                        ),
                        SoundBoard(
                            id = "second",
                            name = "Second",
                            sounds = listOf(sound("sound-2", sharedFile)),
                        ),
                    ),
                activeBoardId = "first",
            )

        val afterDeletion = library.withoutBoard("first")
        val candidates = cleaner().findOrphans(afterDeletion)

        assertTrue(candidates.files.isEmpty())
        assertTrue(candidates.emptyDirectories.isEmpty())
        assertTrue(sharedFile.exists())
    }

    @Test
    fun nestedFolderReferenceProtectsOnlyItsOwnSubtree() {
        val protectedFile =
            createAudio("import-1/music/album/live/song.mp3")
        val orphanFile =
            createAudio("import-1/effects/thunder.mp3")

        val referencedFolder =
            File(temporaryFolder.root, "backup-audio/import-1/music")

        val candidates =
            cleaner().findOrphans(
                libraryOf(
                    sound("music-folder", referencedFolder, SoundType.FOLDER),
                ),
            )

        assertFalse(candidates.files.contains(protectedFile))
        assertTrue(candidates.files.contains(orphanFile))

        assertFalse(candidates.emptyDirectories.contains(referencedFolder))
        assertFalse(candidates.emptyDirectories.contains(requireNotNull(protectedFile.parentFile)))
        assertTrue(candidates.emptyDirectories.contains(requireNotNull(orphanFile.parentFile)))

        assertTrue(protectedFile.exists())
        assertTrue(orphanFile.exists())
    }

    @Test
    fun cleanDeletesOrphansButPreservesReferencedFiles() {
        val referencedFile = createAudio("import-1/music.mp3")
        val orphanFile = createAudio("import-1/unused.mp3")

        val deleted =
            cleaner().clean(
                libraryOf(
                    sound("music", referencedFile),
                ),
            )

        assertEquals(1, deleted)
        assertTrue(referencedFile.exists())
        assertFalse(orphanFile.exists())
    }

    @Test
    fun cleanPreservesFileUntilLastReferenceIsRemoved() {
        val sharedFile = createAudio("import-1/shared.mp3")

        val firstReference = sound("first", sharedFile)
        val secondReference = sound("second", sharedFile)

        val cleaner = cleaner()

        // Two tiles reference the same file.
        assertEquals(
            0,
            cleaner.clean(libraryOf(firstReference, secondReference)),
        )
        assertTrue(sharedFile.exists())

        // One tile remains: the file must still be protected.
        assertEquals(
            0,
            cleaner.clean(libraryOf(secondReference)),
        )
        assertTrue(sharedFile.exists())

        // Last reference removed: the file can now be deleted.
        assertEquals(
            1,
            cleaner.clean(libraryOf()),
        )
        assertFalse(sharedFile.exists())
    }

    @Test
    fun cleanPreservesReferencedFolderAndDeletesUnrelatedFiles() {
        val firstFile = createAudio("import-1/music/first.mp3")
        val secondFile = createAudio("import-1/music/subfolder/second.mp3")
        val orphanFile = createAudio("import-1/unused.mp3")

        val folder = firstFile.parentFile!!

        val deleted =
            cleaner().clean(
                libraryOf(
                    sound("music-folder", folder, SoundType.FOLDER),
                ),
            )

        assertEquals(1, deleted)
        assertTrue(firstFile.exists())
        assertTrue(secondFile.exists())
        assertTrue(folder.exists())
        assertFalse(orphanFile.exists())
    }

    @Test
    fun cleanRemovesOrphanedFolderTree() {
        val firstFile = createAudio("import-1/music/first.mp3")
        val secondFile = createAudio("import-1/music/subfolder/second.mp3")

        val musicDirectory = firstFile.parentFile!!
        val importDirectory = musicDirectory.parentFile!!

        val deleted = cleaner().clean(libraryOf())

        assertEquals(2, deleted)
        assertFalse(firstFile.exists())
        assertFalse(secondFile.exists())
        assertFalse(musicDirectory.exists())
        assertFalse(importDirectory.exists())
    }
}
