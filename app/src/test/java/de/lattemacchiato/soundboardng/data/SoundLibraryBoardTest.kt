package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundLibraryBoardTest {
    @Test
    fun sameUriAllowedWithinAndAcrossBoards() {
        val library =
            SoundLibrary()
                .withSound("a", "content://rain", "Rain")
                .withBoard("second", "Scene 2")
                .selectedBoard("second")
                .withSound("b", "content://rain", "Rain")
                .withSound("c", "content://rain", "Duplicate")

        assertEquals(2, library.sounds.size)
        assertEquals(3, library.boards.sumOf { it.sounds.size })
        assertEquals(listOf("b", "c"), library.sounds.map { it.id })
        assertEquals(setOf("content://rain"), library.allSoundUris())
    }

    @Test fun removingOneReferenceRetainsUri() {
        val library =
            SoundLibrary()
                .withSound("a", "content://rain", "Rain")
                .withBoard("second", "Scene 2")
                .selectedBoard("second")
                .withSound("b", "content://rain", "Rain")
        assertEquals(setOf("content://rain"), library.withoutSound("b").allSoundUris())
        assertEquals(
            emptySet<String>(),
            library
                .withoutSound("b")
                .selectedBoard("default")
                .withoutSound("a")
                .allSoundUris(),
        )
    }

    @Test fun deletingBoardKeepsSharedUri() {
        val library =
            SoundLibrary()
                .withSound("a", "content://rain", "Rain")
                .withBoard("second", "Scene 2")
                .selectedBoard("second")
                .withSound("b", "content://rain", "Rain")
        assertEquals(setOf("content://rain"), library.withoutBoard("second").allSoundUris())
    }
}
