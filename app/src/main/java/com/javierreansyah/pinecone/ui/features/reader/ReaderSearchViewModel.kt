package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    val activeIndex: Int? = null,
)

class ReaderSearchViewModel(
    private val application: Application,
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
        searchJob?.cancel()
        if (publication == null) return
        if (query.isBlank()) {
            _searchState.update {
                it.copy(
                    query = query,
                    results = emptyList(),
                    isLoading = false,
                    searchPerformed = false,
                    isInNavMode = false,
                    activeIndex = null,
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
                activeIndex = null,
            )
        }

        searchJob = viewModelScope.launch {
            try {
                if (com.javierreansyah.pinecone.data.publication.PublicationFormats.profile(
                        publication
                    ) == com.javierreansyah.pinecone.data.publication.ReaderProfile.PDF
                ) {
                    searchPdf(query, publication)
                    return@launch
                }
                val iterator = publication.search(query)
                if (iterator == null) {
                    _searchState.update { it.copy(isLoading = false) }
                    return@launch
                }

                try {
                    while (true) {
                        val result = iterator.next()
                        val (_, _, pageLocators) = result.getOrNull() ?: break
                        val newItems = pageLocators.map { locator ->
                            val posIndex = positions.findIndexForLocator(locator)

                            val positionLabel = when {
                                posIndex != -1 -> application.resources.getQuantityString(
                                    R.plurals.reader_page_num, posIndex + 1, posIndex + 1,
                                )

                                locator.locations.totalProgression != null -> application.getString(
                                    R.string.reader_position_at,
                                    "${(locator.locations.totalProgression!! * 100).toInt()}%",
                                )

                                else -> ""
                            }

                            SearchResultItem(
                                locator = locator,
                                chapterTitle = locator.title,
                                positionLabel = positionLabel,
                                textBefore = locator.text.before,
                                highlight = locator.text.highlight,
                                textAfter = locator.text.after,
                            )
                        }

                        _searchState.update { state ->
                            state.copy(results = state.results + newItems)
                        }
                    }
                } finally {
                    iterator.close()
                }

                _searchState.update { it.copy(isLoading = false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _searchState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun searchPdf(query: String, publication: Publication) {
        val matches = mutableListOf<SearchResultItem>()
        for (link in publication.readingOrder) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val resource = publication.get(link) ?: continue
            val pdf =
                com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory().open(resource, null)
                    .getOrNull() ?: continue
            try {
                for (index in 0 until pdf.pageCount) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val text =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            pdf.access { doc ->
                                val page = doc.loadPage(index)
                                try {
                                    val structured = page.toStructuredText()
                                    try {
                                        structured.asText()
                                    } finally {
                                        structured.destroy()
                                    }
                                } finally {
                                    page.destroy()
                                }
                            }
                        }
                    var offset = text.indexOf(query, ignoreCase = true)
                    while (offset >= 0 && matches.size < 10_000) {
                        val base = publication.locatorFromLink(link) ?: break
                        val locator = base.copyWithLocations(
                            fragments = listOf("page=${index + 1}"),
                            position = index + 1
                        )
                        matches += SearchResultItem(
                            locator,
                            link.title,
                            application.getString(R.string.reader_page_label, index + 1),
                            text.substring(maxOf(0, offset - 60), offset),
                            text.substring(offset, offset + query.length),
                            text.substring(
                                offset + query.length,
                                minOf(text.length, offset + query.length + 100)
                            )
                        )
                        offset = text.indexOf(query, offset + query.length, ignoreCase = true)
                    }
                    _searchState.update { it.copy(results = matches.toList()) }
                    if (matches.size >= 10_000) break
                }
            } finally {
                pdf.close()
            }
        }
        _searchState.update { it.copy(isLoading = false) }
    }

    fun selectSearchResult(index: Int) {
        val results = _searchState.value.results
        if ((index < 0) || (index >= results.size)) return
        _searchState.update {
            it.copy(
                activeIndex = index,
                isInNavMode = true,
            )
        }
        _navigateToLocator.tryEmit(results[index].locator)
    }

    fun nextSearchResult() {
        val (_, stateResults, _, _, _, stateActiveIndex) = _searchState.value
        if (stateResults.isEmpty()) return
        val next = ((stateActiveIndex ?: -1) + 1).coerceAtMost(stateResults.size - 1)
        _searchState.update { it.copy(activeIndex = next) }
        _navigateToLocator.tryEmit(stateResults[next].locator)
    }

    fun prevSearchResult() {
        val (_, stateResults2, _, _, _, stateActiveIndex2) = _searchState.value
        if (stateResults2.isEmpty()) return
        val prev = ((stateActiveIndex2 ?: 1) - 1).coerceAtLeast(0)
        _searchState.update { it.copy(activeIndex = prev) }
        _navigateToLocator.tryEmit(stateResults2[prev].locator)
    }

    fun exitSearchNavigation() {
        searchJob?.cancel()
        _searchState.update {
            it.copy(
                isInNavMode = false,
                activeIndex = null,
                query = "",
                results = emptyList(),
                searchPerformed = false,
                isLoading = false,
            )
        }
    }

    fun hideSearch() {
        searchJob?.cancel()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderSearchViewModel(application) as T
        }
    }
}
