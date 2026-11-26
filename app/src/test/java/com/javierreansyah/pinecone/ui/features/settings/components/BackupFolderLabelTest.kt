package com.javierreansyah.pinecone.ui.features.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupFolderLabelTest {
    @Test
    fun formatsPrimaryAndExternalSafTreeUris() {
        assertEquals(
            "Documents/Pinecone/Demo",
            formatBackupFolderLabel(
                "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FPinecone%2FDemo",
            ),
        )
        assertEquals(
            "Documents/Pinecone",
            formatBackupFolderLabel(
                "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FPinecone",
            ),
        )
        assertEquals(
            "1234-5678/Backups/Pinecone",
            formatBackupFolderLabel(
                "content://com.android.externalstorage.documents/tree/1234-5678%3ABackups%2FPinecone",
            ),
        )
        assertNull(formatBackupFolderLabel(""))
        assertNull(formatBackupFolderLabel("content://com.android.externalstorage.documents/tree/primary%3A"))
        assertNull(formatBackupFolderLabel("content://other.provider/not-a-tree"))
    }
}
