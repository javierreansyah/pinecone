package com.javierreansyah.pinecone.ui.root

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.ui.features.dictionary.DictionariesScreen
import com.javierreansyah.pinecone.ui.features.dictionary.DictionariesViewModel
import com.javierreansyah.pinecone.ui.features.library.archive.ArchiveScreen
import com.javierreansyah.pinecone.ui.features.library.filters.AllFilterItemsScreen
import com.javierreansyah.pinecone.ui.features.library.filters.FilterResultScreen
import com.javierreansyah.pinecone.ui.features.library.info.BookInfoScreen
import com.javierreansyah.pinecone.ui.features.library.info.EditBookScreen
import com.javierreansyah.pinecone.ui.features.library.main.LibraryRoute
import com.javierreansyah.pinecone.ui.features.library.shelf.SelectShelfScreen
import com.javierreansyah.pinecone.ui.features.library.shelf.SelectShelfViewModel
import com.javierreansyah.pinecone.ui.features.library.shelf.ShelfDetailScreen
import com.javierreansyah.pinecone.ui.features.reader.ReaderActivity
import com.javierreansyah.pinecone.ui.features.settings.SettingsScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavGraph(
    backStack: MutableList<NavKey>,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = object :
            ActivityResultContract<Array<String>, List<@JvmSuppressWildcards Uri>>() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, input)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                    val uri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Download"
                    )
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
                val clipData = intent.clipData
                if (clipData != null) {
                    return (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                }
                return listOfNotNull(intent.data)
            }
        },
        onResult = { uris ->
            mainViewModel.importBooks(uris)
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                val uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:"
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        },
        onResult = { uri ->
            uri?.let {
                mainViewModel.scanFolder(it)
            }
        }
    )
    val context = LocalContext.current
    val app = context.applicationContext as PineconeApplication
    val readerPreferences = app.readerPreferences
    val density = LocalDensity.current

    // Debounce to prevent rapid double-clicks from crashing the transition state machine
    var lastBackClickTime by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(
            0L
        )
    }

    val navigateBack: () -> Unit = {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackClickTime > 300L) {
            lastBackClickTime = now
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                (context as? Activity)?.finish()
            }
        }
    }

    // Exclude the top area from the system's edge swipe.
    // This prevents the user's thumb from accidentally starting a predictive back gesture while clicking.
    val windowInfo = LocalWindowInfo.current
    val exclusionWidth = windowInfo.containerSize.width.toFloat()
    val exclusionHeight = with(density) { 120.dp.toPx() }

    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    NavDisplay(
        backStack = backStack,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .systemGestureExclusion {
                Rect(0f, 0f, exclusionWidth, exclusionHeight)
            },
        onBack = {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastBackClickTime > 400L || backStack.size == 1) {
                lastBackClickTime = now
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                } else {
                    (context as? Activity)?.finish()
                }
            }
        },
        transitionSpec = {
            (fadeIn(animationSpec = effectsSpec) + scaleIn(
                initialScale = 0.9f,
                animationSpec = spatialSpec
            ) togetherWith
                    fadeOut(animationSpec = effectsSpec) + scaleOut(
                targetScale = 0.9f,
                animationSpec = spatialSpec
            )).apply {
                targetContentZIndex = 1f
            }
        },
        popTransitionSpec = {
            (fadeIn(animationSpec = effectsSpec) togetherWith
                    fadeOut(animationSpec = effectsSpec) + scaleOut(
                targetScale = 0.9f,
                animationSpec = spatialSpec
            )).apply {
                targetContentZIndex = -1f
            }
        },
        predictivePopTransitionSpec = {
            (fadeIn(animationSpec = effectsSpec) togetherWith
                    fadeOut(animationSpec = effectsSpec) + scaleOut(
                targetScale = 0.9f,
                animationSpec = spatialSpec
            )).apply {
                targetContentZIndex = -1f
            }
        },
        entryProvider = entryProvider {
            entry<Screen.Library> {
                LibraryRoute(
                    onNavigateToReader = { bookId ->
                        val intent = Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToShelf = { shelfId, name, count ->
                        backStack.add(Screen.ShelfDetail(shelfId, name, count))
                    },
                    onNavigateToAuthor = { authorName ->
                        backStack.add(Screen.AuthorDetail(authorName))
                    },
                    onNavigateToTag = { tagName ->
                        backStack.add(Screen.TagDetail(tagName))
                    },
                    onNavigateToAllAuthors = {
                        backStack.add(Screen.AllAuthors)
                    },
                    onNavigateToAllTags = {
                        backStack.add(Screen.AllTags)
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToAddToShelf = { bookId ->
                        backStack.add(Screen.AddToShelf(bookId))
                    },
                    onNavigateToArchives = {
                        backStack.clear()
                        backStack.add(Screen.Library)
                        backStack.add(Screen.Archives)
                    },
                    onNavigateToSettings = {
                        backStack.clear()
                        backStack.add(Screen.Library)
                        backStack.add(Screen.Settings)
                    },
                    onNavigateToDictionaries = {
                        backStack.clear()
                        backStack.add(Screen.Library)
                        backStack.add(Screen.Dictionaries)
                    },
                    onImportFilesClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/epub+zip"
                            )
                        )
                    },
                    onScanFolderClick = { folderPickerLauncher.launch(null) }
                )
            }
            entry<Screen.Settings> {
                SettingsScreen(
                    onNavigateBack = navigateBack
                )
            }
            entry<Screen.Archives> {
                ArchiveScreen(
                    onNavigateBack = navigateBack,
                    onNavigateToReader = { bookId ->
                        val intent = Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToAddToShelf = { bookId ->
                        backStack.add(Screen.AddToShelf(bookId))
                    }
                )
            }
            entry<Screen.ShelfDetail> { args ->
                ShelfDetailScreen(
                    shelfId = args.shelfId,
                    initialShelfName = args.name,
                    initialBookCount = args.count,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = { bookId ->
                        val intent = Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToAddToShelf = { bookId ->
                        backStack.add(Screen.AddToShelf(bookId))
                    }
                )
            }
            entry<Screen.AuthorDetail> { args ->
                FilterResultScreen(
                    filterType = "author",
                    filterValue = args.authorName,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = { bookId ->
                        val intent = Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToMerged = { newName ->
                        backStack.removeLastOrNull()
                        backStack.add(Screen.AuthorDetail(newName))
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToAddToShelf = { bookId ->
                        backStack.add(Screen.AddToShelf(bookId))
                    }
                )
            }
            entry<Screen.TagDetail> { args ->
                FilterResultScreen(
                    filterType = "tag",
                    filterValue = args.tagName,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = { bookId ->
                        val intent = Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToMerged = { newName ->
                        backStack.removeLastOrNull()
                        backStack.add(Screen.TagDetail(newName))
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToAddToShelf = { bookId ->
                        backStack.add(Screen.AddToShelf(bookId))
                    }
                )
            }
            entry<Screen.AllAuthors> {
                AllFilterItemsScreen(
                    filterType = "author",
                    onNavigateBack = navigateBack,
                    onNavigateToDetail = { authorName ->
                        backStack.add(Screen.AuthorDetail(authorName))
                    }
                )
            }
            entry<Screen.AllTags> {
                AllFilterItemsScreen(
                    filterType = "tag",
                    onNavigateBack = navigateBack,
                    onNavigateToDetail = { tagName ->
                        backStack.add(Screen.TagDetail(tagName))
                    }
                )
            }
            entry<Screen.Dictionaries> {
                val factory = DictionariesViewModel.Factory(
                    app.dictionaryRepository,
                    app.dictionaryImportManager,
                    readerPreferences
                )
                val dictViewModel: DictionariesViewModel = viewModel(factory = factory)
                DictionariesScreen(
                    viewModel = dictViewModel,
                    onBack = navigateBack
                )
            }
            entry<Screen.BookInfo> { args ->
                BookInfoScreen(
                    bookId = args.bookId,
                    onNavigateBack = navigateBack,
                    onNavigateToEdit = { id ->
                        backStack.add(Screen.EditBook(id))
                    },
                    onNavigateToTag = { tagName ->
                        backStack.add(Screen.TagDetail(tagName))
                    }
                )
            }
            entry<Screen.EditBook> { args ->
                EditBookScreen(
                    bookId = args.bookId,
                    onNavigateBack = navigateBack
                )
            }
            entry<Screen.AddToShelf> { args ->
                val selectShelfViewModel: SelectShelfViewModel = viewModel(
                    factory = object :
                        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app) {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            if (modelClass.isAssignableFrom(SelectShelfViewModel::class.java)) {
                                @Suppress("UNCHECKED_CAST") return SelectShelfViewModel(app) as T
                            }
                            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                        }
                    }
                )
                SelectShelfScreen(
                    bookId = args.bookId,
                    viewModel = selectShelfViewModel,
                    onNavigateBack = navigateBack
                )
            }
        }
    )
}
