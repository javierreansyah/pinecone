package com.example.readerapp.ui.root

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.readerapp.ReaderApplication
import com.example.readerapp.ui.features.dictionary.DictionariesScreen
import com.example.readerapp.ui.features.dictionary.DictionariesViewModel
import com.example.readerapp.ui.features.library.main.LibraryScreen
import com.example.readerapp.ui.features.settings.SettingsScreen
import com.example.readerapp.ui.features.library.archive.ArchiveScreen
import com.example.readerapp.ui.features.library.shelf.ShelfDetailScreen
import com.example.readerapp.ui.features.library.filters.FilterResultScreen
import com.example.readerapp.ui.features.library.info.BookInfoScreen
import com.example.readerapp.ui.features.library.info.EditBookScreen
import com.example.readerapp.ui.features.library.filters.AllFilterItemsScreen
import com.example.readerapp.ui.features.reader.ReaderActivity
import kotlinx.coroutines.launch

@Composable
fun NavGraph(
    navController: NavHostController,
    drawerState: DrawerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as ReaderApplication
    val readerPreferences = app.readerPreferences

    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToReader = { bookId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                    }
                    context.startActivity(intent)
                },
                onOpenDrawerClick = {
                    val isResumed = navController.currentBackStackEntry
                        ?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
                    if (isResumed) {
                        scope.launch { drawerState.open() }
                    }
                },
                onNavigateToShelf = { shelfId, name, count ->
                    navController.navigate(Screen.ShelfDetail.createRoute(shelfId, name, count))
                },
                onNavigateToAuthor = { authorName ->
                    navController.navigate(Screen.AuthorDetail.createRoute(authorName))
                },
                onNavigateToTag = { tagName ->
                    navController.navigate(Screen.TagDetail.createRoute(tagName))
                },
                onNavigateToAllAuthors = {
                    navController.navigate(Screen.AllAuthors.route)
                },
                onNavigateToAllTags = {
                    navController.navigate(Screen.AllTags.route)
                },
                onNavigateToBookInfo = { bookId ->
                    navController.navigate(Screen.BookInfo.createRoute(bookId))
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.Library.route, inclusive = false)
                }
            )
        }
        composable(Screen.Archives.route) {
            ArchiveScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.Library.route, inclusive = false)
                },
                onNavigateToReader = { bookId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                    }
                    context.startActivity(intent)
                },
                onNavigateToBookInfo = { bookId ->
                    navController.navigate(Screen.BookInfo.createRoute(bookId))
                }
            )
        }
        composable(
            route = Screen.ShelfDetail.route,
            arguments = listOf(
                navArgument("shelfId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("count") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val shelfId = backStackEntry.arguments?.getString("shelfId") ?: return@composable
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val count = backStackEntry.arguments?.getInt("count") ?: 0
            ShelfDetailScreen(
                shelfId = shelfId,
                initialShelfName = name,
                initialBookCount = count,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToReader = { bookId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                    }
                    context.startActivity(intent)
                },
                onNavigateToBookInfo = { bookId ->
                    navController.navigate(Screen.BookInfo.createRoute(bookId))
                }
            )
        }
        composable(Screen.AuthorDetail.route) { backStackEntry ->
            val authorName = backStackEntry.arguments?.getString("authorName") ?: return@composable
            FilterResultScreen(
                filterType = "author",
                filterValue = authorName,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToReader = { bookId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                    }
                    context.startActivity(intent)
                },
                onNavigateToMerged = { newName ->
                    navController.popBackStack()
                    navController.navigate(Screen.AuthorDetail.createRoute(newName))
                },
                onNavigateToBookInfo = { bookId ->
                    navController.navigate(Screen.BookInfo.createRoute(bookId))
                }
            )
        }
        composable(Screen.TagDetail.route) { backStackEntry ->
            val tagName = backStackEntry.arguments?.getString("tagName") ?: return@composable
            FilterResultScreen(
                filterType = "tag",
                filterValue = tagName,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToReader = { bookId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                    }
                    context.startActivity(intent)
                },
                onNavigateToMerged = { newName ->
                    navController.popBackStack()
                    navController.navigate(Screen.TagDetail.createRoute(newName))
                },
                onNavigateToBookInfo = { bookId ->
                    navController.navigate(Screen.BookInfo.createRoute(bookId))
                }
            )
        }
        composable(Screen.AllAuthors.route) {
            AllFilterItemsScreen(
                filterType = "author",
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToDetail = { authorName ->
                    navController.navigate(Screen.AuthorDetail.createRoute(authorName))
                }
            )
        }
        composable(Screen.AllTags.route) {
            AllFilterItemsScreen(
                filterType = "tag",
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToDetail = { tagName ->
                    navController.navigate(Screen.TagDetail.createRoute(tagName))
                }
            )
        }
        composable(Screen.Dictionaries.route) {
            val factory = DictionariesViewModel.Factory(
                app.dictionaryRepository,
                app.dictionaryImportManager,
                app.dictionaryBackupManager,
                readerPreferences
            )
            val dictViewModel: DictionariesViewModel = viewModel(factory = factory)
            DictionariesScreen(
                viewModel = dictViewModel,
                onBack = { navController.popBackStack(Screen.Library.route, inclusive = false) }
            )
        }
        composable(
            route = Screen.BookInfo.route,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pinecone://book_info/{bookId}" })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            BookInfoScreen(
                bookId = bookId,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack() else {
                        (context as? Activity)?.finish()
                    }
                },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditBook.createRoute(id))
                }
            )
        }
        composable(
            route = Screen.EditBook.route,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            EditBookScreen(
                bookId = bookId,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                }
            )
        }
    }
}
