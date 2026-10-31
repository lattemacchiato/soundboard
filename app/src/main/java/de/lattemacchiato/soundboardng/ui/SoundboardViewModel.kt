package de.lattemacchiato.soundboardng.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.lattemacchiato.soundboardng.audio.PlaybackStatus
import de.lattemacchiato.soundboardng.audio.SoundboardEngine
import de.lattemacchiato.soundboardng.data.LockSettings
import de.lattemacchiato.soundboardng.data.Sound
import de.lattemacchiato.soundboardng.data.SoundRepository
import de.lattemacchiato.soundboardng.data.TapBehavior
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row on the board: the persisted sound plus its live playback status. */
data class SoundRowState(
    val sound: Sound,
    val status: PlaybackStatus,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val isUnavailable: Boolean get() = status == PlaybackStatus.UNAVAILABLE
}

class SoundboardViewModel(
    private val repository: SoundRepository,
    private val engine: SoundboardEngine,
    private val lockSettings: LockSettings,
) : ViewModel() {
    val locked: StateFlow<Boolean> = lockSettings.locked

    fun setLocked(value: Boolean) = lockSettings.setLocked(value)

    val rows: StateFlow<List<SoundRowState>> =
        combine(repository.sounds, engine.statuses) { sounds, statuses ->
            sounds.map { SoundRowState(it, statuses[it.id] ?: PlaybackStatus.IDLE) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** True while anything is audible; the screen uses it to offer "Stop all". */
    val anyPlaying: StateFlow<Boolean> =
        engine.statuses
            .map { statuses -> statuses.any { it.value == PlaybackStatus.PLAYING } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun toggle(sound: Sound) {
        if (sound.tapBehavior == TapBehavior.STOP_RESTART &&
            engine.statuses.value[sound.id] == PlaybackStatus.PLAYING &&
            !engine.isFadingOut(sound.id)
        ) {
            engine.stop(sound) // Fade out, then reset to 0:00.
        } else if (sound.tapBehavior == TapBehavior.STOP_RESTART && engine.isFadingOut(sound.id)) {
            engine.restart(sound)
        } else {
            engine.toggle(sound)
        }
    }

    fun setTapBehavior(
        id: String,
        behavior: TapBehavior,
    ) {
        if (locked.value) return
        viewModelScope.launch { if (!locked.value) repository.setTapBehavior(id, behavior) }
    }

    fun setFade(
        id: String,
        fadeIn: Boolean,
        durationMs: Int,
    ) {
        if (locked.value) return
        viewModelScope.launch { if (!locked.value) repository.setFade(id, fadeIn, durationMs) }
    }

    fun restart(sound: Sound) = engine.restart(sound)

    fun setLoop(
        id: String,
        loop: Boolean,
    ) {
        if (locked.value) return
        engine.setLoop(id, loop)
        viewModelScope.launch { if (!locked.value) repository.setLoop(id, loop) }
    }

    /** Stop every playing or paused sound immediately, resetting playback state. */
    fun stopAll() {
        engine.statuses.value.keys
            .toList()
            .forEach(engine::release)
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty() || locked.value) return
        viewModelScope.launch { if (!locked.value) repository.add(uris) }
    }

    /**
     * Applies a volume change to the running mix without persisting it.
     *
     * Called continuously while the dial is being dragged, so it must stay off
     * disk — [commitVolume] does the write once the gesture ends.
     */
    fun previewVolume(
        id: String,
        percent: Int,
    ) {
        if (locked.value) return
        engine.setGain(id, percent.coerceIn(Sound.MIN_VOLUME_PERCENT, Sound.MAX_VOLUME_PERCENT) / 100f)
    }

    fun commitVolume(
        id: String,
        percent: Int,
    ) {
        if (locked.value) return
        previewVolume(id, percent)
        viewModelScope.launch { if (!locked.value) repository.setVolume(id, percent) }
    }

    /** Removes a sound from the board. The underlying file is never deleted. */
    fun remove(id: String) {
        if (locked.value) return
        viewModelScope.launch {
            if (!locked.value) {
                repository.remove(id)
                engine.release(id)
            }
        }
    }

    fun rename(
        id: String,
        name: String,
    ) {
        if (locked.value) return
        viewModelScope.launch { if (!locked.value) repository.rename(id, name) }
    }

    /**
     * Persists a new sound order.
     *
     * Called once a drag ends (with the whole board's ids in their new order) and
     * by rearrange mode's sort shortcuts — both are one-shot writes, not a
     * continuously maintained sort, so a later manual drag is free to move things
     * again.
     */
    fun commitOrder(order: List<String>) {
        if (locked.value) return
        viewModelScope.launch { if (!locked.value) repository.reorder(order) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory =
            viewModelFactory {
                initializer {
                    val app = app()
                    SoundboardViewModel(app.repository, app.engine, app.lockSettings)
                }
            }
    }
}
