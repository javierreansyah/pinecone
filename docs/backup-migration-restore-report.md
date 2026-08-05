# Backup restore after database migration

> **Status (2026-08-05): superseded by the development reset and vault-v1 backup.**
> This document records defects in the discarded schema-v12 and backup implementations.
> The application now uses clean Room-v1 baselines and one deduplicated, checksummed
> backup vault. Vault snapshots and portable exports both start at format version 1;
> no reader or migration is provided for any earlier development backup. Existing
> development installs must clear app data or reinstall.

## Executive summary

The current implementation cannot be considered safe across app updates. The most likely reason a migrated installation fails before backup restore is attempted is an incomplete Room migration graph: the database is version 12, but the application registers migrations for `1→2`, `2→3`, `3→4`, `4→5`, `5→6`, `8→9`, `10→11`, and `11→12`. There is no registered path for database versions 6 or 7 to reach version 8.

Even when Room opens successfully, the backup format is incomplete for the current schema. A v12 backup omits `spaces` and `book_space_cross_ref`, while restore also does not clear or repopulate those tables. A restore can therefore report success while silently losing the user's spaces and their book assignments.

The backup format is a JSON/ZIP data export, not a raw SQLite database backup. Room migrations update the live database only; they do not transform an already-created `.pine` archive. The archive must therefore have its own compatibility/versioning strategy.

## Scope and evidence

This report is based on the working tree inspected on 2026-08-04. The working tree already contains unrelated/uncommitted changes, including the collection-to-space work. No application source was changed by this report.

Relevant files:

- [`AppDatabase.kt`](../app/src/main/java/com/javierreansyah/pinecone/data/local/database/library/AppDatabase.kt) — Room schema version and migration graph.
- [`PineconeApplication.kt`](../app/src/main/java/com/javierreansyah/pinecone/PineconeApplication.kt) — migrations actually registered at runtime.
- [`LibraryBackupRepository.kt`](../app/src/main/java/com/javierreansyah/pinecone/data/repository/backup/LibraryBackupRepository.kt) — ZIP creation and restore logic.
- [`LibraryBackupPayload.kt`](../app/src/main/java/com/javierreansyah/pinecone/data/model/LibraryBackupPayload.kt) — serialized library backup contract.
- [`DictionaryBackupManager.kt`](../app/src/main/java/com/javierreansyah/pinecone/data/repository/dictionary/DictionaryBackupManager.kt) — separate dictionary SQLite backup.

## Findings

### 1. The Room migration graph has gaps — critical

`AppDatabase` declares version 12, but the migration constants jump from `MIGRATION_5_6` to `MIGRATION_8_9`. The application registers the same incomplete sequence. A device whose existing `reader_database` is at version 6 or 7 cannot be upgraded to v12 because Room has no path through the missing versions.

The builder explicitly disables destructive fallback with `.fallbackToDestructiveMigration(false)`. That is appropriate for protecting user data, but it means the app will fail to open rather than recreate the database. The visible symptom may look like backup restore failure because restore cannot run until the application database has opened.

The history in git confirms that v10 and v11 existed before the current v12 work, while the current source still has no `6→7` or `7→8` migration. The exact SQL for those missing transitions must be recovered from the release that created schema versions 7 and 8; it should not be guessed.

### 2. Room migrations do not migrate backup archives — critical design gap

`performBackup()` serializes a `LibraryBackupPayload` with a hard-coded payload `version = 1`. Restore decodes that payload directly and inserts its entities into the currently opened Room database.

This creates two independent version axes:

| Version | Meaning | Current handling |
| --- | --- | --- |
| Room database version | On-device SQLite schema | Migrated by Room when the app opens |
| Backup payload version | JSON/ZIP contract in `.pine` | Always written as `1`; no explicit upgrade pipeline |

An old archive does not pass through Room migrations. If the entity shape changes, restore relies on nullable/default fields and `ignoreUnknownKeys`, which can hide incompatibilities instead of reporting them. A future required field, renamed field, changed meaning, or changed relationship can make restore fail or lose data without a clear compatibility error.

### 3. Current v12 spaces are not included in library backups — high

`LibraryBackupPayload` includes books, bookmarks, shelves, shelf cross-references, notes, authors, tags, and their cross-references. It has no `spaces` list and no `bookSpaceCrossRefs` list.

The current schema adds both `spaces` and `book_space_cross_ref` in `MIGRATION_11_12`. The backup writer never reads these tables, and the restore transaction never clears or inserts them. Consequently:

- a backup created after the spaces feature cannot reproduce the user's spaces;
- restoring over an installation that already has spaces leaves stale spaces behind;
- restored books lose their space assignments;
- the method can still return `true`, so the UI may claim success.

This is a data-integrity failure even if the database migration itself succeeds.

### 4. The v12 migration is not fully aligned with the entity contract — high

`MIGRATION_11_12` drops the `collections` table and copies collection data into the new spaces tables. It does not remove `books.collectionId`. The current `BookEntity` still maps `legacyCollectionId` to the `collectionId` column, so this may be intentional compatibility retention; however, it should be verified against the generated Room schema. `exportSchema = false` prevents schema JSON from being checked into the project, making this class of mismatch harder to detect.

The migration should be validated with Room's schema identity verification, especially because the collection files are currently being removed/changed in the working tree.

### 5. Restore is not transactionally complete for settings and files — medium

Settings are applied before the database transaction. Book and cover files are copied while the Room transaction is open, but filesystem operations cannot roll back with SQLite. If a copy fails after database insertion, the database transaction rolls back but files may already have been deleted or partially replaced. The method then returns `false`, but the on-device filesystem may no longer match the database.

The ZIP extraction also constructs `File(tempDir, entry.name)` without validating that the normalized path remains under `tempDir`. A malformed archive can write outside the temporary directory. This is separate from the migration issue but should be fixed before treating restore as a reliable data-recovery path.

### 6. Dictionary backups have a separate SQLite consistency risk — medium

Dictionary backups copy `.db`, `-wal`, and `-shm` files directly. If the database is open or a checkpoint is not forced, these three files may not represent a stable snapshot. Restore closes only the dictionaries listed in current settings before deleting them; dictionaries present only in the selected backup may not have an open database to close, which should be handled explicitly.

This does not explain the library `.pine` failure, but the worker reports overall failure unless both library and dictionary backups succeed, so it can make backup health appear unreliable.

## Most probable failure sequence

1. The user installs an update containing schema version 12.
2. Room opens `reader_database` and reads an older version, likely 6 or 7.
3. Room cannot find a migration path to version 12 because `6→7` and/or `7→8` is missing.
4. Destructive fallback is disabled, so database initialization fails.
5. The restore action cannot obtain a usable `PineconeApplication.database`, and the backup appears to be unloadable.

If the existing database is already version 8 or newer, the next likely symptom is not an archive parsing error but incomplete restoration of spaces, because the archive contract predates the v12 tables.

## Required remediation

### Immediate release blocker

1. Recover and implement the missing `6→7` and `7→8` migrations from the historical schema definitions or released APKs.
2. Register every contiguous migration through `12` in `PineconeApplication`.
3. Add an instrumented migration test for every supported start version: `1→12`, `2→12`, …, `11→12`, plus an already-v12 open test.
4. Do not use destructive migration as the compatibility fix. It would make the app open by deleting user data.

### Backup format correction

1. Extend the payload with `spaces` and `bookSpaceCrossRefs`.
2. Increment the backup format version when the contract changes.
3. Add a dedicated backup migrator, for example `v1 → v2`, before deserialization into current entities.
4. Validate required archive entries, payload version, referential integrity, and record counts before deleting current data.
5. Restore every table represented by the current schema, and clear every restored table, including spaces and space cross-references.
6. Keep the backup folder URI and other installation-specific permissions as the current code does.

### Restore safety correction

1. Extract with a path-traversal check (`canonicalFile` must remain under the canonical temporary directory).
2. Stage database rows and files first; replace live data only after validation succeeds.
3. Use a temporary files directory and an explicit commit/rollback strategy so database rows never point to missing files.
4. Return structured failure reasons (unsupported format, malformed archive, migration unavailable, invalid references, file copy failure) instead of only `Boolean`.
5. Add checksums or at least archive size/hash metadata so a partially written backup cannot be selected as valid.

## Verification plan

The minimum release test matrix should include:

| Test | Expected result |
| --- | --- |
| Fresh install → add books, notes, shelves, spaces → backup → restore | All rows and files restored exactly |
| Database v6 → update to current version | Opens with all data preserved |
| Database v7 → update to current version | Opens with all data preserved |
| Database v8, v9, v10, v11 → update to current version | Opens with all data preserved |
| Backup made on previous app version → restore on current version | Archive is migrated and accepted |
| Current backup restored over populated current database | No stale rows remain; spaces and relationships match backup |
| Corrupt ZIP, missing `data.json`, unsupported payload version | Restore fails before deleting current data |
| Archive entry containing `../` | Restore rejects the archive |
| Interrupted/failed file copy | Database and files remain recoverable and consistent |
| Dictionary backup with active WAL | Restored dictionary opens and returns expected entries |

For each successful restore, compare stable record counts and relationship sets before backup and after restore. Comparing only the final book count is insufficient; the missing-spaces defect would pass that check.

## Release acceptance criteria

Backup/restore should not be enabled for an update until all of the following are true:

- every supported Room version has a tested migration path to the current version;
- the backup format has an explicit version and migration policy;
- all current user-owned tables and relationships are represented in the archive;
- validation completes before current data is deleted;
- restore tests pass across at least one backup from every supported prior release;
- failures are observable in logs and are actionable in the UI.
