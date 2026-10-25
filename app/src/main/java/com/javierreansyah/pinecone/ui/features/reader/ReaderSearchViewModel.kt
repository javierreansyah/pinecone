package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.pdf.PdfSearchMatch
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchPerformed: Boolean = false,
    val isInNavMode: Boolean = false,
    val activeIndex: Int? = null,
)

@HiltViewModel
class ReaderSearchViewModel @Inject constructor(
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
                val base = publication.locatorFromLink(link) ?: continue
                for (index in 0..<pdf.pageCount) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val page = pdf.search(index, query)
                    val text = page.text
                    for ((start, end, quads) in page.matches) {
                        if (matches.size >= 10_000) break
                        val before = text.substring(maxOf(0, start - 60), start)
                        val highlight = text.substring(start, end)
                        val after = text.substring(end, minOf(text.length, end + 100))
                        val locator = base.copyWithLocations(
                            fragments = listOf("page=${index + 1}"),
                            position = index + 1
                        ).copy(
                            text = Locator.Text(
                                before = before,
                                highlight = highlight,
                                after = after
                            )
                        )
                        matches += SearchResultItem(
                            locator,
                            link.title,
                            application.getString(R.string.reader_page_label, index + 1),
                            before, highlight, after,
                            PdfSearchMatch(matches.size, base.href.toString(), index, quads),
                        )
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
        if (index < 0 || index >= results.size) return
        _searchState.update {
            it.copy(
                activeIndex = index,
                isInNavMode = true,
            )
        }
        _navigateToLocator.tryEmit(results[index].locator)
    }

    fun nextSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val next = ((state.activeIndex ?: -1) + 1).coerceAtMost(state.results.size - 1)
        _searchState.update { it.copy(activeIndex = next) }
        _navigateToLocator.tryEmit(state.results[next].locator)
    }

    fun prevSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val prev = ((state.activeIndex ?: 1) - 1).coerceAtLeast(0)
        _searchState.update { it.copy(activeIndex = prev) }
        _navigateToLocator.tryEmit(state.results[prev].locator)
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


}
