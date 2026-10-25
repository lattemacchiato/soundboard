package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.Serializable

@Serializable
data class SoundBoard(
    val id: String = SoundLibrary.DEFAULT_BOARD_ID,
    val name: String = "Default",
    val sounds: List<Sound> = emptyList(),
)
