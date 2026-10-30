package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLoopSettingsTest {

    @Test fun defaultPreservesExistingBehavior() {
        assertTrue(SoundLibrary().defaultLoop)
    }

    @Test fun newSoundsInheritGlobalDefault() {
        val library = SoundLibrary()
            .withDefaultLoop(false)
            .withSound("a", "content://a", "Sound A")

        assertFalse(library.sounds.first().loop)
    }

    @Test fun changingDefaultDoesNotModifyExistingSounds() {
        val library = SoundLibrary()
            .withSound("a", "content://a", "Sound A")
            .withDefaultLoop(false)
            .withSound("b", "content://b", "Sound B")

        assertTrue(library.sounds.first { it.id == "a" }.loop)
        assertFalse(library.sounds.first { it.id == "b" }.loop)
    }

    @Test fun individualLoopSettingRemainsIndependent() {
        val library = SoundLibrary()
            .withDefaultLoop(false)
            .withSound("a", "content://a", "Sound A")
            .withLoop("a", true)

        assertTrue(library.sounds.first().loop)
        assertFalse(library.defaultLoop)
    }

    @Test fun globalDefaultCanBeChangedRepeatedly() {
        val library = SoundLibrary()
            .withDefaultLoop(false)
            .withDefaultLoop(true)

        assertTrue(library.defaultLoop)
    }
}
