package de.lattemacchiato.soundboardng.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRollbackPolicyTest {
    @Test
    fun emptyLibraryAllowsRollback() {
        assertTrue(ImportRollbackPolicy.canRollback(emptyList()))
    }

    @Test
    fun privateFileReferencesAllowRollback() {
        assertTrue(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "file:///data/user/0/app/files/audio.mp3",
                    "file:///data/user/0/app/files/folder",
                ),
            ),
        )
    }

    @Test
    fun externalStorageFileReferencePreventsRollback() {
        assertFalse(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "content://com.android.externalstorage.documents/document/primary%3AMusic%2Faudio.mp3",
                ),
            ),
        )
    }

    @Test
    fun externalStorageFolderReferencePreventsRollback() {
        assertFalse(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                ),
            ),
        )
    }

    @Test
    fun externalStorageReferenceAmongOtherUrisPreventsRollback() {
        assertFalse(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "file:///data/user/0/app/files/audio.mp3",
                    "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fother.mp3",
                ),
            ),
        )
    }

    @Test
    fun otherContentProviderDoesNotBlockRollback() {
        assertTrue(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "content://com.example.otherprovider/document/123",
                ),
            ),
        )
    }

    @Test
    fun malformedUriPreventsRollback() {
        assertFalse(
            ImportRollbackPolicy.canRollback(
                listOf("content://[invalid"),
            ),
        )
    }

    @Test
    fun externalStorageAuthorityComparisonIsCaseInsensitive() {
        assertFalse(
            ImportRollbackPolicy.canRollback(
                listOf(
                    "content://COM.ANDROID.EXTERNALSTORAGE.DOCUMENTS/document/123",
                ),
            ),
        )
    }
}
