package com.example.readerapp.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search

data class SearchState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchPerformed: Boolean = false,
    val isInNavMode: Boolean = false,
    val activeIndex: Int? = null
)

class ReaderSearchViewModel(
    private val application: Application
) : ViewModel() {

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _navigateToLocator = MutableSharedFlow<Locator>(extraBufferCapacity = 1)
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(query = query, searchPerformed = false) }
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun performSearch(query: String, publication: Publication?, positions: List<Locator>) {
        if (publication == null) return
        if (query.isBlank()) {
            _searchState.update {
                it.copy(
                    query = query,
                    results = emptyList(),
                    isLoading = false,
                    searchPerformed = false,
                    isInNavMode = false,
                    activeIndex = null
                )
            }
            return
        }

        searchJob?.cancel()
        _searchState.update {
            it.copy(
                query = query,
                results = emptyList(),
                isLoading = true,
                searchPerformed = true,
                isInNavMode = false,
                activeIndex = null
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val iterator = publication.search(query)
                if (iterator == null) {
                    _searchState.update { it.copy(isLoading = false) }
                    return@launch
                }

                while (true) {
                    val result = iterator.next()
                    val page = result.getOrNull() ?: break
                    val newItems = page.locators.map { locator ->
                        val posIndex = positions.findIndexForLocator(locator)

                        val positionLabel = when {
                            posIndex != -1 -> application.resources.getQuantityString(
                                R.plurals.reader_page_num, posIndex + 1, posIndex + 1
                            )

                            locator.locations.totalProgression != null -> application.getString(
                                R.string.reader_position_at,
                                "${(locator.locations.totalProgression!! * 100).toInt()}%"
                            )

                            else -> ""
                        }

                        SearchResultItem(
                            locator = locator,
                            chapterTitle = locator.title,
                            positionLabel = positionLabel,
                            textBefore = locator.text.before,
                            highlight = locator.text.highlight,
                            textAfter = locator.text.after
                        )
                    }

                    _searchState.update { state ->
                        state.copy(results = state.results + newItems)
                    }
                }

                _searchState.update { it.copy(isLoading = false) }
            } catch (_: Exception) {
                _searchState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectSearchResult(index: Int) {
        val results = _searchState.value.results
        if (index < 0 || index >= results.size) return
        _searchState.update {
            it.copy(
                activeIndex = index,
                isInNavMode = true
            )
        }
        _navigateToLocator.tryEmit(results[index].locator)
    }

    fun nextSearchResult() {
        val state = _searchState.value
        val results = state.results
        if (results.isEmpty()) return
        val next = ((state.activeIndex ?: -1) + 1).coerceAtMost(results.size - 1)
        _searchState.update { it.copy(activeIndex = next) }
        _navigateToLocator.tryEmit(results[next].locator)
    }

    fun prevSearchResult() {
        val state = _searchState.value
        val results = state.results
        if (results.isEmpty()) return
        val prev = ((state.activeIndex ?: 1) - 1).coerceAtLeast(0)
        _searchState.update { it.copy(activeIndex = prev) }
        _navigateToLocator.tryEmit(results[prev].locator)
    }

    fun exitSearchNavigation() {
        _searchState.update { it.copy(isInNavMode = false, activeIndex = null) }
    }

    fun hideSearch() {
        searchJob?.cancel()
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderSearchViewModel(application) as T
        }
    }
}
