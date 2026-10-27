What this repo does: Pinecone is a local Android reader for books, PDFs, comics, images, and
audiobooks. It also manages library organization, annotations, dictionaries, and backups. This audit
assumes one person using one installation, with a library of hundreds to several thousand titles,
large publications, and background work running alongside the reader.

Audit date: 2026-10-27. Scope: simplification, refactoring, and reuse while keeping functionality
and feel the same. The original audit made no application changes; the implementation status below
records the subsequent authorized refactor.

## Implementation status

Implemented findings **1-8 and 10-20**. The production Kotlin/Java inventory fell from **39,932 to
39,067 lines**, a net reduction of **865 lines**, including the replacement helpers and excluding
tests/resources. The historical findings below retain their original line references.

**Finding 9 was evaluated and retained:** a device experiment preloaded the Google Fonts faces at
weight 400 and compared rasterized `Aa` glyphs with each bundled regular face. Source Serif 4,
Source Sans 3, Literata, Atkinson Hyperlegible, and Source Code Pro all differed on
`Resizable_Experimental`; replacing them would violate the same-appearance requirement, so the
existing provider, certificates, and dependency remain. The temporary comparison test was removed
after the experiment.

The changes share selection bookkeeping, editing transitions, filter content, preference wiring,
numeric callbacks, choice controls, annotation decoding/sorting/formatting, collection rendering,
and swatch frames. They delete the unreachable note dialog, its callback wiring, the reader sheet
forwarding function, and the empty HTML wrapper. Existing storage transaction, recovery, session,
cancellation, and validation boundaries remain in place.

Regression checks cover selection lifecycle, duplicate choice labels, numeric callback
direction/disabled controls, the reader vertical-margin step and endpoints, Back/Cancel/Save
routing, inverted disabled switches, invalid annotation locators, sorting direction, and
tie-breaking. A source comparison also verified that all nine text-setting numeric callbacks retain
their original step, rounding, and bounds in both directions.

**Device validation:** on `Resizable_Experimental` (Android 14), the UI package passed 35 tests and
the library database package passed five tests. After correcting the vertical-margin callback,
`UiReuseTest` passed all five tests, including the new reader-settings regression: **41 distinct
passing device tests** across those runs (four shared-control tests were repeated). Final
screenshots of library filters and settings were visually checked. These checks do not establish
pixel/animation equivalence for every screen.

**Complete validation: BUILD SUCCESSFUL**, exit 0, in 9m 31s. All three app lint reports say
`No issues found.` The final console contains no compiler, Gradle deprecation, or lint warnings. JVM
reports contain **516 variant test cases, zero failures/errors/skips**; this includes repeated
variants and reused incremental results, not 516 independent or freshly executed tests. Gradle
reported 393 actionable tasks: 79 executed, eight from cache, 306 up to date.

Executed from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:assembleStaging :app:testStagingUnitTest :app:lintStaging --continue --warning-mode all --no-parallel --max-workers=1
```

The single-worker options avoid the cross-variant Hilt generated-source/lint race encountered during
the first parallel run. The extracted swatch helper's modifier parameter order was corrected after
lint flagged it. No checks were weakened or new lint exceptions introduced. The final console is in
`build/audit-complete.log`; device results are in `build/audit-ui-tests.log`,
`build/audit-library-tests.log`, and `build/audit-ui-reuse-tests.log`. These are local build
artifacts.

**Remaining limits:** no IDE inspections, physical-device trials, exhaustive animation/pixel
comparison, process-death/playback soak tests, large-library benchmarks, or full PDF fixture sweep
were performed for this refactor. Google Fonts remains intentionally because the tested replacement
changed rendering.

The original source inventory contained **39,932 lines of production Kotlin and Java in 222 files**,
excluding resources, generated code, and tests:

| Module        | Files |  Lines | Main responsibility                                                 |
|---------------|------:|-------:|---------------------------------------------------------------------|
| `app`         |   138 | 29,972 | Screens, navigation, reader presentation, playback service, workers |
| `data`        |    53 |  6,201 | Database, preferences, imports, backups, dictionaries               |
| `publication` |    19 |  3,363 | Publication metadata, PDF conversion, native PDF integration        |
| `core/model`  |    12 |    396 | Shared models and settings contracts                                |

Most of the realistic reduction is in screen composition and repeated callbacks. The evidence
supports roughly **805–1,280 fewer production source lines**, about **2–3%** of this inventory, plus
a possible dependency removal. These were audit estimates of net savings after replacement helpers;
additional tests are not deducted. No larger behavior-preserving reduction was established in this
pass.

## Should fix

1. **Delete the contents sheet's unreachable add-note dialog**
   ([ContentsBottomSheet.kt:99](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/ContentsBottomSheet.kt#L99), [dialog body:274](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/ContentsBottomSheet.kt#L274))
    - **What this is:** The contents sheet contains a note editor and accepts an `onAddNote`
      callback. Both the reader overlay and the audio screen supply that callback.
    - **Problem:** `showAddNoteDialog` starts false and is only ever assigned false, so the entire
      dialog and its callback chain cannot be reached. A whole-tree reference search found no route,
      test, or other trigger that opens it.
    - **Fix:** Delete that dialog, `noteText`, and its callback parameters
      through [ReaderOverlay.kt:381](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/overlay/ReaderOverlay.kt#L381)
      and [AudioPlayerScreen.kt:137](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/AudioPlayerScreen.kt#L137),
      saving about **80–100 lines**. Keep the active audio add-note action at
      `AudioPlayerScreen.kt:86` and the reader's selection note editor.
    - **If we skip it:** An unreachable editor and its wiring remain part of every reader change.
      Existing note creation still works independently of this dead branch.

2. **Remove the reader's large, single-caller sheet forwarding layer**
   ([ReaderOverlay.kt:346](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/overlay/ReaderOverlay.kt#L346), [ReaderSheetsLayer:689](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/overlay/ReaderOverlay.kt#L689))
    - **What this is:** `ReaderOverlay` already collects the reader's state and defines its actions.
      It then forwards more than 60 arguments through `ReaderSheetsLayer` to the actual sheet
      components.
    - **Problem:** Changing one sheet action requires editing a callback, the forwarding call, the
      parameter list, and the child call in the same file. Whole-tree search found just one
      `ReaderSheetsLayer` caller and no independent test consumer.
    - **Fix:** Compose the existing sheet components directly at that call site and remove the
      intermediate signature, saving about **100–160 lines after finding 1**. Keep the same theme
      scope, composition order, remembered sheet state, visibility conditions, and
      jump-before-navigation actions.
    - **If we skip it:** Most new reader sheet controls require several copies of the same wiring.
      The extra layer continues to make ownership harder to follow.

3. **Share selection bookkeeping across library screens**
   ([LibraryCollectionScreen.kt:55](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/LibraryCollectionScreen.kt#L55), [LibraryScreen.kt:188](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/main/LibraryScreen.kt#L188), [FilterResultScreen.kt:198](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/filters/FilterResultScreen.kt#L198), [ShelfDetailScreen.kt:125](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/shelf/ShelfDetailScreen.kt#L125))
    - **What this is:** Each screen stores selected IDs and whether selection mode is active. It
      repeats enter, exit, toggle, clear, select-all, and Back handling; category lists repeat the
      pattern too.
    - **Problem:** For example, changing how Clear differs from Close requires updating several
      copies of the same set operations. The existing shared `BookActions` handles mutations, but
      these screens still duplicate the selection state around it.
    - **Fix:** Use one small remembered selection holder for those operations, saving about **70–120
      lines** without merging the screens. Preserve selection mode with zero selected items,
      separate book/shelf selections, and the immediate long-press selection in Unsorted/Archive
      versus context menus elsewhere.
    - **If we skip it:** Every selection behavior change must be repeated across screens. Small
      differences will continue to need separate fixes and tests.

4. **Reuse the inline editing buttons and animations**
   ([ShelfDetailScreen.kt:563](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/shelf/ShelfDetailScreen.kt#L563), [FilterResultScreen.kt:534](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/filters/FilterResultScreen.kt#L534))
    - **What this is:** Shelf and filtered-collection bars animate Back into Cancel and replace
      their actions with Save during editing. Both implement the same rotation, fading, sliding,
      colors, and Save button sizing.
    - **Problem:** Changing the editing transition or fixing its navigation icon requires modifying
      two substantial copies. Shelf reordering also uses that same visual editing state.
    - **Fix:** Extract only the common editing navigation button and action transition, saving about
      **60–90 lines**. Keep shelf reordering, subtitles, the shelf's single-line title, the filter's
      two-line title, and rename suggestions in their current callers.
    - **If we skip it:** The same animation and accessibility labels need maintenance twice. These
      bars can drift apart even though their shared editing controls look the same today.

5. **Build the common book filter sheet once**
   ([OptionsBottomSheet.kt:234](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/components/OptionsBottomSheet.kt#L234), [shelf sheet:327](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/components/OptionsBottomSheet.kt#L327), [result sheet:374](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/components/OptionsBottomSheet.kt#L374))
    - **What this is:** Library books, shelf detail, and filter results show layout, sort, status,
      and file-format sections. The project already shares the individual section components.
    - **Problem:** Shelf detail and filter results repeat almost the entire arrangement, and the
      library book tab repeats the same sections again. Adding a common book filter currently means
      editing all three arrangements.
    - **Fix:** Share the book-filter content and supply the allowed sort choices, saving about
      **40–70 lines**. Preserve Custom sort only for real shelves, its absence for
      Unshelved/results, and the library shelves tab's different layout and shelf filters.
    - **If we skip it:** New filter options continue to require parallel edits. The duplicated
      arrangement remains larger than the differences between these sheets.

6. **Use one signed change callback for numeric controls**
   ([ReaderSettingsContent.kt:418](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/ReaderSettingsContent.kt#L418), [IncrementDecrementControl.kt:36](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/IncrementDecrementControl.kt#L36), [FormatReaderSettings.kt:165](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/FormatReaderSettings.kt#L165))
    - **What this is:** Ten numeric controls supply separate increment and decrement callbacks. Each
      pair repeats the same setting copy, rounding, and bounds, with only the sign changed.
    - **Problem:** For example, letter spacing repeats its `0.025` step, three-decimal rounding, and
      `0.0..0.25` clamp in two callbacks. A range or step change must be made twice for each
      setting.
    - **Fix:** Change the existing control to report a direction of `-1` or `+1` and keep one update
      transform per setting, saving about **70–110 lines**. Retain every current step, bound,
      rounding rule, enabled state, display format, and button animation; check repeated steps and
      both endpoints.
    - **If we skip it:** Increment and decrement remain separate copies of the same rule. Future
      fixes can easily change only one direction.

7. **Reuse repeated settings rows within their existing visual styles**
   ([SettingsScreen.kt:578](../app/src/main/java/com/javierreansyah/pinecone/ui/features/settings/SettingsScreen.kt#L578), [ReaderSettingsContent.kt:904](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/ReaderSettingsContent.kt#L904))
    - **What this is:** App settings repeat segmented rows with a title, description, icon, and
      switch. Reader advanced settings repeat a plain choice row, open/close state, and
      `SettingsChoiceDialog` for dictionaries, orientation, and jump history.
    - **Problem:** Each additional switch or choice repeats its row structure and interaction
      wiring. Four app switches and three reader choices already maintain those structures
      separately.
    - **Fix:** Add a switch-item helper beside the existing `SettingsItem`, and a reader choice-row
      helper using the existing `SettingsChoiceDialog`, saving about **80–120 lines**. Keep the two
      visual styles, disabled states, whole-row clicks, empty dictionary message, and the inverted
      “hide All” switch mapping.
    - **If we skip it:** Row layout and dialog behavior continue to be copied for each setting.
      Changes to spacing or disabled behavior require several edits.

8. **Keep choice values separate from their displayed labels**
   ([SettingsItem.kt:17](../app/src/main/java/com/javierreansyah/pinecone/ui/features/settings/components/SettingsItem.kt#L17), [SegmentedButtonGroup.kt:22](../app/src/main/java/com/javierreansyah/pinecone/ui/components/SegmentedButtonGroup.kt#L22), [SettingsScreen.kt:530](../app/src/main/java/com/javierreansyah/pinecone/ui/features/settings/SettingsScreen.kt#L530))
    - **What this is:** Several callers turn stored values into translated strings, pass the strings
      through a control, then search the map backwards to recover the value. The existing
      `SettingsChoiceDialog` already accepts real option values and a separate label function.
    - **Problem:** Language, cover mode, theme, contrast, backup frequency, and several reader
      choices repeat this conversion. Selecting an option requires a reverse lookup even though the
      caller already has its real key.
    - **Fix:** Let the two existing controls accept option values and a label function, following
      `SettingsChoiceDialog`, saving about **20–35 lines** across callers. Preserve option ordering,
      all labels, unknown-value fallbacks, and the `in` to `id` locale compatibility rule.
    - **If we skip it:** Every new choice needs another pair of conversions. Display text continues
      to carry selection identity unnecessarily.

9. **Load font previews from the font files already shipped with the app**
   ([ReaderSettingsContent.kt:1100](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/ReaderSettingsContent.kt#L1100), [ReaderFontRegistry.kt:8](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/ReaderFontRegistry.kt#L8), [app/build.gradle.kts:193](../app/build.gradle.kts#L193))
    - **What this is:** EPUB reading uses five bundled font families from `assets/fonts`. Their
      small settings previews request the same families through Google Fonts instead.
    - **Problem:** The preview is the only production user of the Google Fonts API and its
      certificate resource. Maintaining a second font source brings provider setup, fallback
      handling, and a dependency for assets the app already has.
    - **Fix:** Use the bundled regular font faces for the previews, then remove the Google Fonts
      dependency, its catalog entry, and unreferenced certificates, saving about **10–20 Kotlin
      lines plus the certificate XML**. Compare glyph appearance and weight first, since provider
      and bundled font revisions may differ; retain the Literata offset and Source Code naming
      behavior.
    - **If we skip it:** The app keeps a second font-loading path solely for previews. This
      dependency removal remains conditional until the appearance comparison passes.

10. **Use the existing annotation header in book info**
    ([BookInfoScreen.kt:925](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/info/BookInfoScreen.kt#L925), [EntryHeader.kt:26](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/contents/EntryHeader.kt#L26))
- **What this is:** A note in book info displays its chapter, position, and a delete button.
  `EntryHeader` already renders that same row for the reader's annotation lists.
- **Problem:** Book info repeats the header's weighted text column, typography, spacing, two-line
  title, and 24 dp delete button. Updating the shared header does not update that copy.
- **Fix:** Call `EntryHeader` from the book-info note row, saving about **35–55 lines** including
  newly unnecessary imports. Keep book info's surrounding click area, quote marker, note typography,
  and lack of a creation timestamp.
- **If we skip it:** Identical note headers continue to have two implementations. The larger
  book-info screen keeps code that already exists elsewhere.

11. **Share annotation decoding and sorting without merging the entities**
    ([ReaderNotesViewModel.kt:80](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/ReaderNotesViewModel.kt#L80), [second sorting pipeline:102](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/ReaderNotesViewModel.kt#L102), [BookInfoScreen.kt:813](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/info/BookInfoScreen.kt#L813))
- **What this is:** Bookmarks and notes each decode their saved locator and sort by creation date or
  reading order. A locator is Readium's saved reading-position record.
- **Problem:** `sortedBookmarks` and `sortedNotes` repeat the same pipeline, while several consumers
  repeat the same JSON decoding and exception fallback. A malformed locator or equal-position
  sorting rule needs matching changes in multiple places.
- **Fix:** Keep one nullable decoder and one small sorting function with locator/date accessors,
  saving about **25–45 lines**. Preserve all four sort modes, invalid-locator placement, date
  tie-breaking, rendition filtering, and the separate bookmark/note data types; add a focused
  sorting regression test.
- **If we skip it:** The same ordering rule remains implemented twice and locator decoding stays
  scattered. A fix for one annotation type can miss the other.

12. **Merge the duplicate list/grid collection container**
    ([BookCollection.kt:74](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/components/book/BookCollection.kt#L74), [list container:126](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/components/book/BookCollection.kt#L126))
- **What this is:** Both `BookGrid` and `BookList` use `LazyVerticalGrid` and render the same
  `BookItem`. The list uses fixed columns while the cover grid uses adaptive columns.
- **Problem:** They duplicate dimensions lookup, padding application, item iteration, click
  handlers, long-press suppression, and selection wiring. A change to how selected books respond
  must be repeated in both containers.
- **Fix:** Compute the mode's columns, padding, item key, and item style, then use one grid
  container, saving about **30–50 lines**. Preserve every width breakpoint, list/grid key
  difference, content type, bottom-player padding, and the existing layout transition.
- **If we skip it:** The two containers continue to duplicate behavior despite using the same layout
  primitive. Tablet and phone behavior need parallel maintenance.

13. **Share screen preference wiring, leaving book sources separate**
    ([LibraryCollectionViewModel.kt:30](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/LibraryCollectionViewModel.kt#L30), [FilterResultViewModel.kt:69](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/filters/FilterResultViewModel.kt#L69), [ShelfDetailViewModel.kt:63](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/shelf/ShelfDetailViewModel.kt#L63))
- **What this is:** These view models observe preferences for a screen and implement layout, sort,
  format, and status updates. The actual transformation rules are already shared in
  `FilterSortPreferences.kt`.
- **Problem:** They still repeat preference collection, coroutine launching, and the same four
  forwarding methods. Adding a shared preference operation requires changing each view model even
  though persistence already belongs to `LibraryPreferencesManager`.
- **Fix:** Share only this screen-scoped preference wiring in one small helper using the existing
  manager and scope, saving about **30–50 lines**. Keep Archive/Unsorted's global preference scope,
  shelf/filter space scoping, screen keys, default values, subscription behavior, and separate book
  queries.
- **If we skip it:** Shared preference rules remain wrapped in repeated orchestration. The code is
  correct but each new setting needs several matching forwarding methods.

## Nice to have

14. **Merge the segmented-list interface with its only builder**
    ([SegmentedList.kt:44](../app/src/main/java/com/javierreansyah/pinecone/ui/components/SegmentedList.kt#L44), [builder:73](../app/src/main/java/com/javierreansyah/pinecone/ui/components/SegmentedList.kt#L73))

- **What this is:** `SegmentedListScope` declares `item` and `items`, and `SegmentedListBuilder`
  repeats both signatures to implement them. The scope is a small builder API used to describe
  grouped rows.
- **Problem:** Whole-tree search found exactly one implementation and no alternative test
  implementation. Adding a row capability requires changing both declarations even though callers
  always receive the same concrete builder.
- **Fix:** Use one builder class as the scope, moving the existing defaults onto its methods, saving
  about **25–40 lines**. Update the two settings receiver types and keep the inline composable
  builders, wrapper callbacks, row keys, and lazy rendering unchanged.
- **If we skip it:** The interface and implementation continue to mirror each other. No current
  feature breaks, but the extra declaration has no demonstrated second consumer.

15. **Format annotation timestamps once**
    ([BookmarksList.kt:60](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/contents/BookmarksList.kt#L60), [NotesList.kt:73](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/contents/NotesList.kt#L73))

- **What this is:** Both lists build the same creation date/time string and append it to the
  position label. They use an explicit English date pattern and Android's date/time format
  elsewhere.
- **Problem:** The entire date-formatting block and its imports are copied. Changing either date
  policy or subtitle formatting requires matching edits in both lists.
- **Fix:** Share the timestamp/subtitle formatter and keep each caller's current `remember`
  lifetime, saving about **15–25 lines**. Preserve the exact text and locale behavior in this
  refactor, including the current `created` prefix; test English and Indonesian output.
- **If we skip it:** Identical date behavior continues to be maintained twice. There is no immediate
  user-facing failure from leaving the duplication.

16. **Reuse the font and theme swatches' frame**
    ([ReaderSettingsContent.kt:1129](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/ReaderSettingsContent.kt#L1129), [ThemeSwatch.kt:47](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/settings/ThemeSwatch.kt#L47))

- **What this is:** Both swatches draw a 68 dp circular preview, selected ring, inner border, and
  two-line label. Their content is different: font glyphs versus colors or theme artwork.
- **Problem:** The same ring, padding, label width, and typography are implemented twice. A size or
  selection-ring adjustment requires changing both copies.
- **Fix:** Keep one frame with content supplied by the current swatches, saving about **25–40
  lines**, independently of the font-loading change in finding 9. Retain different backgrounds,
  long-press support for custom themes, and the font-specific vertical offset.
- **If we skip it:** The two previews continue to duplicate their visual frame. Future adjustments
  can make their alignment inconsistent.

17. **Render the contents sheet's fixed options from values**
    ([ContentsBottomSheet.kt:118](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/ContentsBottomSheet.kt#L118), [sort menu:223](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/ContentsBottomSheet.kt#L223))

- **What this is:** Three tabs and four sorting choices each repeat an almost identical component
  call. Their meaningful differences are the title, target page, or `SortOption` value.
- **Problem:** The same selection comparison and close-menu behavior are written separately for
  every option. Adding an option repeats structural code instead of supplying another value.
- **Fix:** Loop over an explicit ordered list of tab titles and sort-value/label pairs, saving about
  **45–70 lines**. Preserve the current menu order, item shapes, selected state, pager animation,
  and initial-tab clamping; no new menu framework is needed.
- **If we skip it:** A small fixed menu remains much longer than the choices it describes. Each
  option still owns a copy of the common interaction.

18. **Call the shared HTML view directly** (`DefinitionWebView.kt:9`
    (removed), [ReaderOverlay.kt:949](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/components/overlay/ReaderOverlay.kt#L949))

- **What this is:** `DefinitionWebView` forwards its four arguments directly to `HtmlWebView`.
  Whole-tree search found one call, in the dictionary sheet.
- **Problem:** The wrapper adds no dictionary formatting, lifecycle management, or validation.
  Changes to the shared view's parameters can require another forwarding edit here.
- **Fix:** Replace that call with `HtmlWebView` and delete the wrapper file, saving about **15–25
  lines**. Pass the same word-click callback and font size, and retain both dictionary formatting
  and `ImportedHtml` sanitization.
- **If we skip it:** A redundant forwarding component stays in the rendering chain. This is a small
  cleanup with no intended visual effect.

19. **Reuse author/tag identity lookup during imports and metadata edits**
    ([LibraryRepository.kt:223](../data/src/main/java/com/javierreansyah/pinecone/data/repository/library/LibraryRepository.kt#L223), [PublicationRepository.kt:190](../data/src/main/java/com/javierreansyah/pinecone/data/repository/library/PublicationRepository.kt#L190))

- **What this is:** Both write paths insert an author or tag, handle the already-existing case, and
  create the book relationship. An ignored insert returns a sentinel ID, so each caller looks up the
  existing row.
- **Problem:** Importing a second book by the same author and saving that author in the metadata
  editor execute copies of the same identity-resolution sequence. Both repositories must maintain
  that insert-or-lookup rule.
- **Fix:** Put the small author and tag identity helpers beside the existing DAO methods, saving
  about **15–30 lines**. Leave trimming, author order, missing-row handling, relationship
  replacement, orphan cleanup, and transaction ownership at their current callers; keep rollback
  tests.
- **If we skip it:** Two repositories continue to own the same persistence mechanism. Future changes
  need matching fixes in both paths.

20. **Share the page/percentage label formatter**
    ([ReaderViewModel.kt:427](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/ReaderViewModel.kt#L427), [ReaderSearchViewModel.kt:103](../app/src/main/java/com/javierreansyah/pinecone/ui/features/reader/ReaderSearchViewModel.kt#L103), [BookInfoScreen.kt:1640](../app/src/main/java/com/javierreansyah/pinecone/ui/features/library/info/BookInfoScreen.kt#L1640))

- **What this is:** These paths turn an already-resolved page number into a localized page label,
  falling back to total progress as a percentage. Position lookup itself is already shared in
  `PositionUtils.kt`.
- **Problem:** The page-first, percentage-second, empty-last formatting rule is still repeated. A
  wording or rounding adjustment needs matching edits in the reader, search, and book info.
- **Fix:** Share just that resource-based formatter, saving about **15–25 lines**. Keep each
  caller's page resolver, the reader's read-mode page-count bound, audio's percentage-only behavior,
  and existing plural/rounding rules.
- **If we skip it:** The same label policy continues to have several implementations. There is no
  reason to replace the existing position algorithms to solve this duplication.

## Savings and implementation order

The estimates below count separate code regions. In particular, finding 2 excludes finding 1's
deleted callbacks; finding 3 counts selection bookkeeping, not unrelated screen forwarding; finding
16 excludes font loading; and finding 20 excludes locator decoding.

| Finding                         | Estimated net source lines removed | Main check before accepting                                  |
|---------------------------------|-----------------------------------:|--------------------------------------------------------------|
| 1. Unreachable note dialog      |                             80–100 | Audio and selection-based note creation still work           |
| 2. Reader sheet forwarding      |                            100–160 | Sheet state, Back, navigation, and theme behavior            |
| 3. Selection bookkeeping        |                             70–120 | Zero selection, Clear/Close, long press, selection ownership |
| 4. Inline editing chrome        |                              60–90 | Same focus, animations, rename, and reorder controls         |
| 5. Filter sheet content         |                              40–70 | Per-screen choices, Custom sort, empty filters               |
| 6. Numeric change callbacks     |                             70–110 | Steps, endpoints, rounding, and disabled controls            |
| 7. Settings rows                |                             80–120 | Click areas, dialogs, descriptions, disabled switches        |
| 8. Choice values and labels     |                              20–35 | Labels, persisted keys, and fallback values                  |
| 9. Bundled font previews        |                              10–20 | Font appearance and weight match; one dependency candidate   |
| 10. Annotation header           |                              35–55 | Book-info row appearance and deletion                        |
| 11. Annotation decoding/sorting |                              25–45 | Invalid JSON and all sorting/tie cases                       |
| 12. Collection container        |                              30–50 | Width breakpoints, keys, padding, layout transitions         |
| 13. Preference wiring           |                              30–50 | Screen/space isolation and subscription/default behavior     |
| 14. Segmented-list builder      |                              25–40 | Keys, wrappers, recomposition, and lazy rows                 |
| 15. Annotation timestamps       |                              15–25 | English/Indonesian and device time-format preferences        |
| 16. Swatch frame                |                              25–40 | Rings, offsets, backgrounds, long press                      |
| 17. Fixed menu/tab options      |                              45–70 | Exact ordering, shapes, and initial tab                      |
| 18. HTML forwarding wrapper     |                              15–25 | Dictionary rendering and word clicks                         |
| 19. Author/tag identity helpers |                              15–30 | Existing identities, author order, rollback                  |
| 20. Position label formatter    |                              15–25 | Page/percentage fallbacks and plural strings                 |
| **Total**                       |                      **805–1,280** | **Projections, not completed changes**                       |

Start with **1, 10, 18, and 17**: they have clear deletion or reuse boundaries. Then tackle **5, 6,
4, and 7**, which offer useful savings inside existing UI components. Keep **2, 3, and 13** as
separate changes because composition lifetime, selection ownership, and preference scoping deserve
focused review. Treat **9** as conditional on matching font appearance.

For each implementation, run the repository's Quick command. When changing UI behavior or layout,
also exercise the affected flows using the required **Resizable (Experimental)** emulator profile.
Existing focused checks include `UnsortedInteractionTest`, `MultiSelectAppBarTest`,
`BookInfoShelvesTest`, `LibraryCommonTest`, `FilterSortPreferencesTest`, `PositionUtilsTest`,
`ReadingPreferenceOverridesTest`, `ImportedHtmlTest`, `BookMutationTest`, and
`LibraryProcessingTest`; add a focused regression check when extracted logic has no suitable
existing case.

## Areas reviewed that should keep their current boundaries

- **Import and conversion durability:** Traced the UI request through `MainViewModel`,
  `PublicationOperations`, the worker/scheduler, staging, publication creation, and the database
  commit. Queued records, generation/attempt checks, cancellation handling, and recovery are doing
  different jobs; replacing them with a screen coroutine would change behavior after process death.
- **Deletion, restore, and playback coordination:** `DeleteBooks` and `RestoreBackup` are short but
  stop playback before changing assets. They are useful coordination points, not empty forwarding
  wrappers like finding 18.
- **File ownership:** `PublicationAssetStore`, leased publications, and delayed deletion protect
  files still open in a reader or backup. Removing these checks to shorten repositories would risk
  data loss or broken reading sessions.
- **Backup/restore:** Read `BackupRepository` end to end, including validation, partial-file
  publication, object checks, retention, staged restore, pending settings recovery, and dictionary
  rollback. Its 1,186 lines contain several different safety obligations; no behavior-preserving
  bulk deletion was established, and merely splitting it into files would not reduce the work.
- **Backup records and shared models:** Similar database, UI, and backup fields represent different
  contracts and defaults. Removing backup record mappings would couple the saved format to database
  changes; the duplicated fields alone do not justify that change.
- **Reader versus audio service:** Visual navigation and background playback have different owners
  and lifetimes. Keep the playback service/client/engine split and session checks; do not merge the
  audio and visual reader view models merely because both expose settings and navigation.
- **Preferences and migrations:** Legacy readers, current serializers, migration markers, and the
  special disabled override value `{}` preserve existing installations. Replacing them with defaults
  or deleting the old readers would change stored behavior.
- **PDF reflow and native integration:** Reviewed the conversion pipeline and sampled the cleaner,
  structure extraction, resource writing, location mapping, and native rendering/selection code. The
  large cleaner has format-specific handling for tables, equations, captions, lists, and figures;
  removing those branches would need equivalent output demonstrated against fixtures and real PDFs.
- **Existing reuse:** Keep `BookActions`, `MutationErrorState`, `BookCollection`/`BookItem`,
  `CoverImage`, `SettingsChoiceDialog`, `PositionUtils`, the format preference mappers, and the
  ordered page pipeline. Build the proposed cuts on these pieces rather than introducing another UI
  or repository framework.
- **Build setup and dependencies:** Read the four module builds, version catalog, manifest, and
  entry-point setup; no CI/deployment workflow directory was present. No second dependency removal
  was established: publication engines, HTML cleaning, media playback, image loading, theming,
  icons, and reordering all have current feature uses.

## Original audit validation and limits

Executed from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
```

**Result: BUILD SUCCESSFUL.** Gradle reported 111 actionable tasks, with 7 executed and 104 up to
date. The existing test reports for the selected tasks contain **213 tests, zero
failures/errors/skips** across app, data, publication, and core/model; test task results were
reused, not freshly rerun. Quick does not run lint, device tests, or IDE inspections, and this
result does not establish that all warnings are cleared.

The audit inventoried production sources across every module, searched for duplicated blocks and
declaration references, and checked the call chains for the reported findings. It read the main
storage flows deeply and inspected representative tests; it was not a line-by-line review of every
source or test body. No additional confirmed findings were left out of the report; rejected
candidates are not counted as savings.

**Original audit verdict:** The app already has useful shared components and storage boundaries;
delete the unreachable dialog first, then reduce repeated UI wiring and rendering in small changes.

**Original estimate: -805 to -1,280 source lines, -1 dependency possible.** Estimates include
replacement helpers, exclude new tests and resource savings, and depend on preserving the behavior
checks above.

**Not checked during the original audit:** Live UI/animation equivalence, device instrumentation,
fresh execution of cached unit tests, lint, IDE inspections, release/staging builds, real-device
playback/process-death trials, large-library timings, every PDF reflow heuristic, third-party
dependency internals, or every source/test body. This paragraph records the audit-only baseline; see
Implementation status above for the subsequent changes and validation.
