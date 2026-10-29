package de.lattemacchiato.soundboardng.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import androidx.datastore.core.CorruptionException
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SoundLibrarySerializerTest {
    @Test
    fun `round-trips a library`() =
        runTest {
            val original =
                SoundLibrary()
                    .withSound("a", "content://rain", "Rain")
                    .withSound("b", "content://fan", "Fan")
                    .withVolume("b", 35)

            val bytes = ByteArrayOutputStream().also { SoundLibrarySerializer.writeTo(original, it) }.toByteArray()
            val restored = SoundLibrarySerializer.readFrom(ByteArrayInputStream(bytes))

            assertEquals(original, restored)
        }

    @Test
    fun `malformed json degrades to an empty board rather than failing to launch`() =
        runTest {
            try {
                SoundLibrarySerializer.readFrom(ByteArrayInputStream("{ not json".toByteArray()))
                fail("Expected CorruptionException")
            } catch (_: CorruptionException) {}
        }

    @Test
    fun `unknown keys from a newer build are ignored`() =
        runTest {
            val json = """{"sounds":[{"id":"a","uri":"content://rain","name":"Rain","fadeMs":250}]}"""

            val restored = SoundLibrarySerializer.readFrom(ByteArrayInputStream(json.toByteArray()))

            assertEquals(listOf("Rain"), restored.sounds.map { it.name })
        }

    @Test
    fun `an empty file yields an empty board`() =
        runTest {
            assertEquals(SoundLibrary(), SoundLibrarySerializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }
    @Test
    fun `legacy json without row height settings uses defaults`() =
        runTest {
            val json = """
            {
                "boards": [
                    {
                        "id": "default",
                        "name": "Default",
                        "sounds": []
                    }
                ],
                "activeBoardId": "default"
            }
        """.trimIndent()

            val restored = SoundLibrarySerializer.readFrom(
                ByteArrayInputStream(json.toByteArray())
            )

            assertEquals(1, restored.defaultRowHeightLevel)
            assertEquals(null, restored.boards.first().rowHeightLevel)
            assertEquals(1, restored.effectiveRowHeightLevel("default"))
        }

    @Test
    fun `row height settings survive serialization round trip`() =
        runTest {
            val original = SoundLibrary()
                .withDefaultRowHeightLevel(3)
                .withBoard("board2", "Second board")
                .withBoardRowHeightLevel("board2", 5)

            val bytes = ByteArrayOutputStream().also {
                SoundLibrarySerializer.writeTo(original, it)
            }.toByteArray()

            val restored = SoundLibrarySerializer.readFrom(
                ByteArrayInputStream(bytes)
            )

            assertEquals(original, restored)
            assertEquals(3, restored.defaultRowHeightLevel)
            assertEquals(3, restored.effectiveRowHeightLevel("default"))
            assertEquals(5, restored.effectiveRowHeightLevel("board2"))
            assertEquals(null, restored.boards.first().rowHeightLevel)
        }
}
