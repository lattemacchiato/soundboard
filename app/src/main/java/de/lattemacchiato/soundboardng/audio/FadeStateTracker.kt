package de.lattemacchiato.soundboardng.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FadeStateTracker {
    enum class State {
        FADING_IN,
        FADING_OUT,
    }

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    fun start(
        id: String,
        state: State,
    ) {
        _states.value += id to state
    }

    fun clear(id: String) {
        _states.value -= id
    }

    fun clearAll() {
        _states.value = emptyMap()
    }
}
