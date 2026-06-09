package com.example.readerapp.data.local.database.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, ShelfEntity::class, ShelfBookCrossRefEntity::class, NoteEntity::class, AuthorEntity::class, BookAuthorCrossRef::class, TagEntity::class, BookTagCrossRef::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun shelfDao(): ShelfDao
    abstract fun noteDao(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add isArchived column to books
                db.execSQL("ALTER TABLE books ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")

                // Create notes table
                db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `chapterTitle` TEXT, `noteText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")

                // Create shelves table
                db.execSQL("CREATE TABLE IF NOT EXISTS `shelves` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")

                // Create cross ref table
                db.execSQL("CREATE TABLE IF NOT EXISTS `shelf_book_cross_ref` (`shelfId` TEXT NOT NULL, `bookId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`shelfId`, `bookId`), FOREIGN KEY(`shelfId`) REFERENCES `shelves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shelf_book_cross_ref_shelfId` ON `shelf_book_cross_ref` (`shelfId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shelf_book_cross_ref_bookId` ON `shelf_book_cross_ref` (`bookId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN published TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN tags TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shelf_book_cross_ref ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
