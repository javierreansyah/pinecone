# Validation

## Select the scope

Default to **Quick**. Run **Diff** or **Complete** only when the user requests broader
validation. Do not expand a routine iteration into a project-wide cleanup.

| Mode | Build and JVM unit tests | Android lint | IDE inspections |
|---|---|---|---|
| **Quick** (default) | Debug | None | None; Studio/CLI not required |
| **Diff** | Debug | Debug | Changed/new/uncached source files; reuse unchanged results |
| **Complete** | Debug, release, staging | All three variants | Every source file freshly inspected; no IDE cache reuse |

All modes run pipeline self-tests and `git diff --check`. Compiler warnings remain
errors. **Quick can pass while lint/IDE findings remain**: those checks are skipped,
not cleared. Complete covers local builds, JVM tests, lint, and file-level IDE
inspections—not device/UI tests or every batch-only JetBrains inspection.

## Run

From the repository root (Windows PowerShell 5.1+):

```powershell
# Routine iteration; same as omitting -Mode
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate.ps1 -Mode Quick

# Incremental lint + IDE validation
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate.ps1 -Mode Diff

# All variants + fresh whole-source IDE coverage
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate.ps1 -Mode Complete
```

For **Diff/Complete**, open this project in Android Studio, finish sync/indexing,
and check `android-cli studio check` reports it READY. The script finds the CLI on
PATH or at `$env:USERPROFILE\.android\bin\android-cli.exe`.

## Fix and verify

1. Run the requested mode. Read `summary.json` and the reports for checks that ran.
2. **Quick:** fix compiler/build warnings/errors and test failures. Do not start
   unrelated lint/IDE cleanup or require Studio to make Quick pass.
3. **Diff/Complete:** also fix all reported code/style findings, including IDE
   `INFO` suggestions: redundant constructs/opt-ins, formatting, parentheses,
   destructuring, and collection simplifications. Review any supplied exported
   **Inspect Code** report too; CLI file analysis does not cover every batch check.
4. Rerun after each fix batch; another occurrence may become visible. Finish with
   a passing run of the requested mode. Partial or narrower checks do not replace it.
5. Do not weaken checks, create a baseline, or blanket-suppress findings. A verified
   false positive needs a narrow, documented exception with evidence.
6. Missing tools, failed/stale analyses, timeouts, or changed inputs are blockers,
   not passes. The CLI reports findings; edit the code to fix them. A disconnected
   IDE blocks fresh IDE verification, not source fixes supported by existing reports.
7. Report mode, results, remaining blockers, runtime, and fresh/cached IDE counts.
   Preserve unrelated changes. Never label Quick/Diff as Complete or claim a cached
   result was freshly executed. Record upstream Gradle/AGP/plugin warnings separately.

## Allowed exclusions

- Proofreading/spelling/grammar; these are recorded separately from code findings.
- Clickable "Open in browser" URL navigation hints.
- **`README.md` only:** HTML conformance/style warnings for GitHub layout, such as
  `align="center"` or percentage image widths. Preserve that layout. This does not
  excuse broken links/images, malformed content, or HTML issues in application
  code, WebViews, assets, or resources. Markdown is outside the automated source scan.
- Existing narrowly documented false positives, such as the jsoup exception in
  `lint.xml`; recheck the evidence on dependency upgrades.

## Cache, stability, and iteration

- Diff reuses content-hash/context-matched IDE results. **Cached findings still
  fail Diff.** Failed analyses are retried, not cached. Deleted files are removed.
- A cold cache or build/toolchain/profile/CLI/validation-script change causes a
  whole-source IDE scan in Diff. Ordinary file edits recheck only affected files.
  Cross-file IDE-only effects may require Complete. Complete always inspects all files.
- Quick never reads/writes the IDE cache or connects to Studio. It keeps Gradle's
  incremental caches. Do not routinely run `clean` or `--rerun-tasks` in any mode.
- Diff/Complete use two IDE workers by default; all modes use at most four Gradle
  workers by default. Gradle precedes IDE analysis. Keep the existing 4 GB Gradle heap.
- One run at a time; atomic checkpoints retain completed IDE work after interruption.
  Changed project/configuration/Studio inputs block the result. CLI timeout: 90 s
  per request; Gradle timeout: 30 min. Retry only failed/unrecognized/stale output,
  not every successful inspection.
- Optional: `-AndroidCli <path>`, `-IdeWorkers 1`, `-GradleWorkers 2`,
  `-IdeTimeoutSeconds 120`. For inspection-only iteration, explicitly use
  `-Mode Diff -IdeOnly` or `-Mode Complete -IdeOnly`; neither is a complete validation.

## Reports and exit codes

Read **`build/reports/validation/summary.json` first**. It identifies the current
mode, checks, skipped IDE stage, status, and timings. Quick records IDE finding
counts as `null`, not zero. Reports left by earlier runs are not current evidence.

- `gradle.txt`: build/test output and lint output when requested.
- `ide-inspections.json` / `.txt`: Diff/Complete findings, exclusions, and failures.
- `ide-cache.json`: local reusable diagnostics, not an accepted-issues baseline.
- `diff-check.txt`: whitespace/conflict-marker check.
- Lint: `app/build/reports/lint-results-{debug,release,staging}.*`.
- Unit tests: `app/build/reports/tests/test{Debug,Release,Staging}UnitTest/`.

Exit **0**: requested scope passed (`PASS_IDE_ONLY` is partial). **1**: checks/findings
failed. **2**: tooling/orchestration blocked. RUNNING, BLOCKED, or an old report is
not a pass.

For changes to the pipeline, also run its process/timeout/concurrency regression
tests (these do not run real Diff/Complete application validation):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\validation.Tests.ps1 -IncludeProcessTests
```
