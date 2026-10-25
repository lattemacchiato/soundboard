package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TapBehaviorTest {
    @Test fun existingSoundsDefaultToPauseResume() {
        val sound = Sound("a", "content://a", "A")
        assertEquals(TapBehavior.PAUSE_RESUME, sound.tapBehavior)
    }

    @Test fun globalDefaultAppliesOnlyToNewSounds() {
        val first = SoundLibrary().withSound("a", "content://a", "A")
        val updated = first.withDefaultTapBehavior(TapBehavior.STOP_RESTART)
            .withSound("b", "content://b", "B")
        assertEquals(TapBehavior.PAUSE_RESUME, updated.sounds[0].tapBehavior)
        assertEquals(TapBehavior.STOP_RESTART, updated.sounds[1].tapBehavior)
    }

    @Test fun perSoundSettingDoesNotChangeOtherSounds() {
        val library = SoundLibrary().withSound("a", "content://a", "A")
            .withSound("b", "content://b", "B")
            .withTapBehavior("a", TapBehavior.STOP_RESTART)
        assertEquals(TapBehavior.STOP_RESTART, library.sounds[0].tapBehavior)
        assertEquals(TapBehavior.PAUSE_RESUME, library.sounds[1].tapBehavior)
    }
}
