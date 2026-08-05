package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class, BookmarkEntity::class, ShelfEntity::class,
        ShelfBookCrossRefEntity::class, NoteEntity::class, AuthorEntity::class,
        BookAuthorCrossRef::class, TagEntity::class, BookTagCrossRef::class,
        SpaceEntity::class, BookSpaceCrossRef::class, BackupStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun shelfDao(): ShelfDao
    abstract fun noteDao(): NoteDao
    abstract fun spaceDao(): SpaceDao
    abstract fun backupStateDao(): BackupStateDao

    companion object {
        val BACKUP_REVISION_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = installRevisionTracking(db)
            override fun onOpen(db: SupportSQLiteDatabase) = installRevisionTracking(db)
        }

        private fun installRevisionTracking(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO backup_state " +
                    "(id, revision, pendingSettingsJson) VALUES (1, 0, NULL)"
            )
            val tables = listOf(
                "books", "bookmarks", "shelves", "shelf_book_cross_ref", "notes",
                "authors", "book_author_cross_ref", "tags", "book_tag_cross_ref",
                "spaces", "book_space_cross_ref"
            )
            for (table in tables) for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                val trigger = "backup_revision_${table}_${operation.lowercase()}"
                db.execSQL("CREATE TRIGGER IF NOT EXISTS `$trigger` AFTER $operation ON `$table` " +
                    "BEGIN UPDATE backup_state SET revision = revision + 1 WHERE id = 1; END")
            }
        }
    }
}
