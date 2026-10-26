# Validation

Run Gradle directly from the repository root using Windows PowerShell. Default to **Quick**; use
**Complete** only when broader validation is requested.

| Mode                | Builds and JVM unit tests | Android lint       |
|---------------------|---------------------------|--------------------|
| **Quick** (default) | Debug                     | None               |
| **Complete**        | Debug, release, staging   | All three variants |

Neither mode runs IDE inspections or requires Android Studio or Android CLI. IDE recommendations are
handled manually by the user in JetBrains tools. Complete does not include device/UI tests.

The app's unit-test tasks also run `:core:model:test` and the matching `:data`/`:publication`
unit tests. Staging consumes the release library variants and shares their release tests. Library
release unit tests are explicitly enabled. Dependency lint includes the JVM model sources through
the standalone `com.android.lint` plugin; it must not silently skip that module.

## Commands

```powershell
# Quick: debug build and JVM unit tests
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all

# Complete: builds, JVM unit tests, and lint for all build types
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:assembleStaging :app:testStagingUnitTest :app:lintStaging --continue --warning-mode all
```

## Fix and verify

- Fix build/compiler warnings and errors and failing tests. Complete also requires fixing all
  Android lint errors and warnings. Rerun the requested command after fixes until it passes;
  narrower checks do not replace Complete.
- Keep compiler warnings-as-errors and strict lint settings. Do not weaken checks, introduce
  baselines, or blanket-suppress findings. Verified false positives need narrow, documented
  exceptions with evidence.
- The manifest's local `RemoveWorkManagerInitializer` exception covers an IDE false positive:
  retain the WorkManager metadata removal rule and the installed-manifest assertion in
  `DependencyInjectionTest`. Removing that merge rule would re-enable the default initializer.
- Proofreading/spelling/grammar and README-only HTML layout recommendations are outside this
  validation scope. Preserve the documented jsoup false-positive exception in `lint.xml`; recheck it
  on dependency upgrades.
- Preserve unrelated changes. Do not perform automated IDE recommendation cleanup.
- Keep Gradle's incremental caches; do not routinely run `clean` or `--rerun-tasks`.
- Report the mode, Gradle outcome, remaining warnings/blockers, and any skipped checks honestly.
  Gradle may report tasks as up-to-date or restored from cache; do not claim every task freshly
  executed. Quick can pass while lint findings remain.
- Old `build/reports/validation/` reports from the removed pipeline are not current validation
  evidence. There is no custom summary, IDE cache, or pipeline self-test.

## Reports

- Android lint: `app/build/reports/lint-results-{debug,release,staging}.*`.
- JVM tests: `app/build/reports/tests/test{Debug,Release,Staging}UnitTest/`.
- Read Gradle's console output and exit code for the current run's result.
