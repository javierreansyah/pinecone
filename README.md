<div align="center">
  <img src="docs/images/app-icon.jpg" width="160" alt="Pinecone icon" />
  <h1>Pinecone</h1>
  <p>
    Pinecone is an Android reader for books, documents, comics, and audiobooks, with Material 3 Expressive design and customizable reading and listening.
  </p>
</div>

## Screenshots

Current development app, captured at 1080 × 2400 on the Resizable (Experimental) emulator.

| Library | Shelves | Book details |
| --- | --- | --- |
| <img src="docs/images/library.png" width="280" alt="Library grid with book covers, authors and reading progress" /> | <img src="docs/images/shelves.png" width="280" alt="Audiobooks and Classic Literature shelves" /> | <img src="docs/images/book-info.png" width="280" alt="Book cover, contributors, tags and reading details" /> |

| EPUB reading | Original PDF | PDF converted to EPUB read mode |
| --- | --- | --- |
| <img src="docs/images/epub-reader.png" width="280" alt="Pride and Prejudice in the sepia reader with a saved bookmark" /> | <img src="docs/images/pdf-reader.png" width="280" alt="Attention Is All You Need in its original PDF layout" /> | <img src="docs/images/pdf-read-mode.png" width="280" alt="The same paper converted on device into reflowable text" /> |

PDF read mode keeps the original available and supports reading customization. Scanned pages remain
images; OCR is not supported.

| Audiobook playback | Offline dictionary | Reader customization |
| --- | --- | --- |
| <img src="docs/images/audiobook-player.png" width="280" alt="Romeo and Juliet audiobook with cover art and playback controls" /> | <img src="docs/images/dictionary-lookup.png" width="280" alt="An offline word definition opened from selected text" /> | <img src="docs/images/reader-settings.png" width="280" alt="Font, typography, spacing and publisher-style controls" /> |

<details>
<summary>More features: search, organization, annotations, appearance and backups</summary>

| Library search | Spaces | Bulk selection |
| --- | --- | --- |
| <img src="docs/images/library-search.png" width="280" alt="Search results for Shakespeare across books, audiobooks and authors" /> | <img src="docs/images/spaces.png" width="280" alt="Fiction and Non Fiction spaces with title counts" /> | <img src="docs/images/bulk-selection.png" width="280" alt="Three selected books with bulk actions and a paused audio mini player" /> |

| Shelf membership | Notes and highlights | Converted PDF chapters |
| --- | --- | --- |
| <img src="docs/images/bulk-organize.png" width="280" alt="Organize selected books into spaces and shelves" /> | <img src="docs/images/notes.png" width="280" alt="A saved highlighted word and reading note" /> | <img src="docs/images/pdf-chapters.png" width="280" alt="Chapter navigation in the converted PDF read mode" /> |

| Playback settings | App appearance | Backup and restore |
| --- | --- | --- |
| <img src="docs/images/audiobook-settings.png" width="280" alt="Audiobook playback speed, pitch and reading options" /> | <img src="docs/images/appearance.png" width="280" alt="Material color palettes for the app theme" /> | <img src="docs/images/backup-restore.png" width="280" alt="Local backup location, schedule, manual backup and restore controls" /> |

</details>

Screenshot content and artwork retain their original rights; see [asset sources](docs/ASSET_SOURCES.md).

## Features

- **Books, documents, comics, and audiobooks:** Read EPUB books, PDF documents, CBZ comics, image
  publications, and audiobook packages, or listen to supported standalone audio files.
- **PDF read mode:** Switch between the original PDF and a reflowable reading view generated on your
  device. Scanned pages remain images; OCR is not supported.
- **Audiobook playback:** Play and pause, seek, skip forward or back by 10 seconds, and adjust
  playback speed. Embedded audio tags supply titles and authors when available.
- **Spaces and shelves:** Organize titles into optional spaces and shelves. Create shelves in
  multiple spaces, and move or copy shelves between spaces.
- **Library browsing:** Search titles, authors, shelves, and tags; switch between grid and list
  layouts; and filter by format, reading status, or shelf membership.
- **Reading status and archives:** Track progress, mark titles as finished, archive them, and
  optionally keep new imports in an Unsorted inbox until organized.
- **Bulk organization:** Select multiple titles to organize, mark as read, archive, or delete.
- **Publication details:** View and edit titles, contributors, descriptions, covers, languages,
  identifiers, and comic volume and issue metadata, including imported ComicInfo.xml fields.
- **Bookmarks, notes, and highlights:** Save passages and reading positions, then revisit them from
  the reader or title info page where supported by the format.
- **Search within publications:** Find text in EPUB books and PDF documents and jump to matches.
- **Offline dictionaries:** Import StarDict dictionaries and look up selected words while reading.
- **Reading customization:** Adjust typography, spacing, colors, brightness, and reading options,
  with settings suited to each format.
- **Material 3 Expressive:** Use dynamic colors or curated palettes, light and dark themes,
  adjustable contrast, and English or Indonesian interface text.
- **Local import and backups:** Import individual files or scan folders, create manual or scheduled
  backups, and restore your library, dictionaries, settings, and reading progress.

## Building and updating

See [the release and compatibility guide](docs/RELEASING.md) for signing, versioning, safe updates,
backup recovery, lightweight Windows development, and server-built F-Droid releases. New releases must preserve
installed data and backups from v1.0.0 onward. Development APKs use a separate app ID.

## License

Pinecone is distributed under a single license, **AGPL-3.0-or-later**, with no alternative license
offered. Copyright (C) 2026 Javier Aahmes Reansyah. See [COPYING](COPYING) for the full license
text. Dependency licenses and links are available in the app's About section.
