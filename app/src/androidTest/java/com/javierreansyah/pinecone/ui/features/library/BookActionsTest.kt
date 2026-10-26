package com.javierreansyah.pinecone.ui.features.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackClient
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class BookActionsTest {
    @Test
    fun failedWritesReachTheErrorStateAndSuccessfulActionsStillWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val repository = LibraryRepository(
            context, db, db.bookDao(), db.shelfDao(), db.spaceDao(),
            PublicationAssetStore(context), Dispatchers.IO
        )
        val playback =
            AudioPlaybackClient(context, AudioPlaybackStore(), scope, Dispatchers.Main.immediate)
        val actions = BookActions(scope, repository, DeleteBooks(repository, playback))
        try {
            db.bookDao().insert(BookEntity("fixture", "Fixture", filePath = "/fixture"))
            val sql = db.openHelper.writableDatabase
            sql.execSQL("CREATE TRIGGER reject_update BEFORE UPDATE ON books BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
            actions.toggleArchive("fixture")
            withTimeout(10_000L.milliseconds) { actions.mutationErrors.failed.first { it } }
            assertFalse(requireNotNull(db.bookDao().getEntity("fixture")).isArchived)

            actions.mutationErrors.dismiss()
            sql.execSQL("DROP TRIGGER reject_update")
            actions.toggleArchive("fixture")
            withTimeout(10_000L.milliseconds) {
                repository.getBookFlow("fixture").first { it?.book?.isArchived == true }
            }
            assertFalse(actions.mutationErrors.failed.value)

            sql.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON books BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
            actions.deleteBook("fixture")
            withTimeout(10_000L.milliseconds) { actions.mutationErrors.failed.first { it } }
            assertNotNull(db.bookDao().getEntity("fixture"))
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            db.close()
        }
    }
}
