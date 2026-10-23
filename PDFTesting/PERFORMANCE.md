# PDF conversion performance

Measured on 2026-10-21 against the original converter from commit `ed1194a`. Both builds used the
same Android 14 x86_64 emulator with four reported processors and the debug build configuration.
Each input/build combination ran once for warmup, followed by two measured conversions. Times cover
`convert()`, including extraction, intermediate records, cleanup, EPUB packaging, and source-map
creation. Output hashing and test assertions after conversion are outside the timing.

## Results

| Input         | Pages | Original mean | Optimized mean | Speedup |
|---------------|------:|--------------:|---------------:|--------:|
| Attention.pdf |    15 |      24.423 s |        6.051 s |   4.04x |
| Skripsi.pdf   |   173 |      58.291 s |       29.445 s |   1.98x |

Measured runs, in milliseconds:

| Input         | Original     | Optimized    |
|---------------|--------------|--------------|
| Attention.pdf | 24777, 24069 | 6466, 5636   |
| Skripsi.pdf   | 57813, 58768 | 29475, 29415 |

The first conversions also improved: 35.701 s to 13.703 s for Attention.pdf, and 74.360 s to 39.802
s for Skripsi.pdf. Emulator measurements are specific to this environment; device CPU, memory,
storage, and PDF contents affect timings.

Every generated ZIP entry and the returned source map had matching SHA-256 hashes between both
builds and across all three runs. Only the EPUB modification-time metadata was normalized before
hashing. The combined fingerprints were:

- Attention.pdf: `4e09f4b5ea187907448928792b2a9d936a483aca3b265ce0b5b21ddadb74466a`
- Skripsi.pdf: `79972b71a6b3d95d414ec4ade175331cc65e2f46595d11fb98aa595028eb5491`

## Pipeline changes

- Prepare up to two pages concurrently, falling back to one on a single-core device. Load pages and
  create display lists under the document lock, then replay each worker's independent display list
  outside that lock. MuPDF's JNI uses
  [thread-local contexts](https://github.com/ArtifexSoftware/mupdf/blob/1.28.5/platform/java/jni/context.c).
- Keep document-wide profiling and cleanup/writing in source-page order, retaining margin
  statistics, bibliography continuations, asset names, and source anchors.
- Prefetch and decode bounded page records while cleanup/writing processes the preceding page.
  Intermediate record reads/writes use the IO dispatcher.
- Reuse parsing regexes, font-size counts, normalized block text, and page lookups. Walk DOM
  siblings directly instead of repeatedly indexing child-node lists.
- Join all preparation workers before deleting temporary records on cancellation or failure. Native
  extraction options and converter version remain unchanged.

## Repeating a measurement

Build the debug app and instrumentation APK, install them on the chosen device, and push a PDF to a
path readable by the test process. Run:

```powershell
adb -s emulator-5554 shell am instrument -w -r `
  -e class com.javierreansyah.pinecone.data.publication.conversion.PdfConversionBenchmarkTest `
  -e benchmarkPath /data/local/tmp/Attention.pdf `
  -e benchmarkPasses 2 -e benchmarkLabel current `
  com.javierreansyah.pinecone.dev.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 logcat -d -s PdfConversionBenchmark:I '*:S'
```

`PDF_BENCHMARK` lines report phase timings and fingerprints. Pass zero is warmup. The optional
benchmark checks sequential progress and identical output across repetitions, without imposing
timing thresholds. The eight native conversion regressions also passed with both local PDF fixtures
enabled, and Quick passed 104 unit tests, including preparation order, concurrency, cancellation,
and failure cleanup checks.
