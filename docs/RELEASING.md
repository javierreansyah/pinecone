# Releases and compatibility

Support installed data and backups from **v1.0.0 onward**, including skipped releases. Downgrades
and loading newer formats into older apps are unsupported; reject them before changing user data.

## Release identity

- Keep production ID `com.javierreansyah.pinecone` and each channel's signing identity stable.
  Debug/staging use `.dev` with development signing; release must never fall back to debug signing.
- F-Droid signs its releases. Optional direct APKs retain the existing developer key, whose
  certificate is checked by [the release verifier](../scripts/verify_release.py). Keep signing
  credentials private and backed up; the build's `pinecone.signing.*` properties belong in private
  Gradle user properties. Android Studio's signed APK wizard also supplies credentials; filenames
  omit `-unsigned` for either signing method. Missing credentials produce an unsigned APK; partial
  private configuration fails. Verify the APK certificate before distribution regardless of filename.
- Android replacement requires compatible package, certificate, version code, SDK and ABI support.
  Switching between developer-signed and F-Droid installations requires a verified backup outside
  app-private storage, reinstall, and restore. Android cloud backup/device transfer is disabled.
  Never clear or uninstall a user's app to test recovery without explicit authorization.
- Commit increasing `versionCode` values; keep app, database and backup versions independent.
  The unreleased app baseline is **1.0.0 / 100004**; library/dictionary schemas and backup formats
  are currently **2**. Preserve the higher app code so earlier same-key test APKs can upgrade.
- Public artifacts and tags are immutable unless replacement is explicitly authorized. Verify
  tags identify the reviewed release commit; frozen fixtures retain their original source identity.

## Evolve stored data safely

- Preserve database filenames, historical Room schemas, migrations and integrity constraints.
  Schema changes need a version bump, non-destructive migration, and tests from every supported
  starting schema. Never use destructive fallback or silently reset undecodable preferences.
- Treat serialized names, enum values, nested JSON, settings, locators and file references as
  persistent contracts, including live entities reused in backups. Preserve defaults/wire names
  or provide explicit conversion. App major-version changes do not waive compatibility.
- Keep format-2 readers when adding newer backup formats. Check each layer's version before
  payload decoding; maintain explicit readers/converters rather than globally ignoring fields.
- Validate identities, archive paths, relationships, checksums and canonical destination containment
  before replacement. Migrate dictionaries on staging copies, checkpoint and close them before
  swapping. Preserve transactional replacement, recovery journals and pending-settings recovery.
- Clean partial files on failure/cancellation; keep originals until replacement is durable.
  Pruning must stop when any retained snapshot is unreadable or enumeration is incomplete.
- Preserve frozen fixtures/checksums under `data/src/test/resources/compatibility/v1.0.0`.
  They are synthetic historical contracts, not real-user exports. Add fixtures for later released
  formats; never regenerate old bytes to make tests pass.

## Build and distribute

Follow [validation](VALIDATION.md) according to the change. Keep local Windows development on
prebuilt MuPDF; source/native compilation, scanning and recipe validation belong on F-Droid's
hosted CI. Complete is opt-in. No local WSL, independent reproduction pipeline or GitHub Actions
is required.

The `fdroid/` templates describe the server build; `fastlane/metadata/android` supplies the listing.
Submit the recipe/srclib through `fdroiddata`, referencing the full public release commit. Keep the
MuPDF source/submodule, catalog, attribution and toolchain pins consistent. An srclib supplies
source; the native Gradle module compiles it on the server. Preserve [asset provenance](ASSET_SOURCES.md)
and narrowly justified scanner exceptions. Keep draft builds disabled until their source exists;
no developer-signed reference APK or signature-copy pipeline is required.

Validate actual APK identity, historical restore and normal in-place upgrades on an isolated
Resizable emulator; F-Droid does not test app data compatibility for us. For the first F-Droid
release, rehearse backup/reinstall/restore from the developer installation instead. Keep the old
APK and a second backup copy until recovery is proven. Record the source/version, APK hash and
certificate, validation evidence and unresolved issues. Local build success, an unsigned candidate,
or passing CI alone does not establish publication readiness or F-Droid acceptance.

References: [F-Droid submission](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[metadata reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).
