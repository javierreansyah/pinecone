# Architecture refactor: measurements and remaining validation

Recorded 2026-10-26. Read with [the implementation log](ARCHITECTURE_REFACTOR_PLAN.md).
These are debug-build measurements on **Resizable (Experimental), API 34**, emulator-5554.
They establish reproducible checks and a local baseline, not physical-device production latency.

## Results

### Library query and mapping

`LibraryProcessingTest.selectedShelfMaterializesOnlyItsMembersAndRecordsQueryMappingTimes`
inserts 1,000 synthetic books and a 20-member shelf into in-memory Room. It measures one complete
query/relationship materialization plus `Book.fromEntity` mapping per sample: two warmups, then
five measured samples per path. Full-library then selected-shelf order is fixed; there are no
latency thresholds or assertions that a given machine must be faster.

| Run | Full 1,000-book median | Selected 20-book median |
| --- | ---: | ---: |
| First | 161.5807 ms | 12.6465 ms |
| Final data-suite rerun | 133.1780 ms | 11.3711 ms |

The selected query returned exactly 20 models, versus 1,000 for the broad query. This supports
querying only the selected shelf. It is a same-build comparison of two query paths, **not** a
before/after app benchmark. It does not measure disk-backed startup, Compose frames, sorting,
image decoding, SQL event counts or allocations. Additional tests verify scoped unshelved/inbox
semantics and 1,001-member transactional/idempotent organization.

### Native conversion

`PdfConversionBenchmarkTest.syntheticConversionBenchmark` generates a locally authored 100-page
text PDF. Pass 0 warms JVM/native caches; passes 1–3 are measured. Every pass opens a new native
document. Output entries and source-map content are SHA-256 compared, excluding the required
EPUB modification timestamp from the OPF comparison.

| Pass | Extraction | Total conversion | EPUB bytes |
| --- | ---: | ---: | ---: |
| Warmup | 1,861 ms | 2,712 ms | 6,773 |
| 1 | 941 ms | 1,534 ms | 6,773 |
| 2 | 780 ms | 1,293 ms | 6,773 |
| 3 | 637 ms | 1,092 ms | 6,773 |

Measured median total: **1,293 ms**. Stable content/source-map digest for every pass:
`298ac8bb0452c98558aa9de2d571a349149ce32ae9d685aa93115b215eb0bce4`.

This verifies repeatability for a simple fixture. The continuing timing decrease shows warmup
effects; do not infer a universal throughput or a refactor speedup. Converter version remains 20.
No pre-refactor build was timed with this fixture. Image-heavy, scanned, table-heavy, multilingual
and long research/thesis PDFs still require representative inputs and output review.

### Native reader operations

`syntheticReaderOperationsBenchmark` uses the same 100-page fixture and four fresh-document passes.
Render/text order is pages **1, 100, 50, 1**, exercising distant access and backtracking. Rendering
uses width 600; bitmaps are recycled after each page. Search visits every page and verifies exactly
100 occurrences of `readable`. Values are aggregate operation times, excluding Compose/navigation.

| Pass | Open | Four renders | Four text extractions | Search all 100 pages |
| --- | ---: | ---: | ---: | ---: |
| Warmup | 52.197 ms | 35.773 ms | 10.848 ms | 133.558 ms |
| 1 | 0.434 ms | 18.868 ms | 3.070 ms | 122.768 ms |
| 2 | 0.265 ms | 39.152 ms | 2.896 ms | 120.770 ms |
| 3 | 0.260 ms | 31.492 ms | 3.094 ms | 113.504 ms |

Largest individual bitmap allocation: **1,862,400 bytes**. This is bitmap storage reported by the
returned bitmap, not peak process memory, the native heap, retained search-result memory, or a
cover-cache measurement. Rendering jitter and synthetic content do not justify new caches or
concurrent native document access. Preserve the existing synchronization.

## Validation evidence

| Check | Executed result |
| --- | --- |
| Quick JVM reports | 209 tests: app 95, data 21, publication 83, core model 10; zero failures/errors |
| Full data instrumentation | 17 tests, zero failures/errors/skips |
| App targeted instrumentation | 15 passes: DI 2, playback 9, actual worker 1, book mutation 3 |
| Conversion regression class | 8 passes; 2 external-fixture assumption exits, not executed audits |
| Benchmark class | 2 synthetic passes; 1 external benchmark assumption exit |
| Recovery fixture | Seed and verification passed; repeated after final UI build |
| Organizer manual smoke | Open, dismiss, reopen identical selection, back; sheet rendered successfully |

Data operation tests cover transient input staging with source removal, interruption after one
committed item, restart without duplication, cancel/restore fencing, deletion tombstones, asset
leases, preparation failure names, superseded attempt rejection, and background activation mapped
to a position advanced during conversion. The folder test imports a nested EPUB through a real SAF
provider, shares a newly acquired persistent grant across two requests, releases it only after the
last terminal owner, deduplicates the EPUB, and preserves a preexisting persistent user grant.

Unchanged JVM suites may be up-to-date in later Quick runs. Device tests above were executed on the
emulator. Connected XML on this toolchain records JUnit assumptions as failures even when Gradle
succeeds. Verify test count and exception type. Never report optional fixture audits as passes.

## Reproduction

From the repository root in PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
.\gradlew.bat :data:connectedDebugAndroidTest --warning-mode all
.\gradlew.bat :publication:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.javierreansyah.pinecone.data.publication.conversion.PdfConversionBenchmarkTest' --warning-mode all
.\gradlew.bat :publication:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.javierreansyah.pinecone.data.publication.conversion.PdfReadModeConverterTest' --warning-mode all
```

Only Resizable (Experimental) should be running. Per-test logcat under
`<module>/build/outputs/androidTest-results/connected/debug/` contains `PINECONE_BENCHMARK`,
`PDF_BENCHMARK`, and `PDF_READER_BENCHMARK` records. These build outputs are overwritten on later
runs; this document preserves the observations. JVM counts must use the exact `testDebugUnitTest`
report directory (`test` for core model), excluding stale reports for other variants.

For app regressions, build/install both the debug and debug-androidTest APKs, then run the installed
test runner directly. This command executed all four classes; check the final `OK (15 tests)`:

```powershell
adb shell am instrument -w -r -e class 'com.javierreansyah.pinecone.DependencyInjectionTest,com.javierreansyah.pinecone.AudioPlaybackIntegrationTest,com.javierreansyah.pinecone.PublicationWorkIntegrationTest,com.javierreansyah.pinecone.data.local.database.library.BookMutationTest' com.javierreansyah.pinecone.dev.test/androidx.test.runner.AndroidJUnitRunner
```

`adb` may require the absolute Android SDK platform-tools path on this machine. Debug app output
is currently `app/build/outputs/apk/debug/pinecone-1.0-debug.apk`; prefer output-metadata.json over
hardcoding this versioned filename in scripts. The Hilt debug entry point lives only in `src/debug`.

### Database-to-scheduler recovery check

`PublicationRecoveryFixture` has two opt-in steps and skips by default. Seed explicitly inserts a
QUEUED conversion after importing a synthetic 25-page PDF, but before enqueueing WorkManager.
This creates the database-to-scheduler crash window without timing a native call. It writes IDs to
`files/durable-recovery-fixture.json`, syncing that file. Seed input cache is deleted afterward.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.javierreansyah.pinecone.PublicationRecoveryFixture#seedAcceptedRequestBeforeScheduling' '-Pandroid.testInstrumentationRunnerArguments.seedDurableRecovery=true' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --warning-mode all
adb shell pidof com.javierreansyah.pinecone.dev
adb shell am start -n com.javierreansyah.pinecone.dev/com.javierreansyah.pinecone.MainActivity
adb shell am instrument -w -r -e class 'com.javierreansyah.pinecone.PublicationRecoveryFixture#verifyRecoveredRequest' -e verifyDurableRecovery true com.javierreansyah.pinecone.dev.test/androidx.test.runner.AndroidJUnitRunner
```

AGP normally uninstalls both APKs after connected tests; preserve them for the two-step check.
If the seed instrumentation process already exited, record that accurately; do not claim a live
PID was killed. Do not use force-stop to model ordinary process death. Verification waits for a
terminal result, asserts SUCCEEDED/read-mode activation/output existence, and deletes its book
and fixture marker. An emulator restart between steps was also verified successfully. Never
uninstall, clear app data, or run another uninstalling connected app suite between the two steps.

## Open acceptance and profiling work

These are remaining validation tasks, not reasons to rebuild the implemented framework:

1. On an actual provider, revoke access after a request is accepted, remove/rename a scanned folder,
   and test providers that return null streams. Confirm INPUT_REQUIRED and clear reselection UI.
   Current tests cover missing file/preparation errors and real grant sharing, not every provider.
2. Exercise navigation away and Activity recreation during multi-item import and a long conversion;
   kill an ordinary background process during native extraction and during staging. Verify eventual
   recovery without duplicate commits and without loss of the previous rendition. Existing checks
   cover no-Activity worker execution, coroutine interruption, stale attempts and insert/enqueue gaps.
3. Measure disk-backed cold/warm library startup, search, sorting and shelf scrolling with small,
   medium and large libraries. Record SQL query events separately from mapping time and Compose
   frame duration. Compare identical fixture data/build settings on an isolated baseline checkout.
4. Capture allocation/native-heap/cover-cache peaks and retained organizer stores across repeated
   open/dismiss/recreate cycles. A successful smoke test is not a leak measurement. Test constrained
   cover sizing on grid/list/shelf layouts and change cover mode while prefetch is active.
5. Run the opt-in native conversion benchmark on representative text, scanned, image/table-heavy,
   multilingual and research PDFs. Supply benchmarkPath/benchmarkPasses/benchmarkLabel arguments;
   compare output/source-map digests and visual semantics across builds, not time alone.
6. Trace PDF open, distant jumps, backtracking, accumulated search, rendering and text selection in
   the actual reader UI. Synthetic native timings exclude UI and do not justify a layout cache,
   display-list cache, streaming-search redesign or parallel MuPDF calls.
7. Validate foreground-work restrictions and quotas on relevant newer Android versions/physical
   devices, including notification denial, retry, explicit cancel, delete and restore overlap.
   API 34 cannot establish behavior for every supported OS version or manufacturer.

Keep WP8's broad profiling gates open until these measurements exist. No extra native/cache
optimization was added from synthetic timing. Quick excludes lint, IDE inspections and Complete.
Known AGP/Gradle deprecation and AboutLibraries configuration-time classpath notices remain;
do not silence them and call the build warning-free. Follow repository instructions if Complete
validation is explicitly requested later.
