package com.example.readerapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
}
