package dev.co508.soundboard.data

import kotlinx.serialization.Serializable

/** Persisted appearance preference; SYSTEM falls back to light on Android 8.1. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }
