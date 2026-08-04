package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, ShelfEntity::class, ShelfBookCrossRefEntity::class, NoteEntity::class, AuthorEntity::class, BookAuthorCrossRef::class, TagEntity::class, BookTagCrossRef::class, SpaceEntity::class, BookSpaceCrossRef::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun shelfDao(): ShelfDao
    abstract fun noteDao(): NoteDao
    abstract fun spaceDao(): SpaceDao

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_author_cross_ref ADD COLUMN authorOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN furthestProgression REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE books ADD COLUMN furthestLocatorJson TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN jumpOriginLocatorJson TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE books ADD COLUMN collectionId TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `spaces` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `book_space_cross_ref` (`bookId` TEXT NOT NULL, `spaceId` TEXT NOT NULL, PRIMARY KEY(`bookId`, `spaceId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`spaceId`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_space_cross_ref_spaceId` ON `book_space_cross_ref` (`spaceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_space_cross_ref_bookId` ON `book_space_cross_ref` (`bookId`)")
                
                // Migrate existing collections to spaces
                db.execSQL("INSERT INTO spaces (id, name, createdAt) SELECT id, name, createdAt FROM collections")
                
                // Migrate existing book collections to cross refs
                db.execSQL("INSERT INTO book_space_cross_ref (bookId, spaceId) SELECT id, collectionId FROM books WHERE collectionId IS NOT NULL")
                
                // Drop collections table
                db.execSQL("DROP TABLE collections")
            }
        }
    }
}
