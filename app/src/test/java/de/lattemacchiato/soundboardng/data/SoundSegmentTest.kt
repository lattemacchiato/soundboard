package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSegmentTest {
    private fun library() =
        SoundLibrary()
            .withSound("a", "content://rain", "Rain")
            .withSound("b", "content://fan", "Fan")

    @Test
    fun `new sounds play the complete file by default`() {
        val sound = library().find("a")!!

        assertEquals(0L, sound.startPositionMs)
        assertEquals(null, sound.endPositionMs)
    }

    @Test
    fun `withSegment updates only the selected sound`() {
        val updated = library().withSegment("a", 10_000L, 25_000L)

        assertEquals(10_000L, updated.find("a")?.startPositionMs)
        assertEquals(25_000L, updated.find("a")?.endPositionMs)
        assertEquals(0L, updated.find("b")?.startPositionMs)
        assertEquals(null, updated.find("b")?.endPositionMs)
    }

    @Test
    fun `withSegment accepts an open end`() {
        val updated = library().withSegment("a", 10_000L, null)

        assertEquals(10_000L, updated.find("a")?.startPositionMs)
        assertEquals(null, updated.find("a")?.endPositionMs)
    }

    @Test
    fun `withSegment rejects a negative start`() {
        val original = library()

        assertEquals(original, original.withSegment("a", -1L, 10_000L))
    }

    @Test
    fun `withSegment rejects an end at or before the start`() {
        val original = library()

        assertEquals(original, original.withSegment("a", 10_000L, 10_000L))
        assertEquals(original, original.withSegment("a", 10_000L, 5_000L))
    }

    @Test
    fun `withSegment resets to the complete file`() {
        val updated =
            library()
                .withSegment("a", 10_000L, 25_000L)
                .withSegment("a", 0L, null)

        assertEquals(0L, updated.find("a")?.startPositionMs)
        assertEquals(null, updated.find("a")?.endPositionMs)
    }

    @Test
    fun `withSegment ignores unknown ids`() {
        val original = library()

        assertEquals(original, original.withSegment("missing", 1_000L, 2_000L))
    }

    @Test
    fun `withSegment ignores folders`() {
        val folder =
            Sound(
                id = "folder",
                uri = "content://folder",
                name = "Ambience",
                type = SoundType.FOLDER,
            )
        val original = SoundLibrary().withSound("a", "content://rain", "Rain")
        val withFolder =
            original.copy(
                boards =
                    original.boards.map { board ->
                        if (board.id == original.activeBoardId) {
                            board.copy(sounds = board.sounds + folder)
                        } else {
                            board
                        }
                    },
            )

        assertEquals(withFolder, withFolder.withSegment("folder", 1_000L, 2_000L))
    }
}
