package com.javierreansyah.pinecone.data.repository.library

import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookDao
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookmarkDao
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteDao
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import com.javierreansyah.pinecone.data.model.ReadingSessionToken
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton

/** Atomic reading state and annotations for every publication rendition. */
@Singleton
class ReadingRepository @Inject constructor(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao,
) {
    private val publicationDao get() = database.publicationDao()

    suspend fun currentSession(bookId: String): ReadingSessionToken? =
        database.withTransaction {
            val runtime = database.operationDao().runtime()
            if (runtime.maintenance) return@withTransaction null
            val book = bookDao.getEntity(bookId) ?: return@withTransaction null
            val rendition = publicationDao.rendition(bookId, book.activeRendition)
                ?: return@withTransaction null
            ReadingSessionToken(
                runtime.generation,
                bookId,
                rendition.key,
                rendition.sha256
            )
        }

    private suspend fun permits(session: ReadingSessionToken?): Boolean =
        if (session == null) !database.operationDao().runtime().maintenance
        else permittedBook(session.bookId, session) != null

    /** Call only inside the mutation's Room transaction, so validation and writes stay atomic. */
    private suspend fun permittedBook(bookId: String, session: ReadingSessionToken?): BookEntity? {
        val runtime = database.operationDao().runtime()
        if (runtime.maintenance) return null
        val book = bookDao.getEntity(bookId) ?: return null
        if (session != null && (session.bookId != bookId || runtime.generation != session.generation ||
                    book.activeRendition != session.rendition ||
                    publicationDao.rendition(bookId, session.rendition)?.sha256 != session.sha256)
        ) return null
        return book
    }

    /** Both records are updated by the caller's transaction; absent renditions remain absent. */
    private suspend fun updateBookAndRendition(
        book: BookEntity,
        transform: (PublicationRenditionEntity) -> PublicationRenditionEntity,
    ) {
        bookDao.update(book)
        publicationDao.rendition(book.id, book.activeRendition)?.let {
            publicationDao.putRenditions(listOf(transform(it)))
        }
    }

    /** Maps the latest durable source location, then activates the destination in the caller's transaction. */
    suspend fun activateMappedRendition(
        bookId: String,
        key: String,
        session: ReadingSessionToken? = null
    ): Unit = database.withTransaction {
        val book = permittedBook(bookId, session) ?: return@withTransaction
        val source =
            publicationDao.rendition(bookId, book.activeRendition) ?: return@withTransaction
        val target = publicationDao.rendition(bookId, key) ?: return@withTransaction
        if (source.key != key && source.lastLocatorJson != null) {
            val readMode = publicationDao.rendition(bookId, "read-mode")
            val pages = readMode?.sourceMapJson?.let {
                kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                    it
                )
            }.orEmpty()
            val mapped = RenditionLocations.map(
                source.lastLocatorJson,
                target.lastLocatorJson,
                source.key,
                key,
                pages,
                source.progression
            )
            publicationDao.putRenditions(
                listOf(
                    target.copy(
                        lastLocatorJson = mapped,
                        progression = source.progression
                    )
                )
            )
        }
        activateRendition(bookId, key)
    }

    suspend fun rendition(bookId: String, key: String): PublicationRenditionEntity? =
        publicationDao.rendition(bookId, key)

    fun renditionFlow(bookId: String, key: String): Flow<PublicationRenditionEntity?> =
        publicationDao.renditionFlow(bookId, key)

    suspend fun setReadingOverrides(
        bookId: String,
        key: String,
        json: String,
        session: ReadingSessionToken? = null
    ): Unit? =
        database.withTransaction {
            if (!permits(session)) return@withTransaction
            publicationDao.rendition(bookId, key)
                ?.let { publicationDao.putRenditions(listOf(it.copy(preferencesJson = json))) }
        }

    suspend fun activateRendition(bookId: String, key: String): Unit = database.withTransaction {
        val item = bookDao.getEntity(bookId) ?: return@withTransaction
        val target = publicationDao.rendition(bookId, key) ?: return@withTransaction
        bookDao.update(
            item.copy(
                activeRendition = key,
                progression = target.progression,
                lastLocatorJson = target.lastLocatorJson,
                furthestProgression = target.furthestProgression,
                furthestLocatorJson = target.furthestLocatorJson,
                jumpOriginLocatorJson = target.jumpOriginLocatorJson,
            )
        )
    }

    suspend fun saveReadingPosition(
        bookId: String,
        locator: Locator,
        renditionKey: String = "original",
        session: ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        val runtime = database.operationDao().runtime()
        if (runtime.maintenance) return@withTransaction
        val rendition = publicationDao.rendition(bookId, renditionKey) ?: return@withTransaction
        val detailsBook = bookDao.getEntity(bookId) ?: return@withTransaction
        if (session != null && (session.bookId != bookId || session.rendition != renditionKey || runtime.generation != session.generation ||
                    detailsBook.activeRendition != session.rendition || rendition.sha256 != session.sha256)
        ) return@withTransaction
        val encoded = locator.toJSON().toString()
        val progression = locator.locations.totalProgression ?: 0.0

        val hasJumpOrigin = !rendition.jumpOriginLocatorJson.isNullOrBlank()
        val isNewProgressionFurther = progression > rendition.furthestProgression

        val newFurthestProgression =
            if (!hasJumpOrigin && isNewProgressionFurther) progression else rendition.furthestProgression
        val newFurthestLocatorJson =
            if (!hasJumpOrigin && isNewProgressionFurther) encoded else rendition.furthestLocatorJson

        publicationDao.putRenditions(
            listOf(
                rendition.copy(
                    lastLocatorJson = encoded,
                    progression = progression,
                    furthestProgression = newFurthestProgression,
                    furthestLocatorJson = newFurthestLocatorJson
                )
            )
        )
        if (detailsBook.activeRendition != renditionKey) return@withTransaction

        bookDao.update(
            detailsBook.copy(
                progression = progression,
                lastLocatorJson = encoded,
                lastReadDate = System.currentTimeMillis(),
                furthestProgression = newFurthestProgression,
                furthestLocatorJson = newFurthestLocatorJson,
            ),
        )
    }

    suspend fun saveJumpOrigin(
        bookId: String,
        locator: Locator,
        session: ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        val book = permittedBook(bookId, session) ?: return@withTransaction
        if (book.jumpOriginLocatorJson.isNullOrBlank()) {
            val encoded = locator.toJSON().toString()
            updateBookAndRendition(book.copy(jumpOriginLocatorJson = encoded)) {
                it.copy(jumpOriginLocatorJson = encoded)
            }
        }
    }

    suspend fun saveJumpReadingPosition(
        bookId: String,
        locator: Locator,
        session: ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        val book = permittedBook(bookId, session) ?: return@withTransaction
        val progression = locator.locations.totalProgression ?: 0.0
        val encoded = locator.toJSON().toString()
        val origin =
            book.jumpOriginLocatorJson.takeUnless { it.isNullOrBlank() } ?: book.lastLocatorJson
        updateBookAndRendition(
            book.copy(
                progression = progression,
                lastLocatorJson = encoded,
                lastReadDate = System.currentTimeMillis(),
                jumpOriginLocatorJson = origin,
            ),
        ) {
            it.copy(
                lastLocatorJson = encoded,
                progression = progression,
                jumpOriginLocatorJson = origin
            )
        }
    }

    suspend fun clearJumpOrigin(bookId: String, session: ReadingSessionToken? = null): Unit =
        database.withTransaction {
            val book = permittedBook(bookId, session) ?: return@withTransaction
            if (book.jumpOriginLocatorJson != null) {
                updateBookAndRendition(book.copy(jumpOriginLocatorJson = null)) {
                    it.copy(jumpOriginLocatorJson = null)
                }
            }
        }

    suspend fun resetFurthestToCurrent(bookId: String, session: ReadingSessionToken? = null): Unit =
        database.withTransaction {
            val book = permittedBook(bookId, session) ?: return@withTransaction
            updateBookAndRendition(
                book.copy(
                    furthestProgression = book.progression,
                    furthestLocatorJson = book.lastLocatorJson
                ),
            ) {
                it.copy(
                    furthestProgression = it.progression,
                    furthestLocatorJson = it.lastLocatorJson
                )
            }
        }

    suspend fun getLastLocator(bookId: String): Locator? {
        val json = bookDao.getEntity(bookId)?.lastLocatorJson ?: return null
        return try {
            Locator.fromJSON(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addBookmark(
        bookId: String,
        locator: Locator,
        chapterTitle: String? = null,
        renditionKey: String = "original",
        session: ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = chapterTitle ?: locator.title,
                renditionKey = renditionKey,
            ),
        )
    }

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getByBookId(bookId)

    suspend fun removeBookmark(
        id: Long,
        session: ReadingSessionToken? = null
    ): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        bookmarkDao.deleteById(id)
    }

    suspend fun addNote(
        bookId: String,
        locator: Locator,
        noteText: String,
        color: Int = -1,
        chapterTitle: String? = null,
        renditionKey: String = "original",
        session: ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        noteDao.insert(
            NoteEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                chapterTitle = chapterTitle ?: locator.title,
                noteText = noteText,
                color = color,
                renditionKey = renditionKey,
            ),
        )
    }

    suspend fun updateNote(
        note: NoteEntity,
        session: ReadingSessionToken? = null
    ): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        noteDao.update(note)
    }

    fun getNotes(bookId: String): Flow<List<NoteEntity>> = noteDao.getByBookId(bookId)

    suspend fun removeNote(
        id: Long,
        session: ReadingSessionToken? = null
    ): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        noteDao.deleteById(id)
    }

}
