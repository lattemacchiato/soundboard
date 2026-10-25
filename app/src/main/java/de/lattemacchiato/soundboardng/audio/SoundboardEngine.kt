package de.lattemacchiato.soundboardng.audio

import android.content.Context
import android.media.AudioManager
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.lattemacchiato.soundboardng.data.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mixes any number of looping sounds, each with its own gain.
 *
 * One [ExoPlayer] per sound (see `DECISIONS.md` → "Media3 ExoPlayer, One Player
 * Per Sound"). Players are created on first play and retained while paused so
 * resume is instant; they are released when the sound leaves the board or the
 * whole engine shuts down.
 *
 * **Threading:** ExoPlayer instances must be touched only from the thread that
 * built them. Every method here is main-thread-only, and the engine is built on
 * the main thread by [de.lattemacchiato.soundboardng.SoundboardApp].
 *
 * **Audio focus** is requested once for the engine as a whole rather than per
 * player: N players each requesting focus would be N competing requests from
 * the same app, and the last one would win against its own siblings.
 */
@MainThread
class SoundboardEngine(
    private val context: Context,
) {
    private val players = mutableMapOf<String, ExoPlayer>()
    private val fadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fadeJobs = mutableMapOf<String, Job>()
    private val fadeFactors = mutableMapOf<String, Float>()
    private val fadingOut = mutableSetOf<String>()

    var allowSimultaneousPlayback: Boolean = true
        private set

    fun setAllowSimultaneousPlayback(allow: Boolean) {
        allowSimultaneousPlayback = allow
        if (!allow) {
            val playing = _statuses.value.filterValues { it == PlaybackStatus.PLAYING }.keys.toList()
            // Preserve one currently playing sound when switching modes.
            playing.drop(1).forEach(::stopImmediately)
        }
    }

    private fun stopImmediately(id: String) {
        // release() also cancels pending fade completions and resets playback.
        release(id)
    }

    private fun stopOtherPlayingSounds(exceptId: String) {
        if (allowSimultaneousPlayback) return
        _statuses.value.filter { (id, status) ->
            id != exceptId && status == PlaybackStatus.PLAYING
        }.keys.toList().forEach(::stopImmediately)
    }

    fun isFadingOut(id: String): Boolean = id in fadingOut

    private fun cancelFade(id: String) {
        fadeJobs.remove(id)?.cancel()
        fadingOut.remove(id)
    }

    private fun updateVolume(id: String) {
        players[id]?.volume = (gains[id] ?: 1f) * duckFactor * (fadeFactors[id] ?: 1f)
    }

    private fun animateFade(id: String, target: Float, durationMs: Int, finished: () -> Unit = {}) {
        cancelFade(id)
        if (target == 0f) fadingOut.add(id)
        val start = fadeFactors[id] ?: 1f
        val duration = durationMs.coerceIn(0, 5000)
        if (duration == 0 || start == target) {
            fadingOut.remove(id)
            fadeFactors[id] = target
            updateVolume(id)
            finished()
            return
        }
        fadeJobs[id] = fadeScope.launch {
            val startTime = SystemClock.uptimeMillis()
            while (true) {
                val fraction = ((SystemClock.uptimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                fadeFactors[id] = start + (target - start) * fraction
                updateVolume(id)
                if (fraction >= 1f) break
                delay(16L)
            }
            fadeJobs.remove(id)
            fadingOut.remove(id)
            finished()
        }
    }

    /** Gains last requested by the UI, kept so ducking can be applied on top. */
    private val gains = mutableMapOf<String, Float>()

    private val _statuses = MutableStateFlow<Map<String, PlaybackStatus>>(emptyMap())

    /** Per-sound status, keyed by [Sound.id]. Absent means [PlaybackStatus.IDLE]. */
    val statuses: StateFlow<Map<String, PlaybackStatus>> = _statuses.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = AudioFocusHolder(audioManager, ::onFocusChanged)

    /** Multiplier applied to every player while another app has ducked us. */
    private var duckFactor = 1f

    /** Sounds paused by focus loss, to be resumed if focus comes back. */
    private val pausedByFocusLoss = mutableSetOf<String>()

    /** True while any sound is audible — drives the foreground service lifetime. */
    val isAnythingPlaying: Boolean
        get() = _statuses.value.any { it.value == PlaybackStatus.PLAYING }

    fun play(sound: Sound) {
        // A player that hit an error stays in that state and ignores play(),
        // so retrying a restored file needs a fresh one.
        if (_statuses.value[sound.id] == PlaybackStatus.UNAVAILABLE) {
            players.remove(sound.id)?.release()
        }
        val player = players.getOrPut(sound.id) { buildPlayer(sound) }
        gains[sound.id] = sound.gain
        cancelFade(sound.id)
        fadeFactors[sound.id] = 0f
        updateVolume(sound.id)
        player.repeatMode = if (sound.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (!focus.request()) return
        stopOtherPlayingSounds(sound.id)
        if (!focus.request()) return
        pausedByFocusLoss.remove(sound.id)
        player.play()
        setStatus(sound.id, PlaybackStatus.PLAYING)
        animateFade(sound.id, 1f, sound.fadeInMs)
    }

    /** Start from the beginning even if this sound is currently playing or paused. */
    fun restart(sound: Sound) {
        if (_statuses.value[sound.id] == PlaybackStatus.UNAVAILABLE) {
            players.remove(sound.id)?.release()
        }
        val player = players.getOrPut(sound.id) { buildPlayer(sound) }
        gains[sound.id] = sound.gain
        cancelFade(sound.id)
        fadeFactors[sound.id] = 0f
        updateVolume(sound.id)
        player.repeatMode = if (sound.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (!focus.request()) return
        stopOtherPlayingSounds(sound.id)
        if (!focus.request()) return
        pausedByFocusLoss.remove(sound.id)
        player.seekTo(0L)
        player.play()
        setStatus(sound.id, PlaybackStatus.PLAYING)
        animateFade(sound.id, 1f, sound.fadeInMs)
    }

    /** Apply a changed loop setting without restarting a running player. */
    fun setLoop(id: String, loop: Boolean) {
        players[id]?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun pauseNow(id: String) {
        cancelFade(id)
        players[id]?.pause()
        pausedByFocusLoss.remove(id)
        if (_statuses.value[id] != PlaybackStatus.UNAVAILABLE) setStatus(id, PlaybackStatus.PAUSED)
        abandonFocusIfSilent()
    }

    fun pause(id: String) = pauseNow(id)

    fun pause(sound: Sound) {
        if (_statuses.value[sound.id] != PlaybackStatus.PLAYING) return
        animateFade(sound.id, 0f, sound.fadeOutMs) { pauseNow(sound.id) }
    }

    /** Fade out and reset to the beginning; a subsequent tap starts fresh. */
    fun stop(sound: Sound) {
        if (_statuses.value[sound.id] != PlaybackStatus.PLAYING) {
            release(sound.id)
            return
        }
        animateFade(sound.id, 0f, sound.fadeOutMs) { release(sound.id) }
    }

    fun toggle(sound: Sound) {
        if (_statuses.value[sound.id] == PlaybackStatus.PLAYING && !isFadingOut(sound.id)) pause(sound) else play(sound)
    }

    /** Applies a new relative volume live, without interrupting playback. */
    fun setGain(
        id: String,
        gain: Float,
    ) {
        gains[id] = gain
        updateVolume(id)
    }

    /** Pauses every sound. Backs the notification's "Stop all" action. */
    fun pauseAll() {
        _statuses.value
            .filterValues { it == PlaybackStatus.PLAYING }
            .keys
            .toList()
            .forEach(::pauseNow)
    }

    /** Releases one sound's player — call when it is removed from the board. */
    fun release(id: String) {
        cancelFade(id)
        fadeFactors.remove(id)
        players.remove(id)?.release()
        gains.remove(id)
        pausedByFocusLoss.remove(id)
        _statuses.value = _statuses.value - id
        abandonFocusIfSilent()
    }

    /** Releases everything. The engine is unusable afterwards. */
    fun releaseAll() {
        fadeJobs.values.toList().forEach { it.cancel() }
        fadeJobs.clear()
        fadeFactors.clear()
        fadingOut.clear()
        fadeScope.cancel()
        players.values.forEach(ExoPlayer::release)
        players.clear()
        gains.clear()
        pausedByFocusLoss.clear()
        _statuses.value = emptyMap()
        focus.abandon()
    }

    private fun buildPlayer(sound: Sound): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus: false, because AudioFocusHolder owns
                    // one request for the whole engine. See AudioFocusHolder.
                    false,
                )
                repeatMode = if (sound.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(sound.uri.toUri()))
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED &&
                                _statuses.value[sound.id] != PlaybackStatus.UNAVAILABLE
                            ) {
                                // One-shot reached its end: reset so the next tap starts at zero.
                                cancelFade(sound.id)
                                fadeFactors.remove(sound.id)
                                pause()
                                seekTo(0L)
                                pausedByFocusLoss.remove(sound.id)
                                setStatus(sound.id, PlaybackStatus.IDLE)
                                abandonFocusIfSilent()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            cancelFade(sound.id)
                            setStatus(sound.id, PlaybackStatus.UNAVAILABLE)
                            abandonFocusIfSilent()
                        }
                    },
                )
                prepare()
            }

    private fun setStatus(
        id: String,
        status: PlaybackStatus,
    ) {
        _statuses.value = _statuses.value + (id to status)
    }

    private fun abandonFocusIfSilent() {
        if (!isAnythingPlaying) focus.abandon()
    }

    private fun onFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocusLoss.clear()
                pauseAll()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Remember what was playing so AUDIOFOCUS_GAIN can restore exactly that.
                _statuses.value
                    .filterValues { it == PlaybackStatus.PLAYING }
                    .keys
                    .forEach { id ->
                        pausedByFocusLoss += id
                        cancelFade(id)
                        players[id]?.pause()
                        setStatus(id, PlaybackStatus.PAUSED)
                    }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> applyDuck(DUCK_FACTOR)

            AudioManager.AUDIOFOCUS_GAIN -> {
                applyDuck(1f)
                pausedByFocusLoss.toList().forEach { id ->
                    fadeFactors[id] = 1f
                    updateVolume(id)
                    players[id]?.play()
                    setStatus(id, PlaybackStatus.PLAYING)
                }
                pausedByFocusLoss.clear()
            }
        }
    }

    private fun applyDuck(factor: Float) {
        duckFactor = factor
        players.forEach { (id, player) ->
            updateVolume(id)
        }
    }

    private companion object {
        /** Conventional ducking level while another app speaks over us. */
        const val DUCK_FACTOR = 0.2f
    }
}
