package com.javierreansyah.pinecone.data.repository.library

import androidx.room.withTransaction
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookDao
import com.javierreansyah.pinecone.data.local.database.library.BookmarkDao
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteDao
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Atomic reading state and annotations for every publication rendition. */
@Singleton
class ReadingRepository @Inject constructor(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao,
) {
    private val publicationDao get() = database.publicationDao()

    suspend fun currentSession(bookId: String): com.javierreansyah.pinecone.data.model.ReadingSessionToken? = database.withTransaction {
        val runtime = database.operationDao().runtime()
        if (runtime.maintenance) return@withTransaction null
        val book = bookDao.getById(bookId)?.book ?: return@withTransaction null
        val rendition = publicationDao.rendition(bookId, book.activeRendition) ?: return@withTransaction null
        com.javierreansyah.pinecone.data.model.ReadingSessionToken(runtime.generation, bookId, rendition.key, rendition.sha256)
    }

    private suspend fun permits(session: com.javierreansyah.pinecone.data.model.ReadingSessionToken?): Boolean {
        val runtime = database.operationDao().runtime()
        if (runtime.maintenance) return false
        if (session == null) return true
        val book = bookDao.getById(session.bookId)?.book ?: return false
        return runtime.generation == session.generation && book.activeRendition == session.rendition &&
            publicationDao.rendition(session.bookId, session.rendition)?.sha256 == session.sha256
    }

    /** Maps the latest durable source location, then activates the destination in the caller's transaction. */
    suspend fun activateMappedRendition(bookId: String, key: String, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        val book = bookDao.getById(bookId)?.book ?: return@withTransaction
        val source = publicationDao.rendition(bookId, book.activeRendition) ?: return@withTransaction
        val target = publicationDao.rendition(bookId, key) ?: return@withTransaction
        if (source.key != key && source.lastLocatorJson != null) {
            val readMode = publicationDao.rendition(bookId, "read-mode")
            val pages = readMode?.sourceMapJson?.let {
                kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(it)
            }.orEmpty()
            val mapped = RenditionLocations.map(source.lastLocatorJson, target.lastLocatorJson, source.key, key, pages, source.progression)
            publicationDao.putRenditions(listOf(target.copy(lastLocatorJson = mapped, progression = source.progression)))
        }
        activateRendition(bookId, key)
    }

    suspend fun rendition(bookId: String, key: String): PublicationRenditionEntity? =
        publicationDao.rendition(bookId, key)

    fun renditionFlow(bookId: String, key: String): Flow<PublicationRenditionEntity?> =
        publicationDao.renditionFlow(bookId, key)

    suspend fun setReadingOverrides(bookId: String, key: String, json: String, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit? =
        database.withTransaction {
            if (!permits(session)) return@withTransaction
            publicationDao.rendition(bookId, key)
                ?.let { publicationDao.putRenditions(listOf(it.copy(preferencesJson = json))) }
        }

    suspend fun activateRendition(bookId: String, key: String): Unit = database.withTransaction {
        val item = bookDao.getById(bookId)?.book ?: return@withTransaction
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
        session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null,
    ): Unit = database.withTransaction {
        val runtime = database.operationDao().runtime()
        if (runtime.maintenance) return@withTransaction
        val rendition = publicationDao.rendition(bookId, renditionKey) ?: return@withTransaction
        val detailsBook = bookDao.getById(bookId)?.book ?: return@withTransaction
        if (session != null && (session.bookId != bookId || session.rendition != renditionKey || runtime.generation != session.generation ||
                detailsBook.activeRendition != session.rendition || rendition.sha256 != session.sha256)) return@withTransaction
        val progression = locator.locations.totalProgression ?: 0.0

        val hasJumpOrigin = !rendition.jumpOriginLocatorJson.isNullOrBlank()
        val isNewProgressionFurther = progression > rendition.furthestProgression

        val newFurthestProgression =
            if (!hasJumpOrigin && isNewProgressionFurther) progression else rendition.furthestProgression
        val newFurthestLocatorJson = if (!hasJumpOrigin && isNewProgressionFurther) locator.toJSON()
            .toString() else rendition.furthestLocatorJson

        publicationDao.putRenditions(
            listOf(
                rendition.copy(
                    lastLocatorJson = locator.toJSON().toString(),
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
                lastLocatorJson = locator.toJSON().toString(),
                lastReadDate = System.currentTimeMillis(),
                furthestProgression = newFurthestProgression,
                furthestLocatorJson = newFurthestLocatorJson,
            ),
        )
    }

    suspend fun saveJumpOrigin(bookId: String, locator: Locator, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        val detailsBook2 = bookDao.getById(bookId)?.book ?: return@withTransaction
        if (detailsBook2.jumpOriginLocatorJson.isNullOrBlank()) {
            bookDao.update(
                detailsBook2.copy(
                    jumpOriginLocatorJson = locator.toJSON().toString(),
                ),
            )
            publicationDao.rendition(bookId, detailsBook2.activeRendition)?.let {
                publicationDao.putRenditions(
                    listOf(
                        it.copy(
                            jumpOriginLocatorJson = locator.toJSON().toString()
                        )
                    )
                )
            }
        }
    }

    suspend fun saveJumpReadingPosition(bookId: String, locator: Locator, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit? =
        database.withTransaction {
            if (!permits(session)) return@withTransaction
            val detailsBook3 = bookDao.getById(bookId)?.book ?: return@withTransaction
            val progression = locator.locations.totalProgression ?: 0.0

            val existingJumpOrigin = detailsBook3.jumpOriginLocatorJson
            val newJumpOriginJson = if (existingJumpOrigin.isNullOrBlank()) {
                detailsBook3.lastLocatorJson
            } else {
                existingJumpOrigin
            }

            bookDao.update(
                detailsBook3.copy(
                    progression = progression,
                    lastLocatorJson = locator.toJSON().toString(),
                    lastReadDate = System.currentTimeMillis(),
                    jumpOriginLocatorJson = newJumpOriginJson,
                ),
            )
            publicationDao.rendition(bookId, detailsBook3.activeRendition)?.let {
                publicationDao.putRenditions(
                    listOf(
                        it.copy(
                            lastLocatorJson = locator.toJSON().toString(),
                            progression = progression,
                            jumpOriginLocatorJson = newJumpOriginJson
                        )
                    )
                )
            }
        }

    suspend fun clearJumpOrigin(bookId: String, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        val detailsBook4 = bookDao.getById(bookId)?.book ?: return@withTransaction
        if (detailsBook4.jumpOriginLocatorJson != null) {
            bookDao.update(
                detailsBook4.copy(
                    jumpOriginLocatorJson = null,
                ),
            )
            publicationDao.rendition(bookId, detailsBook4.activeRendition)?.let {
                publicationDao.putRenditions(listOf(it.copy(jumpOriginLocatorJson = null)))
            }
        }
    }

    suspend fun resetFurthestToCurrent(bookId: String, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit? = database.withTransaction {
        if (!permits(session)) return@withTransaction
        val detailsBook5 = bookDao.getById(bookId)?.book ?: return@withTransaction
        bookDao.update(
            detailsBook5.copy(
                furthestProgression = detailsBook5.progression,
                furthestLocatorJson = detailsBook5.lastLocatorJson,
            ),
        )
        publicationDao.rendition(bookId, detailsBook5.activeRendition)?.let {
            publicationDao.putRenditions(
                listOf(
                    it.copy(
                        furthestProgression = it.progression,
                        furthestLocatorJson = it.lastLocatorJson
                    )
                )
            )
        }
    }

    suspend fun getLastLocator(bookId: String): Locator? {
        val json = bookDao.getById(bookId)?.book?.lastLocatorJson ?: return null
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
        session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null,
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

    suspend fun removeBookmark(id: Long, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
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
        session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null,
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

    suspend fun updateNote(note: NoteEntity, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        noteDao.update(note)
    }

    fun getNotes(bookId: String): Flow<List<NoteEntity>> = noteDao.getByBookId(bookId)

    suspend fun removeNote(id: Long, session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null): Unit = database.withTransaction {
        if (!permits(session)) return@withTransaction
        noteDao.deleteById(id)
    }

}
