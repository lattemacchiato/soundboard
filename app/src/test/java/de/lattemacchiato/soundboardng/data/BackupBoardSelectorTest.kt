package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupBoardSelectorTest {
    private val library =
        SoundLibrary(
            boards =
                listOf(
                    SoundBoard(id = "a", name = "Act 1"),
                    SoundBoard(id = "b", name = "Act 2"),
                    SoundBoard(id = "c", name = "Act 3"),
                ),
            activeBoardId = "b",
        )

    @Test
    fun nullSelectionReturnsOriginalLibrary() {
        val result = BackupBoardSelector.select(library, null)

        assertSame(library, result)
    }

    @Test
    fun singleBoardSelectionUpdatesActiveBoard() {
        val result = BackupBoardSelector.select(library, setOf("c"))

        assertEquals(listOf("c"), result.boards.map { it.id })
        assertEquals("c", result.activeBoardId)
    }

    @Test
    fun multipleBoardsPreserveOriginalOrderAndActiveBoard() {
        val result = BackupBoardSelector.select(library, setOf("c", "b"))

        assertEquals(listOf("b", "c"), result.boards.map { it.id })
        assertEquals("b", result.activeBoardId)
    }

    @Test
    fun emptySelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupBoardSelector.select(library, emptySet())
        }
    }

    @Test
    fun unknownBoardIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupBoardSelector.select(library, setOf("a", "unknown"))
        }
    }
}
