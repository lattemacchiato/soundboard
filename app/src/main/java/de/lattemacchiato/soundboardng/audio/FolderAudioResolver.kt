package de.lattemacchiato.soundboardng.audio

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Resolves audio files directly inside a folder.
 *
 * Supports SAF tree URIs and local file URIs (restored backups).
 * Subfolders are intentionally ignored.
 */
class FolderAudioResolver(
    private val contentResolver: ContentResolver,
) {
    data class AudioFile(
        val uri: Uri,
        val name: String,
    )

    fun resolve(folderUri: Uri): List<AudioFile> {
        val files =
            when (folderUri.scheme) {
                ContentResolver.SCHEME_CONTENT -> resolveSaf(folderUri)
                ContentResolver.SCHEME_FILE -> resolveLocal(folderUri)
                else -> throw IllegalArgumentException(
                    "Unsupported folder URI scheme: ${folderUri.scheme}",
                )
            }

        return files.sortedWith(
            compareBy<AudioFile, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.uri.toString() },
        )
    }

    private fun resolveLocal(folderUri: Uri): List<AudioFile> {
        val directory = File(requireNotNull(folderUri.path) { "Missing folder path" })
        require(directory.isDirectory) { "Not a directory: $folderUri" }

        return directory
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.canRead() }
            ?.filter { file ->
                val extension = file.extension.lowercase()
                val mimeType =
                    MimeTypeMap
                        .getSingleton()
                        .getMimeTypeFromExtension(extension)

                mimeType?.startsWith("audio/", ignoreCase = true) == true ||
                    extension in setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "aiff", "aif", "amr")
            }?.map { AudioFile(uri = Uri.fromFile(it), name = it.name) }
            ?.toList()
            ?: emptyList()
    }

    private fun resolveSaf(treeUri: Uri): List<AudioFile> {
        val parentId =
            if (treeUri.pathSegments.size >= 4 &&
                treeUri.pathSegments[2] == "document"
            ) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

        val files = mutableListOf<AudioFile>()

        contentResolver
            .query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            ).let { cursor ->
                requireNotNull(cursor) { "Cannot read folder: $treeUri" }
                cursor.use {
                    val idColumn =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        )
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        )
                    val mimeColumn =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                        )

                    while (cursor.moveToNext()) {
                        val mimeType = cursor.getString(mimeColumn)
                        val name = cursor.getString(nameColumn) ?: continue
                        val extension = name.substringAfterLast('.', "").lowercase()

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                        val isAudio =
                            mimeType?.startsWith("audio/", ignoreCase = true) == true ||
                                extension in
                                setOf(
                                    "mp3",
                                    "wav",
                                    "ogg",
                                    "flac",
                                    "m4a",
                                    "aac",
                                    "opus",
                                    "aiff",
                                    "aif",
                                    "amr",
                                )

                        if (!isAudio) continue

                        val documentId = cursor.getString(idColumn) ?: continue

                        files +=
                            AudioFile(
                                uri =
                                    DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri,
                                        documentId,
                                    ),
                                name = name,
                            )
                    }
                }
            }

        return files
    }
}
