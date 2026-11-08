package de.lattemacchiato.soundboardng.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentTimeTest {
    @Test
    fun `formats milliseconds precisely`() {
        assertEquals("00.00.000", formatSegmentTime(0L))
        assertEquals("00.01.250", formatSegmentTime(1_250L))
        assertEquals("01.23.450", formatSegmentTime(83_450L))
        assertEquals("120.05.007", formatSegmentTime(7_205_007L))
    }

    @Test
    fun `negative positions are formatted as zero`() {
        assertEquals("00.00.000", formatSegmentTime(-1L))
    }

    @Test
    fun `parses valid timestamps`() {
        assertEquals(0L, parseSegmentTime("00.00.000"))
        assertEquals(83_450L, parseSegmentTime("01.23.450"))
        assertEquals(7_205_007L, parseSegmentTime("120.05.007"))
    }

    @Test
    fun `parser tolerates surrounding whitespace`() {
        assertEquals(1_250L, parseSegmentTime(" 00.01.250 "))
    }

    @Test
    fun `rejects malformed timestamps`() {
        listOf(
            "",
            "1.23",
            "01.23.45",
            "01.23.4500",
            "01.60.000",
            "01.75.000",
            "-01.00.000",
            "01:23.450",
            "abc",
        ).forEach {
            assertNull("Should reject. $it", parseSegmentTime(it))
        }
    }

    @Test
    fun `rejects timestamps exceeding Long range`() {
        assertNull(parseSegmentTime("999999999999999999999.00.000"))
        assertNull(parseSegmentTime("153722867280913.00.000"))
    }

    @Test
    fun `format and parse round trip`() {
        listOf(
            0L,
            1L,
            999L,
            1_000L,
            60_000L,
            83_450L,
            7_205_007L,
            Long.MAX_VALUE,
        ).forEach { position ->
            assertEquals(position, parseSegmentTime(formatSegmentTime(position)))
        }
    }
}
