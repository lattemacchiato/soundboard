
package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigurationValidatorTest {

    private fun libraryWithUri(uri: String): SoundLibrary =
        SoundLibrary().withSound(
            id = "test-sound",
            uri = uri,
            name = "Test Sound"
        )

    @Test
    fun contentUriIsAccepted() {
        ConfigurationValidator.validate(
            libraryWithUri("content://media/external/audio/123")
        )
    }

    @Test
    fun fileUriIsAccepted() {
        ConfigurationValidator.validate(
            libraryWithUri("file:///data/user/0/de.lattemacchiato.soundboardng/files/backup-audio/test.mp3")
        )
    }

    @Test
    fun unsupportedUriSchemeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationValidator.validate(
                libraryWithUri("https://example.com/sound.mp3")
            )
        }
    }

    @Test
    fun emptyUriIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationValidator.validate(libraryWithUri(""))
        }
    }
}
