package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("UPDATE books SET isUnsorted = 0 WHERE id IN (:ids) AND isUnsorted = 1")
    suspend fun markOrganized(ids: List<String>)
    @Transaction
    @Query("SELECT * FROM books WHERE isUnsorted = 1 AND isArchived = 0 ORDER BY addedDate DESC")
    fun getUnsortedBooks(): Flow<List<BookWithDetails>>

    @Transaction
    @Query("""SELECT * FROM books WHERE isArchived = 0 AND isUnsorted = 0
        AND EXISTS (SELECT 1 FROM book_space_cross_ref b WHERE b.bookId = books.id)
        AND (:spaceId IS NULL OR :spaceId = '_all_' OR EXISTS (SELECT 1 FROM book_space_cross_ref b WHERE b.bookId = books.id AND b.spaceId = :spaceId))
        AND NOT EXISTS (SELECT 1 FROM shelf_book_cross_ref r INNER JOIN shelves s ON s.id = r.shelfId
            WHERE r.bookId = books.id AND (:spaceId IS NULL OR :spaceId = '_all_' OR s.spaceId = :spaceId))
        ORDER BY lastReadDate DESC, addedDate DESC""")
    fun getUnshelvedBooks(spaceId: String?): Flow<List<BookWithDetails>>

    @Query("SELECT * FROM books WHERE id IN (:ids)") suspend fun entities(ids: List<String>): List<BookEntity>
    @Query("DELETE FROM books WHERE id IN (:ids)") suspend fun deleteIds(ids: List<String>)
    @Query("UPDATE books SET isUnsorted = 0 WHERE id = :bookId")
    suspend fun markOrganized(bookId: String)

    @Query("UPDATE books SET isUnsorted = 0 WHERE isUnsorted = 1")
    suspend fun releaseUnsortedBooks()

    @Transaction
    @Query("SELECT * FROM books WHERE isArchived = 0 ORDER BY lastReadDate DESC, addedDate DESC")
    fun getAllBooks(): Flow<List<BookWithDetails>>

    @Transaction
    @Query(
        """
        SELECT * FROM books
        WHERE isArchived = 0 AND isUnsorted = 0
          AND (:spaceId IS NULL OR :spaceId = '_all_' OR EXISTS (
            SELECT 1 FROM book_space_cross_ref
            WHERE bookId = books.id AND spaceId = :spaceId
          ))
        ORDER BY lastReadDate DESC, addedDate DESC
        """,
    )
    fun getVisibleBooks(spaceId: String?): Flow<List<BookWithDetails>>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE isArchived = 0 AND isUnsorted = 1)")
    fun hasUnsortedBooks(): Flow<Boolean>

    @Transaction
    @Query("SELECT * FROM books WHERE isArchived = 1 ORDER BY lastReadDate DESC, addedDate DESC")
    fun getArchivedBooks(): Flow<List<BookWithDetails>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookWithDetails?

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    fun getByIdFlow(id: String): Flow<BookWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Update(entity = BookEntity::class)
    suspend fun updateMetadata(metadata: BookMetadataUpdate): Int

    @Query("UPDATE books SET isArchived = NOT isArchived WHERE id = :id")
    suspend fun toggleArchive(id: String)

    @Query("UPDATE books SET isRead = NOT isRead WHERE id = :id")
    suspend fun toggleReadStatus(id: String)

    @Query("UPDATE books SET isArchived = :value WHERE id IN (:ids) AND isArchived != :value")
    suspend fun setArchived(ids: List<String>, value: Boolean)

    @Query("UPDATE books SET isRead = :value WHERE id IN (:ids) AND isRead != :value")
    suspend fun setReadStatus(ids: List<String>, value: Boolean)

    @Query("SELECT COUNT(*) FROM books WHERE coverPath = :path")
    suspend fun coverReferenceCount(path: String): Int

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT COUNT(*) FROM books WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSync(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuthor(author: AuthorEntity): Long

    @Query("SELECT * FROM authors WHERE name = :name")
    suspend fun getAuthorByName(name: String): AuthorEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookAuthorCrossRef(crossRef: BookAuthorCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTagCrossRef(crossRef: BookTagCrossRef)

    @Query(
        """
        SELECT DISTINCT authors.* FROM authors
        JOIN book_author_cross_ref ON authors.id = book_author_cross_ref.authorId
        JOIN books ON book_author_cross_ref.bookId = books.id
        WHERE books.isArchived = 0
    """,
    )
    fun getAllAuthors(): Flow<List<AuthorEntity>>

    @Query(
        """
        SELECT DISTINCT tags.* FROM tags
        JOIN book_tag_cross_ref ON tags.id = book_tag_cross_ref.tagId
        JOIN books ON book_tag_cross_ref.bookId = books.id
        WHERE books.isArchived = 0
    """,
    )
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("DELETE FROM book_author_cross_ref WHERE bookId = :bookId")
    suspend fun deleteBookAuthorCrossRefs(bookId: String)

    @Query("DELETE FROM book_tag_cross_ref WHERE bookId = :bookId")
    suspend fun deleteBookTagCrossRefs(bookId: String)

    @Query("DELETE FROM authors WHERE id NOT IN (SELECT authorId FROM book_author_cross_ref)")
    suspend fun deleteOrphanAuthors()

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM book_tag_cross_ref)")
    suspend fun deleteOrphanTags()

    @Query("DELETE FROM authors WHERE name = :name")
    suspend fun deleteAuthorByName(name: String)

    @Query("DELETE FROM tags WHERE name = :name")
    suspend fun deleteTagByName(name: String)

    @Query("UPDATE authors SET name = :newName WHERE name = :oldName")
    suspend fun renameAuthor(oldName: String, newName: String)

    @Query("UPDATE tags SET name = :newName WHERE name = :oldName")
    suspend fun renameTag(oldName: String, newName: String)

    @Query("UPDATE OR IGNORE book_author_cross_ref SET authorId = :newId WHERE authorId = :oldId")
    suspend fun mergeBookAuthorCrossRef(oldId: Long, newId: Long)

    @Query("UPDATE OR IGNORE book_tag_cross_ref SET tagId = :newId WHERE tagId = :oldId")
    suspend fun mergeBookTagCrossRef(oldId: Long, newId: Long)

    @Query("SELECT * FROM authors")
    suspend fun getAllAuthorsSync(): List<AuthorEntity>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSync(): List<TagEntity>

    @Query("SELECT * FROM book_author_cross_ref")
    suspend fun getAllBookAuthorCrossRefsSync(): List<BookAuthorCrossRef>

    @Query("SELECT * FROM book_tag_cross_ref")
    suspend fun getAllBookTagCrossRefsSync(): List<BookTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAuthors(authors: List<AuthorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBookAuthorCrossRefs(crossRefs: List<BookAuthorCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBookTagCrossRefs(crossRefs: List<BookTagCrossRef>)

    @Query("DELETE FROM authors")
    suspend fun deleteAllAuthors()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM book_author_cross_ref")
    suspend fun deleteAllBookAuthorCrossRefs()

    @Query("DELETE FROM book_tag_cross_ref")
    suspend fun deleteAllBookTagCrossRefs()

}
