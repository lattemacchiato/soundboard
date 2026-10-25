package de.lattemacchiato.soundboardng.data

import kotlinx.serialization.Serializable

/** Persisted appearance preference; SYSTEM falls back to light on Android 8.1. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }
