# Permanent compatibility baseline

Synthetic, non-user fixtures assembled once from the models and exported Room schemas in tag
`v1.0.0` (`2b2ca9f`). These are not backups containing a user's library. The schema and settings
contracts come from that tag; the EPUB text, cover, dictionary, and records are project-created
test data under the project license. No external books or dictionary definitions are included.

`portable.pinebackup` and `vault.zip` describe the same library, including its original EPUB,
cover, dictionary, space/shelf membership, author/tag, bookmark, note, progress, and settings.
`library.db` and `dictionary.db` use the unchanged tagged schema version 2. The JSON preference
files retain explicit historical field names and values. `checksums.json` freezes the payloads.

Never regenerate these files from current models, overwrite their schemas, or update their
checksums to make a test pass. Add a new version directory for new historical fixtures.
When serialization or schema changes, fix the production migration/reader instead.
