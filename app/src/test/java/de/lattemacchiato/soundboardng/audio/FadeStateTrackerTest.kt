package de.lattemacchiato.soundboardng.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeStateTrackerTest {
    @Test
    fun initiallyEmpty() {
        val tracker = FadeStateTracker()

        assertTrue(tracker.states.value.isEmpty())
    }

    @Test
    fun startFadeIn() {
        val tracker = FadeStateTracker()

        tracker.start("sound1", FadeStateTracker.State.FADING_IN)

        assertEquals(
            FadeStateTracker.State.FADING_IN,
            tracker.states.value["sound1"],
        )
    }

    @Test
    fun startFadeOut() {
        val tracker = FadeStateTracker()

        tracker.start("sound1", FadeStateTracker.State.FADING_OUT)

        assertEquals(
            FadeStateTracker.State.FADING_OUT,
            tracker.states.value["sound1"],
        )
    }

    @Test
    fun fadeDirectionCanChange() {
        val tracker = FadeStateTracker()

        tracker.start("sound1", FadeStateTracker.State.FADING_IN)
        tracker.start("sound1", FadeStateTracker.State.FADING_OUT)

        assertEquals(
            FadeStateTracker.State.FADING_OUT,
            tracker.states.value["sound1"],
        )
    }

    @Test
    fun clearRemovesOnlySpecifiedSound() {
        val tracker = FadeStateTracker()

        tracker.start("sound1", FadeStateTracker.State.FADING_IN)
        tracker.start("sound2", FadeStateTracker.State.FADING_OUT)
        tracker.clear("sound1")

        assertFalse(tracker.states.value.containsKey("sound1"))
        assertEquals(
            FadeStateTracker.State.FADING_OUT,
            tracker.states.value["sound2"],
        )
    }

    @Test
    fun clearAllRemovesEveryState() {
        val tracker = FadeStateTracker()

        tracker.start("sound1", FadeStateTracker.State.FADING_IN)
        tracker.start("sound2", FadeStateTracker.State.FADING_OUT)
        tracker.clearAll()

        assertTrue(tracker.states.value.isEmpty())
    }
}
