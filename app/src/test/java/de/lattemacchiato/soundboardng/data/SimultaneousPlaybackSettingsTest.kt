package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimultaneousPlaybackSettingsTest {
    @Test fun defaultPreservesExistingBehavior() {
        assertTrue(SoundLibrary().allowSimultaneousPlayback)
    }

    @Test fun settingIsImmutableAndReversible() {
        val original = SoundLibrary()
        val disabled = original.withAllowSimultaneousPlayback(false)
        assertTrue(original.allowSimultaneousPlayback)
        assertFalse(disabled.allowSimultaneousPlayback)
        assertTrue(disabled.withAllowSimultaneousPlayback(true).allowSimultaneousPlayback)
    }
}
