
package de.lattemacchiato.soundboardng.data

internal object ConfigurationValidator {

    fun validate(library: SoundLibrary) {
        // Validate library structure
        require(library.boards.isNotEmpty() && library.boards.size <= 1000) {
            "Invalid board count"
        }

        val boardIds = library.boards.map { it.id }

        require(boardIds.all { it.isNotBlank() }) {
            "Invalid board ID"
        }

        require(boardIds.distinct().size == boardIds.size) {
            "Duplicate board IDs"
        }

        require(library.activeBoardId in boardIds) {
            "Active board not found"
        }

        // Validate global settings
        require(
            library.defaultFadeInMs in 0..5000 &&
                library.defaultFadeOutMs in 0..5000
        ) {
            "Invalid default fade settings"
        }

        // Validate boards and their sounds
        for (board in library.boards) {
            require(board.name.isNotBlank()) {
                "Invalid board name"
            }

            require(board.sounds.size <= 10000) {
                "Too many sounds"
            }

            val soundIds = board.sounds.map { it.id }

            require(soundIds.distinct().size == soundIds.size) {
                "Duplicate sound IDs"
            }

            for (sound in board.sounds) {
                require(sound.id.isNotBlank()) {
                    "Invalid sound ID"
                }

                require(sound.name.isNotBlank()) {
                    "Invalid sound name"
                }

                require(
                    sound.uri.startsWith("content://") ||
                        sound.uri.startsWith("file://")
                ) {
                    "Invalid sound URI"
                }

                require(sound.volumePercent in 0..100) {
                    "Invalid sound volume"
                }

                require(
                    sound.fadeInMs in 0..5000 &&
                        sound.fadeOutMs in 0..5000
                ) {
                    "Invalid sound fade settings"
                }
            }
        }
    }
}
