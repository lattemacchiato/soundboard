package de.lattemacchiato.soundboardng.audio

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Resolves audio files directly inside a SAF-selected folder.
 *
 * Subfolders are intentionally ignored. The returned URIs remain accessible
 * through the persisted read permission of the parent tree URI.
 */
class FolderAudioResolver(
    private val contentResolver: ContentResolver,
) {
    data class AudioFile(
        val uri: Uri,
        val name: String,
    )

    fun resolve(treeUri: Uri): List<AudioFile> {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

        val files = mutableListOf<AudioFile>()

        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeColumn) ?: continue
                if (!mimeType.startsWith("audio/", ignoreCase = true)) continue

                val documentId = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue

                files += AudioFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                    name = name,
                )
            }
        }

        return files.sortedWith(
            compareBy<AudioFile, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.uri.toString() },
        )
    }
}
