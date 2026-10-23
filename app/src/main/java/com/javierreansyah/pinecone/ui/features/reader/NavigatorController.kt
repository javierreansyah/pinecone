@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.ui.features.reader

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.reader.pdf.MuPdfEngineProvider
import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
import com.javierreansyah.pinecone.reader.pdf.MuPdfSettings
import com.javierreansyah.pinecone.reader.pdf.PdfAnnotation
import com.javierreansyah.pinecone.reader.pdf.PdfSearchHighlights
import com.javierreansyah.pinecone.reader.pdf.PdfSelectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Owns only presentation. Publications and background audio have independent session lifetimes. */
class NavigatorController(
    private val lifecycleOwner: LifecycleOwner,
    private val fragmentManager: FragmentManager,
    private val viewModel: ReaderViewModel,
    private val searchViewModel: ReaderSearchViewModel,
    private val notesViewModel: ReaderNotesViewModel,
) : DefaultLifecycleObserver {
    private var epub: EpubNavigatorController? = null
    private var navigator: Navigator? = null
    private var publication: Publication? = null
    private var locationJob: Job? = null
    private var preferencesJob: Job? = null
    private var searchNavigationJob: Job? = null
    private var pdfAnnotationsJob: Job? = null
    private var pdfClearSelectionJob: Job? = null
    private val pdfSearchHighlights = searchViewModel.searchState.map { state ->
        PdfSearchHighlights(
            matches = if (state.searchPerformed) state.results.mapNotNull { it.pdfMatch } else emptyList(),
            focusedIndex = state.activeIndex.takeIf { state.isInNavMode },
        )
    }.stateIn(
        lifecycleOwner.lifecycleScope,
        SharingStarted.WhileSubscribed(5_000),
        PdfSearchHighlights()
    )

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun setupNavigator(publication: Publication) {
        if (this.publication === publication) return
        locationJob?.cancel(); preferencesJob?.cancel(); searchNavigationJob?.cancel()
        pdfAnnotationsJob?.cancel(); pdfClearSelectionJob?.cancel()
        epub?.close(); epub = null
        navigator = null
        fragmentManager.fragments.filter { it.id == R.id.navigator_container }.forEach {
            fragmentManager.beginTransaction().remove(it).commitNow()
        }
        viewModel.onNavigatorDetached()
        this.publication = publication
        notesViewModel.setRendition(viewModel.activeRendition.value)
        searchViewModel.exitSearchNavigation()
        val rendition = viewModel.activeRendition.value
        when (PublicationFormats.profile(publication)) {
            ReaderProfile.EPUB -> {
                epub = EpubNavigatorController(
                    lifecycleOwner,
                    fragmentManager,
                    viewModel,
                    searchViewModel,
                    notesViewModel
                ).also {
                    it.setupNavigator(publication)
                }
                return
            }

            ReaderProfile.IMAGE -> {
                fragmentManager.fragmentFactory = ImageNavigatorFragment.createFactory(
                    publication,
                    viewModel.currentLocator.value ?: viewModel.initialLocator
                )
                fragmentManager.beginTransaction().replace(
                    R.id.navigator_container,
                    ImageNavigatorFragment::class.java,
                    Bundle(),
                    "image_navigator"
                ).commitNow()
                navigator =
                    fragmentManager.findFragmentByTag("image_navigator") as ImageNavigatorFragment
            }

            ReaderProfile.PDF -> {
                val selection = PdfSelectionController(
                    onSelectionChanged = { locator ->
                        notesViewModel.hideViewHighlight()
                        if (locator != null) notesViewModel.showSelectionMenu(locator)
                        else notesViewModel.dismissSelectionBar()
                    },
                    onAnnotationActivated = { id ->
                        notesViewModel.allNotesAndHighlights.value.firstOrNull { it.id == id }
                            ?.let(notesViewModel::viewHighlight)
                    },
                )
                val factory = PdfNavigatorFactory(
                    publication,
                    MuPdfEngineProvider(pdfSearchHighlights, selection)
                )
                fragmentManager.fragmentFactory = factory.createFragmentFactory(
                    viewModel.currentLocator.value ?: viewModel.initialLocator,
                    viewModel.pdfPreferences.value
                )
                val fragment = fragmentManager.fragmentFactory.instantiate(
                    javaClass.classLoader ?: return,
                    PdfNavigatorFragment::class.java.name
                )
                fragmentManager.beginTransaction()
                    .replace(R.id.navigator_container, fragment, "pdf_navigator").commitNow()
                @Suppress("UNCHECKED_CAST")
                val pdf = fragment as PdfNavigatorFragment<MuPdfSettings, MuPdfPreferences>
                navigator = pdf
                preferencesJob = lifecycleOwner.lifecycleScope.launch {
                    viewModel.pdfPreferences.collect { pdf.submitPreferences(it) }
                }
                pdfAnnotationsJob = lifecycleOwner.lifecycleScope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        notesViewModel.allNotesAndHighlights.collect { notes ->
                            selection.applyAnnotations(notes.mapNotNull { note ->
                                val locator =
                                    runCatching { Locator.fromJSON(JSONObject(note.locatorJson)) }.getOrNull()
                                locator?.let { PdfAnnotation(note.id, it, note.color) }
                            })
                        }
                    }
                }
                pdfClearSelectionJob = lifecycleOwner.lifecycleScope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        notesViewModel.clearSelectionEvent.collect { selection.clearSelection() }
                    }
                }
            }

            ReaderProfile.AUDIO -> {
                // The service owns the AudioNavigator. UI destruction must not close it.
                viewModel.startAudio()
                return
            }
        }
        val nav = navigator ?: return
        (nav as? OverflowableNavigator)?.addInputListener(
            DirectionalNavigationAdapter(
                nav,
                animatedTransition = true
            )
        )
        (nav as? VisualNavigator)?.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                viewModel.toggleControls(); return true
            }
        })
        locationJob = lifecycleOwner.lifecycleScope.launch {
            nav.currentLocator.collect {
                if (viewModel.activeRendition.value == rendition) viewModel.onLocatorChanged(it)
                viewModel.savePosition(it, rendition)
            }
        }
        searchNavigationJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.navigateToLocator.collect { locator ->
                    nav.go(locator, animated = true)
                }
            }
        }
    }

    fun go(locator: Locator) {
        if (viewModel.profile.value == ReaderProfile.AUDIO) viewModel.audioGo(locator)
        else {
            epub?.go(locator); navigator?.go(locator)
        }
    }

    fun go(link: Link) {
        if (viewModel.profile.value == ReaderProfile.AUDIO) viewModel.audioGo(link)
        else {
            epub?.go(link); navigator?.go(link)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        locationJob?.cancel(); preferencesJob?.cancel(); searchNavigationJob?.cancel(); epub?.close()
        pdfAnnotationsJob?.cancel(); pdfClearSelectionJob?.cancel()
        owner.lifecycle.removeObserver(this)
    }
}
