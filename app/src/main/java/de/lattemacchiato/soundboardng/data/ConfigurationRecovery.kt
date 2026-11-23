package de.lattemacchiato.soundboardng.data

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Atomically saves the configuration before a JSON or ZIP import.
 *
 * An existing recovery file is preserved if writing fails.
 */
internal object ConfigurationRecovery {
    fun save(
        context: Context,
        payload: String,
    ) {
        val file =
            AtomicFile(
                File(context.filesDir, "soundboard-before-import.json"),
            )

        val output = file.startWrite()
        try {
            output.write(payload.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }
}
