package de.lattemacchiato.soundboardng.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import de.lattemacchiato.soundboardng.SoundboardApp

/** Fetches the app-scoped [SoundboardApp] inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.app(): SoundboardApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoundboardApp
