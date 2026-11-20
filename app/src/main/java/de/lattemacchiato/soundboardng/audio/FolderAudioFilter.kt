package de.lattemacchiato.soundboardng.audio

/**
 * Identifies audio file formats supported for folder playback.
 *
 * File extensions are authoritative because SAF providers may report
 * generic or misleading MIME types (including audio MIME types for MIDI files).
 *
 * This does not validate file contents or decoder compatibility.
 */
private val supportedFolderAudioExtensions =
    setOf(
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

@Suppress("UNUSED_PARAMETER")
internal fun isSupportedFolderAudioFile(
    name: String,
    mimeType: String? = null,
): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in supportedFolderAudioExtensions
}
