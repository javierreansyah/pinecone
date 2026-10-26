package com.javierreansyah.pinecone.ui.root

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.javierreansyah.pinecone.ui.components.ScreenBackDispatcher
import com.javierreansyah.pinecone.ui.components.ScreenNavDisplay
import com.javierreansyah.pinecone.ui.features.dictionary.DictionariesScreen
import com.javierreansyah.pinecone.ui.features.dictionary.DictionariesViewModel
import com.javierreansyah.pinecone.ui.features.library.archive.ArchiveScreen
import com.javierreansyah.pinecone.ui.features.library.filters.AllFilterItemsScreen
import com.javierreansyah.pinecone.ui.features.library.filters.FilterResultScreen
import com.javierreansyah.pinecone.ui.features.library.info.BookInfoScreen
import com.javierreansyah.pinecone.ui.features.library.info.EditBookScreen
import com.javierreansyah.pinecone.ui.features.library.main.LibraryRoute
import com.javierreansyah.pinecone.ui.features.library.shelf.ShelfDetailScreen
import com.javierreansyah.pinecone.ui.features.library.unsorted.UnsortedScreen
import com.javierreansyah.pinecone.ui.features.reader.ReaderActivity
import com.javierreansyah.pinecone.ui.features.settings.AboutScreen
import com.javierreansyah.pinecone.ui.features.settings.OpenSourceLicensesScreen
import com.javierreansyah.pinecone.ui.features.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

@Composable
fun NavGraph(
    backStack: MutableList<NavKey>,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = object :
            ActivityResultContract<Array<String>, List<@JvmSuppressWildcards Uri>>() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, input)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                    val uri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Download",
                    )
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
                val clipData = intent.clipData
                return clipData?.let { data ->
                    (0..<data.itemCount).map { data.getItemAt(it).uri }
                } ?: listOfNotNull(intent.data)
            }
        },
    ) { uris ->
        if (uris.isNotEmpty()) {
            backStack.clear()
            backStack.add(Screen.Library)
            backStack.add(Screen.Unsorted)
            mainViewModel.importBooks(uris)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                val uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:",
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        },
    ) { uri ->
        uri?.let {
            backStack.clear()
            backStack.add(Screen.Library)
            backStack.add(Screen.Unsorted)
            mainViewModel.scanFolder(it)
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openBook: (String, Locator?) -> Unit = { bookId, locator ->
        scope.launch {
            if (mainViewModel.isAudiobook(bookId)) {
                backStack.openAudioPlayer(bookId, locator?.toJSON()?.toString())
            } else {
                context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                    putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                })
            }
        }
    }
    val navigateToReader: (String) -> Unit = { openBook(it, null) }
    val density = LocalDensity.current
    var organizeBookIds by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Set<String>?>(null) }
    val libraryState by mainViewModel.libraryState.collectAsStateWithLifecycle()
    var stableGeneration by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(libraryState) {
        libraryState?.takeUnless { it.second }?.let { (generation, _) ->
            if (stableGeneration != null && stableGeneration != generation) {
                organizeBookIds = null
                backStack.clear()
                backStack.add(Screen.Library)
            }
            stableGeneration = generation
        }
    }

    val backDispatcher = remember { ScreenBackDispatcher() }
    val navigateBack: () -> Unit = backDispatcher::onBack

    // Exclude the top area from the system's edge swipe.
    // This prevents the user's thumb from accidentally starting a predictive back gesture while clicking.
    val windowInfo = LocalWindowInfo.current
    val exclusionWidth = windowInfo.containerSize.width.toFloat()
    val exclusionHeight = with(density) { 120.dp.toPx() }

    val imports by mainViewModel.importOperations.collectAsStateWithLifecycle()
    val activeImport = imports.firstOrNull { !it.state.terminal }
    com.javierreansyah.pinecone.ui.components.WorkNotificationPermission(activeImport != null)
    androidx.compose.foundation.layout.Column(modifier) {
    activeImport?.let { operation ->
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(com.javierreansyah.pinecone.R.string.publication_work_importing) + " · " +
                    androidx.compose.ui.res.stringResource(com.javierreansyah.pinecone.R.string.publication_work_progress, operation.completed, operation.total),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = { mainViewModel.cancelImport(operation.id) }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        }
    }
    ScreenNavDisplay(
        backStack = backStack.filterNot { it is Screen.AudioPlayer },
        backDispatcher = backDispatcher,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        modifier = Modifier.weight(1f)
            .background(MaterialTheme.colorScheme.background)
            .systemGestureExclusion {
                Rect(0f, 0f, exclusionWidth, exclusionHeight)
            },
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                (context as? Activity)?.finish()
            }
        },
        entryProvider = entryProvider {
            entry<Screen.Library> {
                LibraryRoute(
                    onNavigateToReader = navigateToReader,
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
                    onNavigateToAllSpaces = {
                        backStack.add(Screen.AllSpaces)
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                    onNavigateToUnsorted = {
                        backStack.clear()
                        backStack.add(Screen.Library)
                        backStack.add(Screen.Unsorted)
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
                            com.javierreansyah.pinecone.data.publication.PublicationFormats.pickerMimeTypes,
                        )
                    },
                ) { folderPickerLauncher.launch(null) }
            }
            entry<Screen.Settings> {
                SettingsScreen(
                    onNavigateBack = navigateBack,
                ) {
                    backStack.add(Screen.About)
                }
            }
            entry<Screen.About> {
                AboutScreen(
                    onNavigateBack = navigateBack,
                    onNavigateToOpenSourceLicenses = {
                        backStack.add(Screen.OpenSourceLicenses)
                    },
                )
            }
            entry<Screen.OpenSourceLicenses> {
                OpenSourceLicensesScreen(
                    onNavigateBack = navigateBack,
                )
            }
            entry<Screen.Unsorted> {
                UnsortedScreen(
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.Archives> {
                ArchiveScreen(
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.ShelfDetail> { (argsShelfId, argsName, argsCount) ->
                ShelfDetailScreen(
                    shelfId = argsShelfId,
                    initialShelfName = argsName,
                    initialBookCount = argsCount,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.AuthorDetail> { (argsAuthorName) ->
                FilterResultScreen(
                    filterType = "author",
                    filterValue = argsAuthorName,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToMerged = { newName ->
                        backStack.removeLastOrNull()
                        backStack.add(Screen.AuthorDetail(newName))
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.TagDetail> { (argsTagName) ->
                FilterResultScreen(
                    filterType = "tag",
                    filterValue = argsTagName,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToMerged = { newName ->
                        backStack.removeLastOrNull()
                        backStack.add(Screen.TagDetail(newName))
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.SpaceDetail> { (argsSpaceName) ->
                FilterResultScreen(
                    filterType = "space",
                    filterValue = argsSpaceName,
                    onNavigateBack = navigateBack,
                    onNavigateToReader = navigateToReader,
                    onNavigateToMerged = { newName ->
                        backStack.removeLastOrNull()
                        backStack.add(Screen.SpaceDetail(newName))
                    },
                    onNavigateToBookInfo = { bookId ->
                        backStack.add(Screen.BookInfo(bookId))
                    },
                    onNavigateToOrganize = { bookIds ->
                        organizeBookIds = bookIds
                    },
                )
            }
            entry<Screen.AllAuthors> {
                AllFilterItemsScreen(
                    filterType = "author",
                    onNavigateBack = navigateBack,
                ) { authorName ->
                    backStack.add(Screen.AuthorDetail(authorName))
                }
            }
            entry<Screen.AllTags> {
                AllFilterItemsScreen(
                    filterType = "tag",
                    onNavigateBack = navigateBack,
                ) { tagName ->
                    backStack.add(Screen.TagDetail(tagName))
                }
            }
            entry<Screen.AllSpaces> {
                AllFilterItemsScreen(
                    filterType = "space",
                    onNavigateBack = navigateBack,
                ) { spaceName ->
                    backStack.add(Screen.SpaceDetail(spaceName))
                }
            }
            entry<Screen.Dictionaries> {
                val dictViewModel: DictionariesViewModel = hiltViewModel()
                DictionariesScreen(
                    viewModel = dictViewModel,
                    onBack = navigateBack,
                )
            }
            entry<Screen.BookInfo> { (argsBookId) ->
                BookInfoScreen(
                    bookId = argsBookId,
                    onNavigateToReader = openBook,
                    onNavigateBack = navigateBack,
                    onNavigateToEdit = { id ->
                        backStack.add(Screen.EditBook(id))
                    },
                    onNavigateToTag = { tagName ->
                        backStack.add(Screen.TagDetail(tagName))
                    },
                ) { bookIds ->
                    organizeBookIds = bookIds
                }
            }
            entry<Screen.EditBook> { (argsBookId2) ->
                EditBookScreen(
                    bookId = argsBookId2,
                    onNavigateBack = navigateBack,
                )
            }
        },
    )

    }

    val organizerEntries = androidx.navigation3.runtime.rememberDecoratedNavEntries(
        backStack = organizeBookIds?.let { listOf(Screen.Organize(it.sorted())) }.orEmpty(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Screen.Organize> { selection ->
                val organizeViewModel = hiltViewModel<
                    com.javierreansyah.pinecone.ui.features.library.organize.OrganizeViewModel,
                    com.javierreansyah.pinecone.ui.features.library.organize.OrganizeViewModel.Factory,
                >(creationCallback = { it.create(selection.bookIds) })
                com.javierreansyah.pinecone.ui.features.library.organize.OrganizeBottomSheet(organizeViewModel) { organizeBookIds = null }
            }
        },
    )
    organizerEntries.lastOrNull()?.Content()

    val importErrorReport by mainViewModel.importErrorReport.collectAsStateWithLifecycle()
    importErrorReport?.let { report ->
        ImportErrorDialog(
            report = report,
        ) { mainViewModel.dismissImportErrorReport() }
    }
}
