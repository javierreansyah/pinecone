# Dependency decisions

The dependency refactor keeps existing reader behavior and backup formats. Hilt owns shared services
and constructs ViewModels and workers; JSON DataStore owns settings. Imported dictionary and
description HTML is parsed and cleaned by jsoup before the app adds its own styles or scripts.

## Ownership and persistence

- Hilt's singleton component owns the database, repositories, preference managers, Readium services,
  and audio session. ViewModels keep their existing activity/navigation-entry lifetimes. Assisted
  factories accept publication IDs and organization selections, not service dependencies.
- The library repository receives a lazy audio-session dependency so constructing it does not
  recursively construct the audio session that uses it. Neither class retrieves services from the
  application.
- WorkManager uses `HiltWorkerFactory` through on-demand initialization. Its default Startup
  initializer is removed from the manifest.
- Reader and library settings migrate independently into typed JSON DataStores. Migration markers
  are stored with the new data; the original preferences remain read-only for recovery. Migration
  failures propagate, allowing a later initialization to retry. Malformed individual legacy records
  use the documented defaults instead of aborting otherwise valid settings.
- Incremental settings edits use DataStore transforms against the latest value. Complete settings
  replacement is reserved for backup restoration. Backup JSON still contains `ReaderSettings`, not
  the new storage envelope, so storage migration does not change the portable backup format.

## Retained custom implementations

| Candidate                      | Evidence and decision                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Navigation 3 `NavDisplay`      | The standard display supports configurable predictive transitions. Pinecone additionally separates gesture preview from a committed exit flight, delays the pop and ViewModel disposal until that flight finishes, preserves outgoing-screen layering during consecutive pops, and lets local Back handlers win. `ScreenNavDisplayTest` covers these requirements. Retain the custom display and Navigation 3 entry decorators; a default display is not a demonstrated equivalent.                                                                                                |
| AndroidX PDF                   | The reviewed release is `1.0.0-beta01`; Compose viewing and some annotation APIs remain experimental. Pinecone integrates MuPDF selection/search coordinates, Readium locators, persisted notes, layout preferences, and PDF-to-EPUB source mapping. Replacing the viewer still requires those adapters and does not replace the conversion engine. Retain the current viewer until equivalent behavior is demonstrated across supported devices and stored publications.                                                                                                          |
| Paging                         | Library screens support whole-collection counts, selection, filtering, custom shelf ordering, and reordering. Adding Paging without moving these operations to appropriate queries would add complexity without eliminating the existing collection logic. Keep complete-list semantics and preserve metadata-specific sorting. The primary library's space/inbox filtering and its unsorted-item existence check now run in Room, avoiding materializing excluded books. `VisibleBooksQueryTest` compares the query with existing filtering and every sort mode on 5,000 records. |
| EPUB/PDF conversion and backup | Page anchors, reconstruction rules, incremental snapshots, asset recovery, and restore transactions are app behavior. Standard ZIP, XML, PDF, and database libraries already supply the underlying mechanisms; an additional generic library does not replace these rules.                                                                                                                                                                                                                                                                                                         |

## Dependency analysis

Run the advisory report explicitly:

```powershell
.\gradlew.bat :app:projectHealth '-PanalyzeDependencies=true' --warning-mode all
```

The plugin's published tested AGP range ends at 9.3.1; this project uses 9.4.1. Its report was
exercised on this project, but it stays opt-in so routine builds do not apply an unsupported
integration. Do not run automatic dependency fixes without reviewing the advice.

The reviewed advice replaces empty KTX compatibility artifacts with their current base artifacts,
declares directly used APIs, removes unused preview/Room KTX dependencies, replaces unused
AboutLibraries Compose UI with its core library, and uses Navigation 3 runtime directly. The report
can still suggest splitting supported aggregate dependencies into implementation artifacts. Keep the
Material Symbols and Reorderable published entry points, which select debug and release variants
correctly; do not hard-code their internal variant artifacts. Keep coroutine Android support for the
Main dispatcher even though its registration occurs through a runtime service loader. Source
inspection and build/runtime validation take precedence over mechanical advice.

## References

- [Hilt Gradle/KSP setup](https://dagger.dev/hilt/gradle-setup)
- [Hilt assisted ViewModels](https://dagger.dev/hilt/view-model)
- [DataStore migration and typed storage](https://developer.android.com/topic/libraries/architecture/datastore)
- [jsoup safelist cleaning](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer)
- [Navigation 3 transitions](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)
- [AndroidX PDF release notes](https://developer.android.com/jetpack/androidx/releases/pdf)
- [Paging overview](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
