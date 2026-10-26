package com.javierreansyah.pinecone

import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.javierreansyah.pinecone.ui.features.library.info.BookInfoViewModel
import com.javierreansyah.pinecone.ui.root.AudioControlsViewModel
import com.javierreansyah.pinecone.worker.BackupWorker
import dagger.hilt.android.lifecycle.withCreationCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class DependencyInjectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchSharesPlaybackStateAndRetainsAssistedViewModelsAcrossRecreation() {
        compose.onRoot().assertExists()
        lateinit var services: AudioControlsViewModel
        lateinit var first: BookInfoViewModel
        compose.activityRule.scenario.onActivity { activity ->
            services = ViewModelProvider(activity)[AudioControlsViewModel::class.java]
            val otherControls =
                ViewModelProvider(activity)["other-controls", AudioControlsViewModel::class.java]
            assertNotSame(services, otherControls)
            assertSame(services.state, otherControls.state)

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
            assertSame(services, ViewModelProvider(activity)[AudioControlsViewModel::class.java])
            assertSame(first, ViewModelProvider(activity)["first", BookInfoViewModel::class.java])
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun configuredWorkerFactoryConstructsBackupWorkerWithItsDependencies() {
        val app = compose.activity.application as PineconeApplication
        val manifest = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PROVIDERS.toLong() or PackageManager.GET_META_DATA.toLong()
            ),
        )
        assertFalse(
            "The packaged app must not include WorkManager's default initializer",
            manifest.providers.orEmpty().any { provider ->
                provider.name == "androidx.work.impl.WorkManagerInitializer" ||
                        provider.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true
            },
        )
        val worker = TestListenableWorkerBuilder<BackupWorker>(app)
            .setWorkerFactory(app.workerFactory)
            .build()
        assertEquals(BackupWorker::class.java, worker.javaClass)
    }
}
