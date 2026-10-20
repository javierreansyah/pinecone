# PDF read mode

The production converter uses MuPDF 1.28.5's native document writers. It does not
run a desktop `mutool` process or use a server. `PdfReadModeConverter` orchestrates
these steps on the device:

1. `MuPdfPageExporter` loads one native display list per extraction page. It exports
   semantic XHTML and detailed structured-text XML with structure collection,
   segmentation, paragraph breaking, dehyphenation, style collection and accurate
   glyph bounds. A separate export enables native table hunting. This keeps false
   table detections from changing the main reading order. Native vectors stay in
   MuPDF; the unsafe Java structured-text vector callback is not used.
2. `MuPdfStructure` reads the exported geometry and native table cells. Page records
   are written to temporary files. Only bounded font/margin statistics are retained
   across the first pass, rather than keeping the document's glyphs in memory.
3. `MuPdfReflowCleaner` adapts the native XHTML for a reflowable reader: consistent
   headings, joined number/title fragments, list markers and continuations,
   bibliography entries, superscripts/subscripts and repeated margin text. It
   rejects obvious table false positives. When MuPDF combines synchronized table
   lines into super-cells, cleanup splits those native lines into rows while keeping
   MuPDF's columns and cell spans. Ambiguous tables remain native text.
4. Visual regions are exported through MuPDF's SVG document writer with `text=path`.
   Native display-list clipping selects the region. Original paths, glyph outlines,
   raster images, masks, colors and clipping remain together. Charts are standalone
   SVG assets, including their labels; their primitives are not reconstructed as
   separate HTML elements. Region selection uses native figure tags/segmentation
   when available. Untagged drawings still require conservative region grouping.
   Equations use the same native SVG writer, embedded inline with isolated IDs and
   text-color adaptation. Simple inline scripts remain selectable text. No formulas
   are redrawn using substitute fonts or hand-built radical paths. If a font has
   unmapped Unicode glyphs, the affected paragraph/table retains its original layout
   in native SVG; that fallback is not fully reflowable or text-selectable.
5. `EpubWriter.nativePage` streams the cleaned content into one continuous
   `text/content.xhtml`, adds SVG/image resources to the manifest and preserves the
   original PDF outline, source-page anchors and page list. Source blocks/text runs
   feed the existing bookmark/note/location remapping. Native handles and temporary
   records are released on success, failure and cancellation.

The previous `PdfLayoutReconstructor`/`PdfGraphicsDevice` pipeline is no longer
called by production conversion. Its existing regression fixtures remain available;
the active pipeline has additional JVM cleanup and native export/conversion tests.

The generated EPUB is a file in app storage. Its database record stores the path,
conversion version, source map and reading state. Converter version **10** invalidates
older cached conversions; **Advanced → Reparse PDF** explicitly rebuilds a rendition.
Opening an already current rendition continues to reuse the cached file.

This remains a deterministic, on-device conversion. MuPDF's semantic/table output
is heuristic for untagged PDFs. Scanned pages retain their original appearance as
native SVG containing their source imagery; there is no OCR or language model.
`mutool clean` repairs PDF syntax and is not an HTML/layout cleanup stage.

## Validation

Run the repository's Quick checks:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
```

`PdfReadModeConverterTest` additionally runs on an Android device/emulator. It
checks native chart preservation, filled vector drawings, inline scripts, location
migration, cache reuse, cancellation and Unicode serialization. Optional `pdfPath`
and `thesisPath` instrumentation arguments enable the research-paper and full-thesis
audits using copies of the PDFs accessible inside the app's storage. The thesis
audit verifies every source page and the final page in an Android WebView DOM.
`MuPdfExportTest` can separately save raw native XHTML/XML/SVG for diagnosis.
