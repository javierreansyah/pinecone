# Repository structure and conventions

`app/` contains the Android application, JVM tests (`src/test`), device tests (`src/androidTest`),
and Room schema history (`schemas`). `gradle/` contains the wrapper and dependency catalog.
`Screenshots/` contains images used by the README. Supporting development documentation lives in
`docs/`; `README.md`, `AGENTS.md`, and the application license notices stay at the root.

## String resources

Use lower snake case with a feature prefix followed by its purpose, such as
`reader_conversion_failed` or `settings_library_bottom_navigation_summary`. Keep shared actions
under `action_*` and shared messages under `common_*` in
`strings.xml`. Other UI strings belong in the corresponding feature file, such as
`reader_strings.xml`, `library_strings.xml`, or `settings_strings.xml`.

Use `publication_*` for metadata and actions that apply to multiple formats.
`content_strings.xml` holds format-sensitive vocabulary selected by
`PublicationWording`; book-specific wording belongs there. Keep distinct semantic resources even
when their current English text matches: translations may differ by context. Use aliases when the
resources intentionally share the same meaning.

Keep English (`values`) and Indonesian (`values-in`) keys and resource types in sync. Use plurals
for counts and preserve format arguments and Android escaping. When renaming an ID, update Kotlin
references and XML aliases together. UI wording and translation coverage are checked by JVM tests.

## Library state

`FilterSortPreferences` describes layout, sorting, and selected filters. Its shared transition
functions define sort-direction defaults and filter toggles; view models retain responsibility for
storing preferences under their screen/space keys. Archive and Unsorted use
`LibraryCollectionViewModel` for their common state and actions, with separate repository sources
and persistence keys.

Prefer named model properties over long positional destructuring and typed flow composition over
heterogeneous arrays with unchecked casts.

## Local files and validation

Generated build output, Kotlin caches, and root diagnostic logs are ignored. Keep external PDF audit
inputs outside the repository and pass device-readable paths to the optional instrumentation tests
as described in [PDF read mode](PDF_READ_MODE.md)
and [PDF performance](PDF_PERFORMANCE.md). Synthetic and JVM fixtures remain in the test source
sets.

Run Gradle from the repository root. See [validation](VALIDATION.md) for Quick and Complete commands
and their scope. Complete covers builds, JVM tests, and Android lint for debug, release, and
staging; device tests and IDE inspections are separate.
