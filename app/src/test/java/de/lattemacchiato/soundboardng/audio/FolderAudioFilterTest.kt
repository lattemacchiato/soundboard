package de.lattemacchiato.soundboardng.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAudioFilterTest {
    @Test
    fun `supported audio formats are accepted`() {
        val extensions =
            listOf(
                "mp3",
                "wav",
                "ogg",
                "flac",
                "m4a",
                "aac",
                "opus",
                "aiff",
                "aif",
                "amr",
            )

        extensions.forEach { extension ->
            assertTrue(
                "Expected .$extension to be accepted",
                isSupportedFolderAudioFile("sound.$extension"),
            )
        }
    }

    @Test
    fun `non audio files are rejected`() {
        val extensions =
            listOf(
                "txt",
                "jpg",
                "png",
                "pdf",
                "m3u",
                "mid",
                "midi",
                "kar",
                "zip",
                "json",
            )

        extensions.forEach { extension ->
            assertFalse(
                "Expected .$extension to be rejected",
                isSupportedFolderAudioFile("file.$extension"),
            )
        }
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertTrue(isSupportedFolderAudioFile("Music.MP3"))
        assertTrue(isSupportedFolderAudioFile("Music.WaV"))
        assertFalse(isSupportedFolderAudioFile("Music.MID"))
    }

    @Test
    fun `unknown or missing extensions are rejected`() {
        assertFalse(isSupportedFolderAudioFile("README"))
        assertFalse(isSupportedFolderAudioFile("sound.unknown"))
        assertFalse(isSupportedFolderAudioFile(".hidden"))
    }

    @Test
    fun `mime type cannot override extension filtering`() {
        assertFalse(isSupportedFolderAudioFile("music.mid", "audio/midi"))
        assertFalse(isSupportedFolderAudioFile("cover.jpg", "audio/mpeg"))
        assertTrue(isSupportedFolderAudioFile("music.mp3", "application/octet-stream"))
        assertTrue(isSupportedFolderAudioFile("music.wav", null))
    }
}
