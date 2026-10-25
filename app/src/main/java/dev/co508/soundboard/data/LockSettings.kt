package dev.co508.soundboard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide editing lock, persisted synchronously for reliable restarts. */
class LockSettings(context: Context) {
    private val preferences = context.getSharedPreferences("soundboard_settings", Context.MODE_PRIVATE)
    private val mutableLocked = MutableStateFlow(preferences.getBoolean("locked", false))
    val locked: StateFlow<Boolean> = mutableLocked

    fun setLocked(value: Boolean) {
        if (preferences.edit().putBoolean("locked", value).commit()) {
            mutableLocked.value = value
        }
    }
}
