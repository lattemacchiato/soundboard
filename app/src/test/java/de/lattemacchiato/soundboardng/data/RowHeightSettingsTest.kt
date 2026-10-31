package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowHeightSettingsTest {
    @Test fun defaultPreservesExistingBehavior() {
        val library = SoundLibrary()

        assertEquals(1, library.defaultRowHeightLevel)
        assertNull(library.boards.first().rowHeightLevel)
        assertEquals(1, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
    }

    @Test fun globalLevelIsClampedToValidRange() {
        val library = SoundLibrary()

        assertEquals(1, library.withDefaultRowHeightLevel(0).defaultRowHeightLevel)
        assertEquals(3, library.withDefaultRowHeightLevel(3).defaultRowHeightLevel)
        assertEquals(5, library.withDefaultRowHeightLevel(6).defaultRowHeightLevel)
    }

    @Test fun newBoardsInheritGlobalDefault() {
        val library =
            SoundLibrary()
                .withDefaultRowHeightLevel(4)
                .withBoard("board2", "Second board")

        assertNull(library.boards.first { it.id == "board2" }.rowHeightLevel)
        assertEquals(4, library.effectiveRowHeightLevel("board2"))
    }

    @Test fun boardCanOverrideGlobalDefault() {
        val library =
            SoundLibrary()
                .withDefaultRowHeightLevel(2)
                .withBoardRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID, 5)

        assertEquals(5, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
        assertEquals(2, library.defaultRowHeightLevel)
    }

    @Test fun globalChangeDoesNotOverwriteBoardOverride() {
        val library =
            SoundLibrary()
                .withBoardRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID, 4)
                .withDefaultRowHeightLevel(2)

        assertEquals(4, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
    }

    @Test fun nullRestoresInheritance() {
        val library =
            SoundLibrary()
                .withDefaultRowHeightLevel(3)
                .withBoardRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID, 5)
                .withBoardRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID, null)

        assertNull(library.boards.first().rowHeightLevel)
        assertEquals(3, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
    }

    @Test fun boardLevelIsClampedToValidRange() {
        val library =
            SoundLibrary()
                .withBoardRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID, 99)

        assertEquals(5, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
    }

    @Test fun changingOneBoardDoesNotAffectAnother() {
        val library =
            SoundLibrary()
                .withDefaultRowHeightLevel(2)
                .withBoard("board2", "Second board")
                .withBoardRowHeightLevel("board2", 5)

        assertEquals(2, library.effectiveRowHeightLevel(SoundLibrary.DEFAULT_BOARD_ID))
        assertEquals(5, library.effectiveRowHeightLevel("board2"))
    }
}
