
package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigurationValidatorTest {
    private fun libraryWithUri(uri: String): SoundLibrary =
        SoundLibrary().withSound(
            id = "test-sound",
            uri = uri,
            name = "Test Sound",
        )

    @Test
    fun contentUriIsAccepted() {
        ConfigurationValidator.validate(
            libraryWithUri("content://media/external/audio/123"),
        )
    }

    @Test
    fun fileUriIsAccepted() {
        ConfigurationValidator.validate(
            libraryWithUri("file:///data/user/0/de.lattemacchiato.soundboardng/files/backup-audio/test.mp3"),
        )
    }

    @Test
    fun unsupportedUriSchemeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationValidator.validate(
                libraryWithUri("https://example.com/sound.mp3"),
            )
        }
    }

    @Test
    fun emptyUriIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationValidator.validate(libraryWithUri(""))
        }
    }

    @Test
    fun duplicateSoundIdsAcrossBoardsAreRejected() {
        val library =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "board-a",
                            name = "Act 1",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "same-id",
                                        uri = "content://audio/1",
                                        name = "Pausengong",
                                    ),
                                ),
                        ),
                        SoundBoard(
                            id = "board-b",
                            name = "Act 2",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "same-id",
                                        uri = "content://audio/2",
                                        name = "Pausengong",
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "board-a",
            )

        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationValidator.validate(library)
        }
    }

    @Test
    fun sameAudioUriAcrossBoardsIsAccepted() {
        val library =
            SoundLibrary(
                boards =
                    listOf(
                        SoundBoard(
                            id = "board-a",
                            name = "Act 1",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "sound-a",
                                        uri = "content://audio/pausengong",
                                        name = "Pausengong",
                                    ),
                                ),
                        ),
                        SoundBoard(
                            id = "board-b",
                            name = "Act 2",
                            sounds =
                                listOf(
                                    Sound(
                                        id = "sound-b",
                                        uri = "content://audio/pausengong",
                                        name = "Pausengong",
                                    ),
                                ),
                        ),
                    ),
                activeBoardId = "board-a",
            )

        ConfigurationValidator.validate(library)
    }
}
