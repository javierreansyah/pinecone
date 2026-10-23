# PDF read mode

The production converter uses MuPDF 1.28.5's native document writers. It does not run a desktop
`mutool` process or use a server. `PdfReadModeConverter` orchestrates these steps on the device:

1. `MuPdfPageExporter` loads one native display list per extraction page. It exports semantic XHTML
   and detailed structured-text XML with structure collection, segmentation, paragraph breaking,
   dehyphenation, style collection and accurate glyph bounds. A separate export enables native table
   hunting. This keeps false table detections from changing the main reading order. Native vectors
   stay in MuPDF; the unsafe Java structured-text vector callback is not used.
2. `MuPdfStructure` reads the exported geometry and native table cells. It pairs tables with HTML by
   text content, ignoring empty tagged shells left by native table hunting, and preserves header
   cells. Page records are written to temporary files. Only bounded font/margin statistics are
   retained across the first pass, rather than keeping the document's glyphs in memory.
3. `MuPdfReflowCleaner` adapts the native XHTML for a reflowable reader: consistent headings, joined
   number/title fragments, list markers and continuations, bibliography entries,
   superscripts/subscripts and repeated margin text. It distinguishes captions from inline
   references using nearby visual regions, rejoins font changes on the same source line, and rejects
   obvious table false positives. Actual ruling paths allow long descriptions and sparse cells in
   tables. Tables absent from the base XHTML are inserted by their source geometry. Native nested
   cell wrappers are normalized to valid HTML. Wrapped cell bands with no shared ruling line are
   joined again in ruled tables. When MuPDF combines synchronized unruled table lines into
   super-cells, cleanup splits those native lines into rows while keeping MuPDF's columns and cell
   spans. Ambiguous tables remain native text.
4. Visual regions are exported through MuPDF's SVG document writer with `text=path`. Native
   display-list clipping selects the region. Original paths, glyph outlines, raster images, masks,
   colors and clipping remain together. Charts are standalone SVG assets, including their labels;
   their primitives are not reconstructed as separate HTML elements. Region selection uses native
   figure tags/segmentation when available. Untagged drawings still require conservative region
   grouping. Equations use the same native SVG writer, embedded inline with isolated IDs and
   text-color adaptation. Simple inline scripts remain selectable text. No formulas are redrawn
   using substitute fonts or hand-built radical paths. If a font has unmapped Unicode glyphs, the
   affected paragraph/table retains its original layout in native SVG; that fallback is not fully
   reflowable or text-selectable.
5. `EpubWriter.nativePage` buffers one source-page section so bibliography continuations can join
   across page boundaries, then streams completed sections through `EpubPageChunks`. Up to 25 source
   PDF pages remain in one file. Longer documents split after pages 25, 50, 75, etc., at the next
   safe source-page boundary. Paragraphs, tables, equations, lists and figures stay whole. A heading
   with following text, a figure/table with its caption, or a continuing list across a proposed
   boundary delays the cut to the next safe page boundary. There is no complexity score or inferred
   chapter boundary. The first file remains
   `text/content.xhtml`; later files are `text/content-0002.xhtml`, etc. Completed resources spool
   to disk; links are rewritten one resource at a time after all anchor destinations are known.
6. Navigation uses the original PDF outline. An outline title matching a heading on its source page
   links directly to that heading; otherwise it links to the source page. PDFs without an outline
   use a source-page list. Heading styles do not create inferred chapters or determine file
   boundaries.
7. Source-page, block and run IDs remain stable. Reparse migrates bookmarks, notes and reading
   positions, including locations saved in older split renditions. Native handles and temporary
   files are released on success, failure and cancellation.

The previous `PdfLayoutReconstructor`/`PdfGraphicsDevice` pipeline is no longer called by production
conversion. Its existing regression fixtures remain available; the active pipeline has additional
JVM cleanup and native export/conversion tests.

The generated EPUB is a file in app storage. Its database record stores the path, conversion
version, source map and reading state. Converter version **14** invalidates older cached
conversions; **Advanced → Reparse PDF** explicitly rebuilds a rendition. Opening an already current
rendition continues to reuse the cached file.

In read mode, page labels use the source PDF page anchors and EPUB page list. Source-map paths are
relative to the EPUB package; reader locations are resolved against the opened publication's reading
order before restoration or navigation. This also applies to migrated bookmarks and highlight
decorations. Readium's estimated byte-based positions remain available for progression seeking; they
are not screen-page counts and can be inflated by inline SVG formula data.

This remains a deterministic, on-device conversion. MuPDF's semantic/table output is heuristic for
untagged PDFs. Scanned pages retain their original appearance as native SVG containing their source
imagery; there is no OCR or language model.
`mutool clean` repairs PDF syntax and is not an HTML/layout cleanup stage.

## Validation

Run the repository's Quick checks:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
```

`PdfReadModeConverterTest` additionally runs on an Android device/emulator. It checks native chart
preservation, filled vector drawings, inline scripts, location migration, cache reuse, cancellation
and Unicode serialization. Optional `pdfPath`
and `thesisPath` instrumentation arguments enable the research-paper and full-thesis audits using
copies of the PDFs accessible inside the app's storage. The thesis audit verifies every source page
and the final page in an Android WebView DOM, three tables on a mixed text/table page, caption order
and inline figure references. Both PDF audits check chapter/page links and every block/run's mapped
resource, and confirm that all source pages survive conversion. The paper stays in one file; the
thesis uses seven files. JVM tests cover exact 25-page intervals, repeatable output, deferred cuts
for content groups, cross-file links, bibliography entries at file boundaries, and
saved-location/range migration in both directions.
`MuPdfExportTest` can separately save raw native XHTML/XML/SVG for diagnosis; its optional `pages`
argument selects comma-separated, one-based PDF pages.
