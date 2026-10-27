# Asset and dependency provenance

Bundle only assets/dependencies with compatible licenses and traceable sources. Preserve copyright,
license text, attribution and reserved font names when replacing or modifying them. Recheck the
resolved dependency graph and packaged notices when dependencies change; an old audit is not proof
for a new build.

| Assets | Provenance and license |
| --- | --- |
| Fonts | Atkinson Hyperlegible, Literata and Adobe Source families: OFL-1.1. Sources/copyrights are in the bundled [font notice](../app/src/main/assets/fonts/NOTICE.txt), alongside `OFL.txt`. Preserve the reserved name "Source". |
| App/listing artwork | Existing project artwork under AGPL-3.0-or-later. Listing icon derives from `docs/images/AppIcon.jpg`; the Settings capture avoids third-party book artwork. The Indonesian listing currently reuses the English capture. |
| Material Symbols | Supplied by the existing icon dependency under Apache-2.0. |
| Compatibility fixtures | Project-created synthetic books, images, records and dictionary content under the project license. |

Do not assume that third-party covers or text visible in a screenshot can be redistributed.

F-Droid replaces the MuPDF Maven AAR with pinned upstream source and submodules. Preserve its
AboutLibraries entry and upstream/third-party notices under `assets/mupdf` when substituting the
dependency. Review server dependency/scanner reports; metadata alone is not a source audit.

The recipe's two hyphenation ZIP exceptions contain UTF-8 pattern data, not executable code.
Keep exceptions specific, documented and tied to reviewed source; recheck them on upgrades.
Never ignore an entire dependency directory to conceal binaries.
