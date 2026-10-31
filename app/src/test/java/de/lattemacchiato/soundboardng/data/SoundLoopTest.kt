package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLoopTest {
    @Test fun existingSoundDefaultsToLoop() {
        val sound = Json.decodeFromString<Sound>("""{"id":"x","uri":"content://a","name":"A"}""")
        assertTrue(sound.loop)
    }

    @Test fun loopSettingIsPerBoardAndPerSound() {
        val first =
            SoundLibrary()
                .withSound("a", "content://a", "A")
                .withBoard("second", "Second")
                .selectedBoard("second")
                .withSound("b", "content://a", "A")
                .withLoop("b", false)
        assertFalse(first.sounds.single().loop)
        assertTrue(
            first
                .selectedBoard("default")
                .sounds
                .single()
                .loop,
        )
        val restored = Json.decodeFromString<SoundLibrary>(Json.encodeToString(SoundLibrary.serializer(), first))
        assertEquals(first, restored)
    }
}
