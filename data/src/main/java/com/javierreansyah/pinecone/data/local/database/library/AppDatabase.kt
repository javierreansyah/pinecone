package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        BookEntity::class, BookmarkEntity::class, ShelfEntity::class,
        ShelfBookCrossRefEntity::class, NoteEntity::class, AuthorEntity::class,
        BookAuthorCrossRef::class, TagEntity::class, BookTagCrossRef::class,
        SpaceEntity::class, BookSpaceCrossRef::class, BackupStateEntity::class,
        PublicationRenditionEntity::class, PublicationCreditEntity::class,
        OperationEntity::class, OperationInputEntity::class, LibraryRuntimeEntity::class, PublicationDeletionEntity::class,
        OperationUriGrantEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun shelfDao(): ShelfDao
    abstract fun noteDao(): NoteDao
    abstract fun spaceDao(): SpaceDao
    abstract fun backupStateDao(): BackupStateDao
    abstract fun publicationDao(): PublicationDao
    abstract fun operationDao(): OperationDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS publication_operations (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, state TEXT NOT NULL, generation INTEGER NOT NULL, sequence INTEGER NOT NULL, createdAt INTEGER NOT NULL, bookId TEXT, sourceHash TEXT, converterVersion INTEGER, forceReparse INTEGER NOT NULL, attempt TEXT, completed INTEGER NOT NULL, total INTEGER NOT NULL, error TEXT, acknowledged INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_publication_operations_bookId ON publication_operations (bookId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS publication_operation_inputs (operationId TEXT NOT NULL, ordinal INTEGER NOT NULL, uri TEXT NOT NULL, displayName TEXT NOT NULL, folder INTEGER NOT NULL, stagedPath TEXT, grantUri TEXT, folderPathJson TEXT, state TEXT NOT NULL, bookId TEXT, error TEXT, PRIMARY KEY(operationId, ordinal), FOREIGN KEY(operationId) REFERENCES publication_operations(id) ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS library_runtime (id INTEGER NOT NULL PRIMARY KEY, generation INTEGER NOT NULL, sequence INTEGER NOT NULL, maintenance INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS publication_deletions (bookId TEXT NOT NULL PRIMARY KEY, sequence INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS operation_uri_grants (uri TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT OR IGNORE INTO library_runtime (id, generation, sequence, maintenance) VALUES (1, 0, 0, 0)")
            }
        }
        val BACKUP_REVISION_CALLBACK: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = installRevisionTracking(db)
            override fun onOpen(db: SupportSQLiteDatabase) {
                installRevisionTracking(db)
                // Remove empty shelves left by earlier app versions, not shelves hidden by filters.
                db.execSQL("DELETE FROM shelves WHERE id NOT IN (SELECT DISTINCT shelfId FROM shelf_book_cross_ref) AND id != 'unshelved'")
            }

            private fun installRevisionTracking(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO library_runtime (id, generation, sequence, maintenance) VALUES (1, 0, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO spaces (id, name, createdAt) VALUES ('_all_', 'All', 0)")
                db.execSQL("INSERT OR IGNORE INTO book_space_cross_ref (bookId, spaceId) SELECT id, '_all_' FROM books")
                db.execSQL(
                    "INSERT OR IGNORE INTO backup_state " +
                            "(id, revision, pendingSettingsJson) VALUES (1, 0, NULL)",
                )
                val tables = listOf(
                    "books", "bookmarks", "shelves", "shelf_book_cross_ref", "notes",
                    "authors", "book_author_cross_ref", "tags", "book_tag_cross_ref",
                    "spaces", "book_space_cross_ref",
                    "publication_renditions", "publication_credits",
                )
                for (table in tables) for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                    val trigger = "backup_revision_${table}_${operation.lowercase()}"
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS `$trigger` AFTER $operation ON `$table` " +
                                "BEGIN UPDATE backup_state SET revision = revision + 1 WHERE id = 1; END",
                    )
                }
            }
        }
    }
}
