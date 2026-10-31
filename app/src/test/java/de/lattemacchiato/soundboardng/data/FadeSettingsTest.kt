package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeSettingsTest {
    @Test fun defaultsAndExistingSounds() {
        val old = Sound("old", "uri:old", "Old")
        assertEquals(0, old.fadeInMs)
        assertEquals(0, old.fadeOutMs)
        val library =
            SoundLibrary()
                .withDefaultFade(true, 1500)
                .withDefaultFade(false, 2500)
                .withSound("new", "uri:new", "New")
        assertEquals(1500, library.find("new")?.fadeInMs)
        assertEquals(2500, library.find("new")?.fadeOutMs)
        assertEquals(0, old.fadeInMs)
    }

    @Test fun perSoundOverrideDoesNotChangeDefaults() {
        val library =
            SoundLibrary()
                .withDefaultFade(true, 1000)
                .withSound("one", "uri:one", "One")
                .withFade("one", true, 3000)
                .withSound("two", "uri:two", "Two")
        assertEquals(3000, library.find("one")?.fadeInMs)
        assertEquals(1000, library.find("two")?.fadeInMs)
        assertEquals(1000, library.defaultFadeInMs)
    }

    @Test fun durationsAreClamped() {
        val library =
            SoundLibrary()
                .withDefaultFade(true, 9999)
                .withSound("one", "uri:one", "One")
                .withFade("one", false, -500)
        assertEquals(5000, library.find("one")?.fadeInMs)
        assertEquals(0, library.find("one")?.fadeOutMs)
    }
}
