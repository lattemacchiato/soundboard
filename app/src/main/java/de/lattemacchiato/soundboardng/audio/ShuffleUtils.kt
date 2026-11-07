package de.lattemacchiato.soundboardng.audio

internal fun <T> shuffledWithoutImmediateRepeat(
    items: List<T>,
    previousLast: T?,
): List<T> {
    val shuffled = items.shuffled().toMutableList()

    if (
        shuffled.size > 1 &&
        previousLast != null &&
        shuffled.first() == previousLast
    ) {
        val swapIndex = shuffled.indexOfFirst { it != previousLast }
        if (swapIndex > 0) {
            val first = shuffled[0]
            shuffled[0] = shuffled[swapIndex]
            shuffled[swapIndex] = first
        }
    }

    return shuffled
}
