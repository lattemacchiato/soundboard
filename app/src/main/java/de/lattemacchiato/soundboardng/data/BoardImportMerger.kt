package de.lattemacchiato.soundboardng.data

import java.util.UUID

/**
 * Pure configuration merge logic for selective ZIP board imports.
 *
 * Audio URIs must already be resolved before merging.
 */
internal object BoardImportMerger {
    enum class Mode {
        ADD,
        REPLACE,
    }

    fun merge(
        existing: SoundLibrary,
        imported: SoundLibrary,
        selectedBoardIds: Set<String>,
        mode: Mode,
    ): SoundLibrary {
        require(selectedBoardIds.isNotEmpty()) {
            "At least one board must be selected"
        }

        val selectedBoards = imported.boards.filter { it.id in selectedBoardIds }

        require(selectedBoards.size == selectedBoardIds.size) {
            "Selected board not found in backup"
        }

        return when (mode) {
            Mode.ADD -> addBoards(existing, selectedBoards)
            Mode.REPLACE -> replaceBoards(imported, selectedBoards)
        }
    }

    private fun addBoards(
        existing: SoundLibrary,
        selectedBoards: List<SoundBoard>,
    ): SoundLibrary {
        val usedNames = existing.boards.map { it.name }.toMutableSet()
        val usedIds = existing.boards.map { it.id }.toMutableSet()
        val usedSoundIds =
            existing.boards
                .flatMap { it.sounds }
                .map { it.id }
                .toMutableSet()

        val newBoards =
            selectedBoards.map { board ->
                val name = uniqueName(board.name, usedNames)
                usedNames += name

                var newId: String
                do {
                    newId = UUID.randomUUID().toString()
                } while (newId in usedIds)
                usedIds += newId

                val sounds =
                    board.sounds.map { sound ->
                        var soundId = sound.id

                        if (soundId in usedSoundIds) {
                            do {
                                soundId = UUID.randomUUID().toString()
                            } while (soundId in usedSoundIds)
                        }

                        usedSoundIds += soundId
                        sound.copy(id = soundId)
                    }

                board.copy(
                    id = newId,
                    name = name,
                    sounds = sounds,
                )
            }

        return existing.copy(boards = existing.boards + newBoards)
    }

    private fun replaceBoards(
        imported: SoundLibrary,
        selectedBoards: List<SoundBoard>,
    ): SoundLibrary {
        val activeId =
            imported.activeBoardId.takeIf { active ->
                selectedBoards.any { it.id == active }
            } ?: selectedBoards.first().id

        return imported.copy(
            boards = selectedBoards,
            activeBoardId = activeId,
        )
    }

    private fun uniqueName(
        name: String,
        usedNames: Set<String>,
    ): String {
        if (name !in usedNames) return name

        var suffix = 2
        while ("$name ($suffix)" in usedNames) {
            suffix++
        }
        return "$name ($suffix)"
    }
}
