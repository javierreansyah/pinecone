# Working guidelines

Preserve unrelated user changes. Keep changes focused and reuse existing project patterns.

Default to **Quick** from the repository root (Windows PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
```

Fix compiler errors/warnings and failing tests, then rerun the requested validation. Keep strict
checks; report remaining upstream warnings and skipped checks honestly. Quick does not run lint.
Use **Complete** only when explicitly requested; see [validation](docs/VALIDATION.md) for scope
and commands. IDE recommendations are handled manually by the user.

Use only **Resizable (Experimental)** for device testing. Keep local checks sequential and
lightweight; native/F-Droid builds belong on F-Droid's hosted CI.

Read the relevant guide before changing:

- [Releases and compatibility](docs/RELEASING.md): release configuration, dependencies, databases,
  persisted models/settings, publication storage, and backup/restore. Preserve installed data and
  backups from **v1.0.0 onward**, including skipped releases.
- [Asset sources](docs/ASSET_SOURCES.md): bundled assets, attribution, and dependency licensing.
