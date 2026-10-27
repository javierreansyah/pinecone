# Validation

Use **Quick** by default and **Complete** only when explicitly requested. Run from the repository
root in Windows PowerShell.

| Mode | Builds and JVM tests | Android lint |
| --- | --- | --- |
| Quick | Debug | None |
| Complete | Debug, release, staging | All three variants |

```powershell
# Quick
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all

# Complete
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:assembleStaging :app:testStagingUnitTest :app:lintStaging --continue --warning-mode all
```

App test tasks include the model and matching library tests; staging uses release library variants.
Keep dependency/model lint coverage enabled. Neither mode includes device tests or IDE inspections.
Run affected compatibility tests when persistence changes, using **Resizable (Experimental)**.
IDE recommendations, including proofreading, are handled manually by the user.

## Keep checks reliable and affordable

- Fix compiler errors/warnings and failing tests; Complete also requires resolving lint errors and
  warnings. Rerun the requested mode after fixes; narrower checks do not replace it.
- Preserve warnings-as-errors and strict lint. Do not add baselines or blanket suppressions.
  Keep documented false-positive exceptions narrow and recheck them when dependencies change.
- Respect the resource limits in `gradle.properties`, run checks sequentially, and preserve caches.
  Avoid routine clean/forced rebuilds or starting an emulator unless device testing is needed.
- Local builds use prebuilt MuPDF. Do not start WSL, native/reproduction builds, or GitHub Actions
  for release validation; use F-Droid's hosted CI for its recipe and source build.
- Report the requested mode, exit status, remaining warnings, and skipped checks. Distinguish
  executed tasks from cached/up-to-date work. Use current console output and build reports;
  successful Quick does not establish lint cleanliness or release readiness.
