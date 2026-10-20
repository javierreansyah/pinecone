package com.javierreansyah.pinecone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

/**
 * Runs in the provider APK, without the target APK's Kotlin runtime on its classpath.
 */
public class TestBackupGrantActivity extends Activity {
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String target = getPackageName().substring(0, getPackageName().length() - ".test".length());
        grantUriPermission(target, DocumentsContract.buildTreeDocumentUri(TestBackupDocumentsProvider.AUTHORITY, "root"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
