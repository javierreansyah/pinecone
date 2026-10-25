package com.javierreansyah.pinecone

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.javierreansyah.pinecone.ui.features.library.info.BookInfoViewModel
import com.javierreansyah.pinecone.ui.root.AppServicesViewModel
import com.javierreansyah.pinecone.worker.BackupWorker
import dagger.hilt.android.lifecycle.withCreationCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class DependencyInjectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchSharesSingletonsAndRetainsKeyedAssistedViewModelsAcrossRecreation() {
        compose.onRoot().assertExists()
        lateinit var services: AppServicesViewModel
        lateinit var first: BookInfoViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val app = activity.application as PineconeApplication
            services = ViewModelProvider(activity)[AppServicesViewModel::class.java]
            assertSame(app.audioSession, services.audioSession)
            assertSame(app.libraryRepository, services.libraryRepository)
            assertSame(app.readerPreferences, services.readerPreferences)

            val provider = ViewModelProvider(
                activity.viewModelStore,
                activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<BookInfoViewModel.Factory> {
                    it.create("missing-first")
                },
            )
            first = provider["first", BookInfoViewModel::class.java]
            val second = ViewModelProvider(
                activity.viewModelStore,
                activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras.withCreationCallback<BookInfoViewModel.Factory> {
                    it.create("missing-second")
                },
            )["second", BookInfoViewModel::class.java]
            assertNotSame(first, second)
            assertSame(first, provider["first", BookInfoViewModel::class.java])
        }

        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            assertSame(services, ViewModelProvider(activity)[AppServicesViewModel::class.java])
            assertSame(first, ViewModelProvider(activity)["first", BookInfoViewModel::class.java])
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun configuredWorkerFactoryConstructsBackupWorkerWithItsDependencies() {
        val app = compose.activity.application as PineconeApplication
        val worker = TestListenableWorkerBuilder<BackupWorker>(app)
            .setWorkerFactory(app.workerFactory)
            .build()
        assertEquals(BackupWorker::class.java, worker.javaClass)
    }
}
