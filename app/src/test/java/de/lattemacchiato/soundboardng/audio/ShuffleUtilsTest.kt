package de.lattemacchiato.soundboardng.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleUtilsTest {
    @Test
    fun emptyListRemainsEmpty() {
        val result =
            shuffledWithoutImmediateRepeat(
                emptyList<String>(),
                null,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun singleItemRemainsUnchanged() {
        val result =
            shuffledWithoutImmediateRepeat(
                listOf("A"),
                "A",
            )

        assertEquals(listOf("A"), result)
    }

    @Test
    fun shufflePreservesAllItemsExactlyOnce() {
        val items = listOf("A", "B", "C", "D")

        repeat(100) {
            val result = shuffledWithoutImmediateRepeat(items, null)

            assertEquals(items.size, result.size)
            assertEquals(items.toSet(), result.toSet())
        }
    }

    @Test
    fun avoidsImmediateRepeatBetweenPasses() {
        val items = listOf("A", "B", "C")

        repeat(100) {
            val result = shuffledWithoutImmediateRepeat(items, "B")

            assertNotEquals("B", result.first())
            assertEquals(items.toSet(), result.toSet())
        }
    }

    @Test
    fun twoItemsNeverRepeatAtPassBoundary() {
        val items = listOf("A", "B")

        repeat(100) {
            val result = shuffledWithoutImmediateRepeat(items, "A")

            assertEquals(listOf("B", "A"), result)
        }
    }
}
