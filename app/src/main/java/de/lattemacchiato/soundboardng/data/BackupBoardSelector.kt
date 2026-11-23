package de.lattemacchiato.soundboardng.data

/**
 * Selects boards for ZIP and JSON backups without modifying the original library.
 */
internal object BackupBoardSelector {
    fun select(
        library: SoundLibrary,
        selectedBoardIds: Set<String>?,
    ): SoundLibrary {
        if (selectedBoardIds == null) return library

        require(selectedBoardIds.isNotEmpty()) {
            "At least one board must be selected"
        }

        val selectedBoards =
            library.boards.filter { it.id in selectedBoardIds }

        require(selectedBoards.size == selectedBoardIds.size) {
            "Selected board not found"
        }

        val activeId =
            library.activeBoardId.takeIf { active ->
                selectedBoards.any { it.id == active }
            } ?: selectedBoards.first().id

        return library.copy(
            boards = selectedBoards,
            activeBoardId = activeId,
        )
    }
}
