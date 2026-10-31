package de.lattemacchiato.soundboardng.data

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SoundLibraryMigrationTest {
    private suspend fun read(text: String) = SoundLibrarySerializer.readFrom(ByteArrayInputStream(text.toByteArray()))

    @Test fun legacySoundsArePreserved() =
        runTest {
            val old = """{"sounds":[{"id":"a","uri":"content://rain","name":"Rain","volumePercent":37}]}"""
            val migrated = read(old)
            assertEquals("default", migrated.activeBoardId)
            assertEquals(1, migrated.boards.size)
            assertEquals("a", migrated.sounds.single().id)
            assertEquals("content://rain", migrated.sounds.single().uri)
            assertEquals(37, migrated.sounds.single().volumePercent)
        }

    @Test fun legacyEmptyBoardIsMigrated() =
        runTest {
            assertEquals(SoundLibrary(), read("""{"sounds":[]}"""))
        }

    @Test fun roundTripKeepsBoardsAndSelection() =
        runTest {
            val library =
                SoundLibrary()
                    .withSound("a", "content://rain", "Rain")
                    .withBoard("b", "Act 2")
                    .selectedBoard("b")
                    .withSound("c", "content://rain", "Rain again")
            val output = ByteArrayOutputStream()
            SoundLibrarySerializer.writeTo(library, output)
            assertEquals(library, SoundLibrarySerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
            assertEquals(
                1,
                library.boards
                    .first()
                    .sounds.size,
            )
            assertEquals(
                1,
                library.boards
                    .last()
                    .sounds.size,
            )
        }

    @Test fun lastBoardCannotBeDeleted() {
        assertEquals(SoundLibrary(), SoundLibrary().withoutBoard("default"))
    }

    @Test fun deletingActiveBoardSelectsAnother() {
        val result = SoundLibrary().withBoard("b", "Second").selectedBoard("b").withoutBoard("b")
        assertEquals("default", result.activeBoardId)
        assertEquals(1, result.boards.size)
    }

    @Test fun corruptJsonDoesNotBecomeEmptyLibrary() =
        runTest {
            try {
                read("{ not json")
                fail("Expected CorruptionException")
            } catch (_: CorruptionException) {
            }
        }
}
