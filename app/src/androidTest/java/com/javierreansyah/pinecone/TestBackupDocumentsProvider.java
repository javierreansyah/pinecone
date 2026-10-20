package com.javierreansyah.pinecone;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Test-APK-only SAF provider. Uses Java because this process cannot load target dependencies.
 */
public class TestBackupDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.javierreansyah.pinecone.test.backupdocs";
    private static final String[] COLUMNS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};

    private File root() {
        final File directory = new File(Objects.requireNonNull(getContext()).getFilesDir(), "backup-test-vault");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create test backup directory: " + directory);
        }
        try {
            return directory.getCanonicalFile();
        } catch (final IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private File file(final String id) {
        if ("root".equals(id)) return root();
        if (!id.startsWith("root/")) throw new IllegalArgumentException("Unknown document");
        try {
            final File result = new File(root(), id.substring(5)).getCanonicalFile();
            if (!result.getPath().startsWith(root().getPath() + File.separator))
                throw new SecurityException("Unsafe document path");
            return result;
        } catch (final IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private String id(final File file) {
        return file.equals(root()) ? "root" : "root/" + file.getPath().substring(root().getPath().length() + 1).replace(File.separatorChar, '/');
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(final String[] projection) {
        final MatrixCursor cursor = new MatrixCursor(null != projection ? projection : new String[]{DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS});
        final Map<String, Object> values = new HashMap<>();
        values.put(DocumentsContract.Root.COLUMN_ROOT_ID, "test");
        values.put(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root");
        values.put(DocumentsContract.Root.COLUMN_TITLE, "Pinecone test vault");
        values.put(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE);
        add(cursor, values);
        return cursor;
    }

    private void add(final MatrixCursor cursor, final Map<String, Object> values) {
        final MatrixCursor.RowBuilder row = cursor.newRow();
        for (final String column : cursor.getColumnNames()) row.add(values.get(column));
    }

    private void addDocument(final MatrixCursor cursor, final File file) {
        final Map<String, Object> values = new HashMap<>();
        values.put(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id(file));
        values.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName());
        values.put(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream");
        values.put(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_SUPPORTS_RENAME | (file.isDirectory() ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : 0));
        values.put(DocumentsContract.Document.COLUMN_SIZE, file.length());
        values.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        add(cursor, values);
    }

    @Override
    public Cursor queryDocument(final String documentId, final String[] projection) {
        final MatrixCursor cursor = new MatrixCursor(null != projection ? projection : TestBackupDocumentsProvider.COLUMNS);
        addDocument(cursor, file(documentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(final String parentId, final String[] projection, final String sortOrder) {
        final MatrixCursor cursor = new MatrixCursor(null != projection ? projection : TestBackupDocumentsProvider.COLUMNS);
        final File[] children = file(parentId).listFiles();
        if (null != children) for (final File child : children) addDocument(cursor, child);
        return cursor;
    }

    @Override
    @NonNull
    public ParcelFileDescriptor openDocument(@NonNull final String documentId, @NonNull final String mode, @Nullable final CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    private void safeName(final String name) {
        if (".".equals(name) || "..".equals(name) || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException("Unsafe document name");
    }

    @Override
    public String createDocument(final String parentId, final String mimeType, final String name) throws FileNotFoundException {
        safeName(name);
        final File target = new File(file(parentId), name);
        try {
            final boolean created = mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR) ? target.mkdirs() : target.createNewFile();
            if (!created) throw new IOException("Cannot create " + target);
            return id(target);
        } catch (final IOException error) {
            throw new FileNotFoundException(error.toString());
        }
    }

    private void delete(final File target) throws FileNotFoundException {
        final File[] children = target.listFiles();
        if (null != children) for (final File child : children) delete(child);
        if (!target.delete()) throw new FileNotFoundException("Cannot delete " + target);
    }

    @Override
    public void deleteDocument(final String documentId) throws FileNotFoundException {
        final File target = file(documentId);
        if (target.equals(root())) throw new SecurityException("Cannot delete provider root");
        delete(target);
    }

    @Override
    public String renameDocument(final String documentId, final String name) throws FileNotFoundException {
        safeName(name);
        final File old = file(documentId);
        final File target = new File(old.getParentFile(), name);
        if (!old.renameTo(target)) throw new FileNotFoundException("Cannot rename " + old);
        return id(target);
    }

    @Override
    public boolean isChildDocument(final String parentId, final String documentId) {
        return file(documentId).getPath().startsWith(file(parentId).getPath() + File.separator);
    }
}
