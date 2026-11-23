package de.lattemacchiato.soundboardng.data

/**
 * Conservative policy for rolling back newly created external audio.
 *
 * Existing references to Android external storage prevent rollback,
 * because their document hierarchy cannot always be established reliably.
 */
internal object ImportRollbackPolicy {
    private const val EXTERNAL_STORAGE_AUTHORITY =
        "com.android.externalstorage.documents"

    /**
     * Returns true only when all stored sound URIs were examined
     * successfully and none references Android external storage.
     */
    fun canRollback(soundUris: Collection<String>): Boolean =
        runCatching {
            soundUris.none { uri ->
                val parsed = java.net.URI(uri)

                parsed.scheme.equals("content", ignoreCase = true) &&
                    parsed.authority.equals(
                        EXTERNAL_STORAGE_AUTHORITY,
                        ignoreCase = true,
                    )
            }
        }.getOrDefault(false)
}
