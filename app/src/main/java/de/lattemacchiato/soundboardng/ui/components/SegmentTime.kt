package de.lattemacchiato.soundboardng.ui.components

import java.util.Locale

/**
 * Formats a playback position as mm.ss.SSS.
 * Minutes may exceed 99 for long audio files.
 */
internal fun formatSegmentTime(positionMs: Long): String {
    val safeMs = positionMs.coerceAtLeast(0L)
    val minutes = safeMs / 60_000L
    val seconds = (safeMs / 1_000L) % 60L
    val milliseconds = safeMs % 1_000L

    return String.format(
        Locale.US,
        "%02d.%02d.%03d",
        minutes,
        seconds,
        milliseconds,
    )
}

/**
 * Parses mm.ss.SSS into milliseconds.
 * Returns null for malformed or out-of-range values.
 */
internal fun parseSegmentTime(text: String): Long? {
    val match =
        Regex("""^(\d+)\.([0-5]\d)\.(\d{3})$""")
            .matchEntire(text.trim()) ?: return null

    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val seconds = match.groupValues[2].toLongOrNull() ?: return null
    val milliseconds = match.groupValues[3].toLongOrNull() ?: return null

    if (minutes > (Long.MAX_VALUE - seconds * 1_000L - milliseconds) / 60_000L) {
        return null
    }

    return minutes * 60_000L + seconds * 1_000L + milliseconds
}
