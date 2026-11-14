package de.lattemacchiato.soundboardng.audio

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import de.lattemacchiato.soundboardng.data.PlaybackOrder
import de.lattemacchiato.soundboardng.data.Sound
import de.lattemacchiato.soundboardng.data.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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
    private val playerSounds = mutableMapOf<String, Sound>()
    private val soundTypes = mutableMapOf<String, SoundType>()
    private val folderResolver = FolderAudioResolver(context.contentResolver)
    private val folderLoadJobs = mutableMapOf<String, Job>()
    private val folderAudioFiles =
        mutableMapOf<String, List<FolderAudioResolver.AudioFile>>()
    private val folderPlaybackOrders =
        mutableMapOf<String, PlaybackOrder>()
    private val fadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fadeJobs = mutableMapOf<String, Job>()

    private val fadeStateTracker = FadeStateTracker()

    val fadeStates: StateFlow<Map<String, FadeStateTracker.State>> =
        fadeStateTracker.states
    private val segmentFadeJobs = mutableMapOf<String, Job>()
    private val fadeFactors = mutableMapOf<String, Float>()
    private val segmentFadeFactors = mutableMapOf<String, Float>()
    private val fadingOut = mutableSetOf<String>()
    var allowSimultaneousPlayback: Boolean = true
        private set

    fun setAllowSimultaneousPlayback(allow: Boolean) {
        allowSimultaneousPlayback = allow
        if (!allow) {
            val playing =
                _statuses.value
                    .filterValues { it == PlaybackStatus.PLAYING }
                    .keys
                    .toList()
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
        _statuses.value
            .filter { (id, status) ->
                id != exceptId && status == PlaybackStatus.PLAYING
            }.keys
            .toList()
            .forEach(::stopImmediately)
    }

    fun isFadingOut(id: String): Boolean = id in fadingOut

    private fun cancelFade(id: String) {
        fadeJobs.remove(id)?.cancel()
        fadingOut.remove(id)
        fadeStateTracker.clear(id)
    }

    private fun cancelSegmentFade(id: String) {
        segmentFadeJobs.remove(id)?.cancel()
        segmentFadeFactors.remove(id)
        updateVolume(id)
    }

    private fun startPlaybackFadeMonitor(
        sound: Sound,
        player: ExoPlayer,
    ) {
        cancelSegmentFade(sound.id)

        val fadeOutMs = sound.fadeOutMs.coerceIn(0, 5000)

        segmentFadeFactors[sound.id] = 1f

        segmentFadeJobs[sound.id] =
            fadeScope.launch {
                var previousPositionMs = player.currentPosition
                while (true) {
                    val positionMs = player.currentPosition

                    if (positionMs < previousPositionMs) {
                        // Playback jumped backwards (loop or seek).
                        segmentFadeFactors[sound.id] = 1f
                        fadeFactors[sound.id] = 0f
                        updateVolume(sound.id)
                        animateFade(sound.id, 1f, sound.fadeInMs)
                    }

                    previousPositionMs = positionMs
                    val durationMs = player.duration

                    val factor =
                        if (fadeOutMs == 0 || durationMs == C.TIME_UNSET || durationMs <= 0L) {
                            1f
                        } else {
                            val remainingMs = durationMs - positionMs
                            (remainingMs.toFloat() / fadeOutMs)
                                .coerceIn(0f, 1f)
                        }

                    if (segmentFadeFactors[sound.id] != factor) {
                        segmentFadeFactors[sound.id] = factor
                        updateVolume(sound.id)
                    }

                    delay(16.milliseconds)
                }
            }
    }

    private fun updateVolume(id: String) {
        players[id]?.volume =
            (gains[id] ?: 1f) *
            duckFactor *
            (fadeFactors[id] ?: 1f) *
            (segmentFadeFactors[id] ?: 1f)
    }

    private fun animateFade(
        id: String,
        target: Float,
        durationMs: Int,
        finished: () -> Unit = {},
        showFeedback: Boolean = false,
    ) {
        if (target == 0f) cancelSegmentFade(id)
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

        if (showFeedback) {
            val state =
                if (target == 0f) {
                    FadeStateTracker.State.FADING_OUT
                } else {
                    FadeStateTracker.State.FADING_IN
                }

            fadeStateTracker.start(id, state)
        }

        fadeJobs[id] =
            fadeScope.launch {
                val startTime = SystemClock.uptimeMillis()
                while (true) {
                    val fraction =
                        ((SystemClock.uptimeMillis() - startTime).toFloat() / duration)
                            .coerceIn(0f, 1f)

                    fadeFactors[id] = start + (target - start) * fraction
                    updateVolume(id)

                    if (fraction >= 1f) break
                    delay(16.milliseconds)
                }

                fadeJobs.remove(id)
                fadingOut.remove(id)
                fadeStateTracker.clear(id)
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
        if (folderLoadJobs[sound.id]?.isActive == true) return

        if (
            sound.type == SoundType.FOLDER &&
            (
                players[sound.id] == null ||
                    folderPlaybackOrders[sound.id] != sound.playbackOrder
            )
        ) {
            if (players[sound.id] != null) {
                release(sound.id)
            }

            // Immediate visual feedback while the folder is being loaded.
            if (sound.fadeInMs > 0) {
                fadeStateTracker.start(
                    sound.id,
                    FadeStateTracker.State.FADING_IN,
                )
            }

            loadFolder(sound) { player ->
                gains[sound.id] = sound.gain
                cancelFade(sound.id)
                fadeFactors[sound.id] = 0f
                updateVolume(sound.id)

                player.repeatMode =
                    when {
                        !sound.loop -> Player.REPEAT_MODE_OFF
                        sound.playbackOrder == PlaybackOrder.SHUFFLE ->
                            Player.REPEAT_MODE_OFF
                        else -> Player.REPEAT_MODE_ALL
                    }

                if (!focus.request()) return@loadFolder
                stopOtherPlayingSounds(sound.id)
                if (!focus.request()) return@loadFolder

                pausedByFocusLoss.remove(sound.id)
                player.play()
                setStatus(sound.id, PlaybackStatus.PLAYING)
                animateFade(
                    id = sound.id,
                    target = 1f,
                    durationMs = sound.fadeInMs,
                    showFeedback = true,
                )
            }
            return
        }

        // A player that hit an error stays in that state and ignores play(),
        // so retrying a restored file needs a fresh one.

        if (_statuses.value[sound.id] == PlaybackStatus.UNAVAILABLE) {
            players.remove(sound.id)?.release()
        }
        val player = players.getOrPut(sound.id) { buildPlayer(sound) }
        playerSounds[sound.id] = sound
        gains[sound.id] = sound.gain
        cancelFade(sound.id)
        fadeFactors[sound.id] = 0f
        updateVolume(sound.id)

        player.repeatMode =
            when {
                !sound.loop -> Player.REPEAT_MODE_OFF
                sound.type == SoundType.FOLDER &&
                    sound.playbackOrder == PlaybackOrder.SHUFFLE ->
                    Player.REPEAT_MODE_OFF
                sound.type == SoundType.FOLDER -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_ONE
            }

        if (!focus.request()) return
        stopOtherPlayingSounds(sound.id)
        if (!focus.request()) return
        pausedByFocusLoss.remove(sound.id)
        player.play()
        setStatus(sound.id, PlaybackStatus.PLAYING)
        animateFade(
            id = sound.id,
            target = 1f,
            durationMs = sound.fadeInMs,
            showFeedback = true,
        )
        startPlaybackFadeMonitor(sound, player)
    }

    /** Start from the beginning even if this sound is currently playing or paused. */
    fun restart(sound: Sound) {
        if (sound.type == SoundType.FOLDER && players[sound.id] == null) {
            play(sound)
            return
        }

        if (_statuses.value[sound.id] == PlaybackStatus.UNAVAILABLE) {
            players.remove(sound.id)?.release()
        }
        val player = players.getOrPut(sound.id) { buildPlayer(sound) }
        playerSounds[sound.id] = sound
        gains[sound.id] = sound.gain
        cancelSegmentFade(sound.id)
        cancelFade(sound.id)
        fadeFactors[sound.id] = 0f
        updateVolume(sound.id)
        player.repeatMode =
            when {
                !sound.loop -> Player.REPEAT_MODE_OFF
                sound.type == SoundType.FOLDER &&
                    sound.playbackOrder == PlaybackOrder.SHUFFLE ->
                    Player.REPEAT_MODE_OFF
                sound.type == SoundType.FOLDER -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_ONE
            }
        if (!focus.request()) return
        stopOtherPlayingSounds(sound.id)
        if (!focus.request()) return
        pausedByFocusLoss.remove(sound.id)
        if (sound.type == SoundType.FOLDER) {
            player.seekToDefaultPosition(0)
        } else {
            player.seekTo(0L)
        }
        player.play()
        setStatus(sound.id, PlaybackStatus.PLAYING)
        animateFade(
            id = sound.id,
            target = 1f,
            durationMs = sound.fadeInMs,
            showFeedback = true,
        )
        startPlaybackFadeMonitor(sound, player)
    }

    /** Apply a changed loop setting without restarting a running player. */
    fun setLoop(
        id: String,
        loop: Boolean,
    ) {
        players[id]?.repeatMode =
            when {
                !loop -> Player.REPEAT_MODE_OFF
                soundTypes[id] == SoundType.FOLDER -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_ONE
            }
    }

    private fun pauseNow(id: String) {
        cancelSegmentFade(id)
        cancelFade(id)
        players[id]?.pause()
        pausedByFocusLoss.remove(id)
        if (_statuses.value[id] != PlaybackStatus.UNAVAILABLE) setStatus(id, PlaybackStatus.PAUSED)
        abandonFocusIfSilent()
    }

    fun pause(sound: Sound) {
        if (_statuses.value[sound.id] != PlaybackStatus.PLAYING) return
        animateFade(
            id = sound.id,
            target = 0f,
            durationMs = sound.fadeOutMs,
            finished = { pauseNow(sound.id) },
            showFeedback = true,
        )
    }

    /** Fade out and reset to the beginning; a subsequent tap starts fresh. */
    fun stop(sound: Sound) {
        if (_statuses.value[sound.id] != PlaybackStatus.PLAYING) {
            release(sound.id)
            return
        }
        animateFade(
            id = sound.id,
            target = 0f,
            durationMs = sound.fadeOutMs,
            finished = { release(sound.id) },
            showFeedback = true,
        )
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
        folderLoadJobs.remove(id)?.cancel()
        cancelFade(id)
        cancelSegmentFade(id)
        fadeFactors.remove(id)
        players.remove(id)?.release()
        playerSounds.remove(id)
        folderAudioFiles.remove(id)
        folderPlaybackOrders.remove(id)
        soundTypes.remove(id)
        gains.remove(id)
        pausedByFocusLoss.remove(id)
        _statuses.value -= id
        abandonFocusIfSilent()
    }

    /**
     * Permanently shuts down the engine and releases all resources.
     *
     * Currently unused by design: SoundboardEngine is owned by the Application
     * and must survive Activity recreation and navigation.
     *
     * Normal cleanup releases individual players via release(id).
     * Keep this method for a potential explicit application shutdown or
     * future lifecycle changes. Do not call it during Activity disposal.
     */
    @Suppress("unused")
    fun releaseAll() {
        folderLoadJobs.values.toList().forEach { it.cancel() }
        folderLoadJobs.clear()

        fadeJobs.values.toList().forEach { it.cancel() }
        fadeJobs.clear()
        fadeFactors.clear()
        fadingOut.clear()
        fadeStateTracker.clearAll()
        segmentFadeJobs.values.toList().forEach { it.cancel() }
        segmentFadeJobs.clear()
        segmentFadeFactors.clear()
        fadeScope.cancel()
        players.values.forEach(ExoPlayer::release)
        players.clear()
        playerSounds.clear()
        soundTypes.clear()
        gains.clear()
        pausedByFocusLoss.clear()
        _statuses.value = emptyMap()
        focus.abandon()
    }

    @OptIn(UnstableApi::class)
    private fun loadFolder(
        sound: Sound,
        onReady: (ExoPlayer) -> Unit,
    ) {
        if (folderLoadJobs[sound.id]?.isActive == true) return

        val job =
            fadeScope.launch {
                try {
                    val files =
                        withContext(Dispatchers.IO) {
                            folderResolver.resolve(sound.uri.toUri())
                        }

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    if (files.isEmpty()) {
                        Log.w(
                            "SoundboardEngine",
                            "Folder contains no supported audio files: ${sound.name}",
                        )
                        setStatus(sound.id, PlaybackStatus.UNAVAILABLE)
                        return@launch
                    }

                    folderAudioFiles[sound.id] = files
                    folderPlaybackOrders[sound.id] = sound.playbackOrder
                    val player = buildPlayer(sound)

                    players[sound.id] = player

                    val mediaItems =
                        if (sound.playbackOrder == PlaybackOrder.SHUFFLE) {
                            createShufflePass(files, null)
                        } else {
                            files.map { MediaItem.fromUri(it.uri) }
                        }

                    player.setMediaItems(mediaItems)

                    if (
                        sound.type == SoundType.FOLDER &&
                        sound.playbackOrder == PlaybackOrder.SHUFFLE &&
                        sound.loop
                    ) {
                        appendShufflePass(player, files)
                    }

                    player.seekToDefaultPosition(
                        player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled),
                    )

                    player.prepare()
                    onReady(player)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SoundboardEngine", "Failed to load folder: ${sound.name}", e)
                    players.remove(sound.id)?.release()
                    folderAudioFiles.remove(sound.id)
                    folderPlaybackOrders.remove(sound.id)
                    soundTypes.remove(sound.id)
                    setStatus(sound.id, PlaybackStatus.UNAVAILABLE)
                } finally {
                    if (folderLoadJobs[sound.id] ===
                        kotlinx.coroutines.currentCoroutineContext()[Job]
                    ) {
                        folderLoadJobs.remove(sound.id)

                        // Clear loading feedback only for this load attempt.
                        if (_statuses.value[sound.id] != PlaybackStatus.PLAYING) {
                            fadeStateTracker.clear(sound.id)
                        }
                    }
                }
            }

        folderLoadJobs[sound.id] = job
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(sound: Sound): ExoPlayer {
        soundTypes[sound.id] = sound.type
        playerSounds[sound.id] = sound

        return ExoPlayer
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
                repeatMode =
                    when {
                        !sound.loop -> Player.REPEAT_MODE_OFF
                        sound.type == SoundType.FOLDER &&
                            sound.playbackOrder == PlaybackOrder.SHUFFLE ->
                            Player.REPEAT_MODE_OFF
                        sound.type == SoundType.FOLDER -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_ONE
                    }

                if (sound.type == SoundType.FILE) {
                    val clipping =
                        MediaItem.ClippingConfiguration
                            .Builder()
                            .setStartPositionMs(sound.startPositionMs)
                            .setEndPositionMs(sound.endPositionMs ?: C.TIME_END_OF_SOURCE)
                            .build()

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setUri(sound.uri.toUri())
                            .setClippingConfiguration(clipping)
                            .build()

                    setMediaItem(mediaItem)
                }
                addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (sound.type != SoundType.FOLDER) return
                            if (sound.playbackOrder != PlaybackOrder.SHUFFLE) return

                            if (
                                sound.loop &&
                                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                                currentMediaItemIndex == mediaItemCount - 1
                            ) {
                                folderAudioFiles[sound.id]?.let { files ->
                                    appendShufflePass(this@apply, files)

                                    if (currentMediaItemIndex >= files.size * 2 - 1) {
                                        removeMediaItems(0, files.size)
                                    }
                                }
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED &&
                                _statuses.value[sound.id] != PlaybackStatus.UNAVAILABLE
                            ) {
                                // One-shot reached its end: reset so the next tap starts at zero.
                                cancelFade(sound.id)
                                cancelSegmentFade(sound.id)
                                fadeFactors.remove(sound.id)
                                pause()
                                if (sound.type == SoundType.FOLDER) {
                                    if (sound.playbackOrder == PlaybackOrder.SHUFFLE && !sound.loop) {
                                        folderAudioFiles[sound.id]?.let { files ->
                                            setMediaItems(createShufflePass(files, null))
                                            prepare()
                                        }
                                    } else {
                                        seekToDefaultPosition(0)
                                    }
                                } else {
                                    seekTo(0L)
                                }
                                pausedByFocusLoss.remove(sound.id)
                                setStatus(sound.id, PlaybackStatus.IDLE)
                                abandonFocusIfSilent()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            cancelFade(sound.id)
                            cancelSegmentFade(sound.id)
                            setStatus(sound.id, PlaybackStatus.UNAVAILABLE)
                            abandonFocusIfSilent()
                        }
                    },
                )
                prepare()
            }
    }

    private fun setStatus(
        id: String,
        status: PlaybackStatus,
    ) {
        _statuses.value += id to status
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
                        cancelSegmentFade(id)
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

                    val player = players[id]
                    val sound = playerSounds[id]

                    if (player != null && sound != null) {
                        player.play()
                        setStatus(id, PlaybackStatus.PLAYING)

                        if (sound.type == SoundType.FILE) {
                            startPlaybackFadeMonitor(sound, player)
                        }
                    }
                }
                pausedByFocusLoss.clear()
            }
        }
    }

    private fun createShufflePass(
        files: List<FolderAudioResolver.AudioFile>,
        previousLast: FolderAudioResolver.AudioFile?,
    ): List<MediaItem> =
        shuffledWithoutImmediateRepeat(files, previousLast)
            .map { MediaItem.fromUri(it.uri) }

    private fun lastPlaylistUri(player: ExoPlayer): Uri? {
        if (player.mediaItemCount == 0) return null

        return player
            .getMediaItemAt(player.mediaItemCount - 1)
            .localConfiguration
            ?.uri
    }

    private fun appendShufflePass(
        player: ExoPlayer,
        files: List<FolderAudioResolver.AudioFile>,
    ) {
        val previousLastUri = lastPlaylistUri(player)

        val previousLastFile =
            files.find {
                it.uri == previousLastUri
            }

        val nextPass = createShufflePass(files, previousLastFile)

        player.addMediaItems(nextPass)
    }

    private fun applyDuck(factor: Float) {
        duckFactor = factor
        players.keys.forEach { id ->
            updateVolume(id)
        }
    }

    private companion object {
        /** Conventional ducking level while another app speaks over us. */
        const val DUCK_FACTOR = 0.2f
    }
}
