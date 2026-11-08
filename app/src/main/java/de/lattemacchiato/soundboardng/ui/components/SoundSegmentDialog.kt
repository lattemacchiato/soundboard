package de.lattemacchiato.soundboardng.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.lattemacchiato.soundboardng.data.Sound
import kotlin.math.roundToLong

/**
 * Configures the playable range of a single audio file.
 *
 * Changes remain local until Save is pressed.
 */
@Composable
fun SoundSegmentDialog(
    sound: Sound,
    onSave: (startMs: Long, endMs: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var startMs by remember(sound.id) {
        mutableStateOf(sound.startPositionMs)
    }
    var endMs by remember(sound.id) {
        mutableStateOf(sound.endPositionMs)
    }

    val context = LocalContext.current

    var durationMs by remember(sound.id) {
        mutableStateOf<Long?>(null)
    }
    var loadError by remember(sound.id) {
        mutableStateOf(false)
    }

    var previewActive by remember(sound.id) {
        mutableStateOf(false)
    }

    val previewPlayer =
        remember(context, sound.id, sound.uri) {
            ExoPlayer.Builder(context).build()
        }

    DisposableEffect(previewPlayer, sound.uri) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        previewActive = false
                    }
                    if (playbackState == Player.STATE_READY) {
                        val duration = previewPlayer.duration
                        if (!previewActive) {
                            durationMs =
                                duration.takeIf {
                                    it != C.TIME_UNSET && it > 0L
                                }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    loadError = true
                    previewActive = false
                }
            }

        previewPlayer.addListener(listener)
        previewPlayer.setMediaItem(MediaItem.fromUri(sound.uri.toUri()))
        previewPlayer.prepare()

        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    var startText by remember(sound.id) {
        mutableStateOf(formatSegmentTime(sound.startPositionMs))
    }
    var endText by remember(sound.id) {
        mutableStateOf(sound.endPositionMs?.let(::formatSegmentTime) ?: "")
    }

    val parsedStart = parseSegmentTime(startText)
    val parsedEnd =
        if (endText.isBlank()) null else parseSegmentTime(endText)

    val validSegment =
        !loadError &&
            durationMs?.let { duration ->
                parsedStart != null &&
                    parsedStart >= 0L &&
                    parsedStart < duration &&
                    (
                        endText.isBlank() ||
                            (
                                parsedEnd != null &&
                                    parsedEnd > parsedStart &&
                                    parsedEnd <= duration
                            )
                    )
            } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback segment") },
        text = {
            Column {
                when {
                    loadError -> Text("Unable to load audio file")
                    durationMs == null -> Text("Loading audio duration...")
                    else -> {
                        val duration = durationMs!!
                        val effectiveEnd = (endMs ?: duration).coerceIn(0L, duration)
                        val effectiveStart = startMs.coerceIn(0L, duration)

                        Text("Duration: ${formatSegmentTime(duration)}")

                        Text("Start: ${formatSegmentTime(startMs)}")
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { text ->
                                startText = text
                                val parsed = parseSegmentTime(text)
                                if (parsed != null &&
                                    parsed >= 0L &&
                                    parsed < effectiveEnd
                                ) {
                                    startMs = parsed
                                }
                            },
                            label = { Text("Start (mm.ss.SSS)") },
                            singleLine = true,
                            isError =
                                parseSegmentTime(startText)?.let {
                                    it >= effectiveEnd
                                } ?: true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = effectiveStart.toFloat(),
                            onValueChange = { value ->
                                startMs =
                                    value
                                        .roundToLong()
                                        .coerceIn(0L, (effectiveEnd - 1L).coerceAtLeast(0L))
                                startText = formatSegmentTime(startMs)
                            },
                            valueRange = 0f..duration.toFloat(),
                            enabled = duration > 1L && effectiveEnd > 0L,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            "End: ${endMs?.let(::formatSegmentTime) ?: "End of file"}",
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { text ->
                                endText = text
                                if (text.isBlank()) {
                                    endMs = null
                                } else {
                                    val parsed = parseSegmentTime(text)
                                    if (parsed != null &&
                                        parsed > effectiveStart &&
                                        parsed <= duration
                                    ) {
                                        endMs = parsed.takeIf { it < duration }
                                    }
                                }
                            },
                            label = { Text("End (mm.ss.SSS)") },
                            placeholder = { Text("End of file") },
                            singleLine = true,
                            isError =
                                endText.isNotBlank() &&
                                    (
                                        parseSegmentTime(endText)?.let {
                                            it <= effectiveStart || it > duration
                                        } ?: true
                                    ),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = effectiveEnd.toFloat(),
                            onValueChange = { value ->
                                val selected =
                                    value
                                        .roundToLong()
                                        .coerceIn(
                                            (effectiveStart + 1L).coerceAtMost(duration),
                                            duration,
                                        )
                                endMs = selected.takeIf { it < duration }
                                endText = endMs?.let(::formatSegmentTime) ?: ""
                            },
                            valueRange = 0f..duration.toFloat(),
                            enabled = duration > 1L && effectiveStart < duration,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        TextButton(
                            onClick = {
                                previewPlayer.stop()
                                previewActive = false
                                startMs = 0L
                                endMs = null
                                startText = formatSegmentTime(0L)
                                endText = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset to full file")
                        }

                        Button(
                            onClick = {
                                if (previewActive) {
                                    previewPlayer.stop()
                                    previewActive = false
                                } else if (validSegment) {
                                    val clipping =
                                        MediaItem.ClippingConfiguration
                                            .Builder()
                                            .setStartPositionMs(parsedStart!!)
                                            .setEndPositionMs(
                                                parsedEnd ?: C.TIME_END_OF_SOURCE,
                                            ).build()

                                    val mediaItem =
                                        MediaItem
                                            .Builder()
                                            .setUri(sound.uri.toUri())
                                            .setClippingConfiguration(clipping)
                                            .build()

                                    previewActive = true
                                    previewPlayer.stop()
                                    previewPlayer.setMediaItem(mediaItem)
                                    previewPlayer.prepare()
                                    previewPlayer.play()
                                }
                            },
                            enabled = previewActive || validSegment,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (previewActive) "Stop preview" else "Play preview")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validSegment) {
                        onSave(
                            parsedStart!!,
                            parsedEnd?.takeIf { it < durationMs!! },
                        )
                        onDismiss()
                    }
                },
                enabled = validSegment,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
