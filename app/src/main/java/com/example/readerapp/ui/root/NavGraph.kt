package com.example.readerapp.ui.root

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.readerapp.ReaderApplication
import com.example.readerapp.ui.features.dictionary.DictionariesScreen
import com.example.readerapp.ui.features.dictionary.DictionariesViewModel
import com.example.readerapp.ui.features.library.archive.ArchiveScreen
import com.example.readerapp.ui.features.library.filters.AllFilterItemsScreen
import com.example.readerapp.ui.features.library.filters.FilterResultScreen
import com.example.readerapp.ui.features.library.info.BookInfoScreen
import com.example.readerapp.ui.features.library.info.EditBookScreen
import com.example.readerapp.ui.features.library.main.LibraryRoute
import com.example.readerapp.ui.features.library.shelf.SelectShelfScreen
import com.example.readerapp.ui.features.library.shelf.SelectShelfViewModel
import com.example.readerapp.ui.features.library.shelf.ShelfDetailScreen
import com.example.readerapp.ui.features.reader.ReaderActivity
import com.example.readerapp.ui.features.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun NavGraph(
    backStack: MutableList<NavKey>,
    drawerState: DrawerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as ReaderApplication
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
        if (now - lastBackClickTime > 300L) { // Matches the 300ms transition duration perfectly
            lastBackClickTime = now
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                (context as? Activity)?.finish()
            }
        }
    }

    // Exclude the top-left area where the app bar back button sits from the system's edge swipe.
    // This prevents the user's thumb from accidentally starting a predictive back gesture while clicking.
    val exclusionWidth = with(density) { 60.dp.toPx() }
    val exclusionHeight = with(density) { 120.dp.toPx() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .systemGestureExclusion {
                Rect(0f, 0f, exclusionWidth, exclusionHeight)
            },
        onBack = {
            val now = android.os.SystemClock.elapsedRealtime()
            // Predictive/System back gestures also get debounced to be   safe
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
            (fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(300)
            ) togetherWith
                    fadeOut(animationSpec = tween(300)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(300)
            )).apply {
                targetContentZIndex = 1f
            }
        },
        popTransitionSpec = {
            (fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(300)
            )).apply {
                targetContentZIndex = -1f
            }
        },
        predictivePopTransitionSpec = {
            (fadeIn() togetherWith
                    fadeOut() + scaleOut(targetScale = 0.9f)).apply {
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
                    onOpenDrawerClick = {
                        scope.launch { drawerState.open() }
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
                    }
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
