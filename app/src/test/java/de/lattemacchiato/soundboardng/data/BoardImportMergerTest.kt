package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardImportMergerTest {
    @Test
    fun addPreservesExistingConfigurationAndCreatesNewBoardId() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "Existing")),
                activeBoardId = "existing",
                defaultFadeInMs = 1200,
            )
        val imported =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "imported", name = "Music")),
                activeBoardId = "imported",
                defaultFadeInMs = 300,
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("imported"),
                BoardImportMerger.Mode.ADD,
            )

        assertEquals(2, result.boards.size)
        assertEquals(existing.boards.first(), result.boards.first())
        assertEquals("existing", result.activeBoardId)
        assertEquals(1200, result.defaultFadeInMs)
        assertEquals("Music", result.boards.last().name)
        assertNotEquals("imported", result.boards.last().id)
        assertNotEquals("existing", result.boards.last().id)
    }

    @Test
    fun addResolvesBoardNameConflict() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "Music")),
                activeBoardId = "existing",
            )
        val imported =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "imported", name = "Music")),
                activeBoardId = "imported",
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("imported"),
                BoardImportMerger.Mode.ADD,
            )

        assertEquals("Music (2)", result.boards.last().name)
    }

    @Test
    fun replaceUsesOnlySelectedBoardsAndValidActiveBoard() {
        val existing = SoundLibrary()
        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(id = "first", name = "First"),
                        SoundBoard(id = "second", name = "Second"),
                    ),
                activeBoardId = "first",
                defaultFadeOutMs = 900,
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("second"),
                BoardImportMerger.Mode.REPLACE,
            )

        assertEquals(listOf("second"), result.boards.map { it.id })
        assertEquals("second", result.activeBoardId)
        assertEquals(900, result.defaultFadeOutMs)
        assertTrue(result.boards.none { it.id == "first" })
    }

    @Test
    fun repeatedAddCreatesUniqueBoardsAndNames() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "Musik")),
                activeBoardId = "existing",
            )
        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "imported",
                            name = "Musik",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "sound-1",
                                        uri = "content://audio",
                                        name = "Intro",
                                        fadeInMs = 1200,
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "imported",
            )

        val first =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("imported"),
                BoardImportMerger.Mode.ADD,
            )
        val second =
            BoardImportMerger.merge(
                first,
                imported,
                setOf("imported"),
                BoardImportMerger.Mode.ADD,
            )

        assertEquals(listOf("Musik", "Musik (2)", "Musik (3)"), second.boards.map { it.name })
        assertEquals(
            3,
            second.boards
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals("existing", second.activeBoardId)
        assertEquals(
            "sound-1",
            second.boards[1]
                .sounds
                .single()
                .id,
        )
        assertNotEquals(
            second.boards[1]
                .sounds
                .single()
                .id,
            second.boards[2]
                .sounds
                .single()
                .id,
        )
        assertEquals(
            2,
            second.boards
                .flatMap { it.sounds }
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(
            1200,
            second.boards[2]
                .sounds
                .single()
                .fadeInMs,
        )
        assertEquals(setOf("content://audio"), second.allSoundUris())
    }

    @Test
    fun addMultipleSelectedBoardsPreservesOrder() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "Existing")),
                activeBoardId = "existing",
            )
        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(id = "a", name = "Music"),
                        SoundBoard(id = "b", name = "Effects"),
                        SoundBoard(id = "c", name = "Dialog"),
                    ),
                activeBoardId = "a",
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("c", "a"),
                BoardImportMerger.Mode.ADD,
            )

        assertEquals(
            listOf("Existing", "Music", "Dialog"),
            result.boards.map { it.name },
        )
        assertEquals("existing", result.activeBoardId)
        assertEquals(
            3,
            result.boards
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun unknownSelectedBoardIsRejected() {
        val existing = SoundLibrary()
        val imported =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "a", name = "Music")),
                activeBoardId = "a",
            )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("a", "missing"),
                BoardImportMerger.Mode.ADD,
            )
        }
    }

    @Test
    fun emptySelectionIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BoardImportMerger.merge(
                existing = SoundLibrary(),
                imported = SoundLibrary(),
                selectedBoardIds = emptySet(),
                mode = BoardImportMerger.Mode.ADD,
            )
        }
    }

    @Test
    fun addRemapsSoundIdConflictingWithExistingBoard() {
        val existing =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "existing",
                            name = "Existing",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "shared-id",
                                        uri = "content://existing",
                                        name = "Existing sound",
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "existing",
            )

        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "imported",
                            name = "Imported",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "shared-id",
                                        uri = "content://imported",
                                        name = "Imported sound",
                                        fadeInMs = 1500,
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "imported",
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("imported"),
                BoardImportMerger.Mode.ADD,
            )

        val originalSound =
            result.boards
                .first()
                .sounds
                .single()
        val importedSound =
            result.boards
                .last()
                .sounds
                .single()

        assertEquals("shared-id", originalSound.id)
        assertNotEquals("shared-id", importedSound.id)
        assertEquals("content://imported", importedSound.uri)
        assertEquals(1500, importedSound.fadeInMs)
        assertEquals("existing", result.activeBoardId)
    }

    @Test
    fun addRemapsDuplicateSoundIdsAcrossImportedBoards() {
        val existing = SoundLibrary()

        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "board-a",
                            name = "Music",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "duplicate-id",
                                        uri = "content://music",
                                        name = "Music",
                                    ),
                                ),
                        ),
                        SoundBoard(
                            id = "board-b",
                            name = "Effects",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "duplicate-id",
                                        uri = "content://effects",
                                        name = "Effects",
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "board-a",
            )

        val result =
            BoardImportMerger.merge(
                existing,
                imported,
                setOf("board-a", "board-b"),
                BoardImportMerger.Mode.ADD,
            )

        val importedSounds =
            result.boards.drop(1).flatMap { it.sounds }

        assertEquals(2, importedSounds.size)
        assertEquals(2, importedSounds.map { it.id }.toSet().size)
        assertEquals("duplicate-id", importedSounds.first().id)
        assertEquals(
            setOf("content://music", "content://effects"),
            importedSounds.map { it.uri }.toSet(),
        )
    }

    @Test
    fun addAllowsBoardNamesDifferingOnlyInCase() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "music")),
                activeBoardId = "existing",
            )
        val imported =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "imported", name = "Music")),
                activeBoardId = "imported",
            )

        val result =
            BoardImportMerger.merge(
                existing = existing,
                imported = imported,
                selectedBoardIds = setOf("imported"),
                mode = BoardImportMerger.Mode.ADD,
            )

        assertEquals(
            listOf("music", "Music"),
            result.boards.map { it.name },
        )
    }

    @Test
    fun replaceMultipleSelectedBoardsPreservesOrderAndImportedSettings() {
        val existing =
            SoundLibrary(
                boards = listOf(SoundBoard(id = "existing", name = "Existing")),
                activeBoardId = "existing",
                defaultFadeInMs = 100,
            )
        val imported =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(id = "a", name = "Music"),
                        SoundBoard(id = "b", name = "Effects"),
                        SoundBoard(id = "c", name = "Dialog"),
                    ),
                activeBoardId = "b",
                defaultFadeInMs = 1800,
            )

        val result =
            BoardImportMerger.merge(
                existing = existing,
                imported = imported,
                selectedBoardIds = setOf("c", "a"),
                mode = BoardImportMerger.Mode.REPLACE,
            )

        assertEquals(listOf("a", "c"), result.boards.map { it.id })
        assertEquals(listOf("Music", "Dialog"), result.boards.map { it.name })
        assertEquals("a", result.activeBoardId)
        assertEquals(1800, result.defaultFadeInMs)
        assertTrue(result.boards.none { it.id == "existing" })
    }
}
