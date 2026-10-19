[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Diff', 'Complete')][string]$Mode = 'Quick',
    [string]$AndroidCli,
    [ValidateRange(1, 4)][int]$IdeWorkers = 2,
    [ValidateRange(1, 8)][int]$GradleWorkers = 4,
    [ValidateRange(1, 600)][int]$IdeTimeoutSeconds = 90,
    [switch]$IdeOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
Import-Module (Join-Path $PSScriptRoot 'Validation.psm1') -Force
$root = Split-Path $PSScriptRoot -Parent
$reportDir = Join-Path $root 'build/reports/validation'
New-Item -ItemType Directory -Force $reportDir | Out-Null
$summaryPath = Join-Path $reportDir 'summary.json'
$cachePath = Join-Path $reportDir 'ide-cache.json'
$lock = $null
$watch = [Diagnostics.Stopwatch]::StartNew()
$exitCode = 2
$summary = [ordered]@{
    startedAt = (Get-Date -Format o); finishedAt = $null; mode = $Mode
    status = 'RUNNING'; partial = [bool]$IdeOnly; gradleExitCode = $null
    gradleTasks = @(); ideStatus = if ($Mode -eq 'Quick') { 'SKIPPED' } else { 'PENDING' }
    files = 0; analyzed = 0; cached = 0; findings = $null; analysisFailures = $null
    seconds = 0; gradleSeconds = 0; ideSeconds = 0; blocker = $null
}
Push-Location $root
try {
    try {
        $lock = [IO.File]::Open((Join-Path $reportDir 'validation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch { throw 'Another validation run is active. Let it finish before starting another.' }
    Write-AtomicJson $summaryPath $summary
    if ($Mode -eq 'Quick' -and $IdeOnly) {
        throw '-IdeOnly requires -Mode Diff or -Mode Complete. Quick is build/tests only.'
    }
    & (Join-Path $PSScriptRoot 'tests/validation.Tests.ps1')
    $initialState = Get-ProjectValidationState $root

    if (-not $IdeOnly) {
        $tasks = @(Get-ValidationTasks $Mode)
        $summary.gradleTasks = $tasks
        Write-Host "$Mode Gradle validation: $($tasks -join ' ')"
        $gradle = Invoke-CheckedProcess (Join-Path $root 'gradlew.bat') ($tasks + @('--continue', '--warning-mode', 'all', '--console=plain', "--max-workers=$GradleWorkers")) $root 1800
        [IO.File]::WriteAllText((Join-Path $reportDir 'gradle.txt'), $gradle.Output)
        Write-Host $gradle.Output
        $summary.gradleExitCode = $gradle.ExitCode
        $summary.gradleSeconds = $gradle.Seconds
    }

    # Quick deliberately ends before CLI discovery, IDE cache access, or lint.
    # Existing IDE findings do not gate this build/test-only scope.
    if ($Mode -eq 'Quick') {
        $diff = Invoke-CheckedProcess 'git.exe' @('-c', 'core.safecrlf=false', 'diff', '--check') $root 30
        [IO.File]::WriteAllText((Join-Path $reportDir 'diff-check.txt'), $diff.Output)
        if ((Get-ProjectValidationState $root).Hash -ne $initialState.Hash) {
            throw 'Project inputs changed during validation. Rerun against a stable working tree.'
        }
        $summary.status = if ($summary.gradleExitCode -eq 0 -and $diff.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
        $exitCode = if ($summary.status -eq 'PASS') { 0 } else { 1 }
        exit $exitCode
    }

    if (-not $AndroidCli) {
        $command = Get-Command android-cli, android -ErrorAction SilentlyContinue | Select-Object -First 1
        $AndroidCli = if ($command) { $command.Source } else { Join-Path $env:USERPROFILE '.android/bin/android-cli.exe' }
    }
    if (-not (Test-Path -LiteralPath $AndroidCli)) { throw 'Android CLI is unavailable. Supply -AndroidCli <path>.' }
    $AndroidCli = (Resolve-Path -LiteralPath $AndroidCli).Path
    $check = Invoke-CheckedProcess $AndroidCli @('studio', 'check') $root 30
    $status = Remove-Ansi $check.Output
    $readyPattern = '(?im)^\s*READY\s+.*' + [regex]::Escape($root.Replace('\', '/')) + '/?\s*$'
    if ($check.ExitCode -ne 0 -or $status.Replace('\', '/') -notmatch $readyPattern) {
        throw "Studio must report THIS project READY after sync/indexing. IDE validation is blocked.`n$status"
    }

    $allFiles = $initialState.Files
    $files = @($allFiles | Where-Object {
        $_ -match '\.(kt|kts|java|xml)$' -and $_ -notmatch '(^|/)(build|\.idea|\.gradle)/' -and
        (Test-Path -LiteralPath (Join-Path $root $_))
    })
    if ($files.Count -eq 0) { throw 'No project source files found; refusing an empty inspection pass.' }
    $summary.files = $files.Count

    # Build/toolchain/profile/script changes invalidate ALL cached IDE results.
    # Source edits invalidate their own results; Diff mode is deliberately incremental.
    $controls = @($allFiles | Where-Object {
        $_ -match '(\.gradle(\.kts)?$|(^|/)(gradle\.properties|local\.properties|\.editorconfig|lint\.xml)$|^gradle/|^\.idea/(inspectionProfiles|codeStyles)/|^\.idea/misc\.xml$|^scripts/.*\.(ps1|psm1)$)'
    })
    $controls += 'local.properties'
    $globalProfileRoot = Join-Path $env:APPDATA 'Google'
    if (Test-Path $globalProfileRoot) {
        $controls += @(Get-ChildItem $globalProfileRoot -Directory -Filter 'AndroidStudio*' | ForEach-Object {
            foreach ($folder in 'inspection', 'codestyles', 'options') {
                $directory = Join-Path $_.FullName $folder
                if (Test-Path $directory) {
                    Get-ChildItem $directory -File -Filter '*.xml' | Where-Object {
                        $folder -ne 'options' -or $_.Name -match '(inspection|code.?style|editor|kotlin)'
                    } | ForEach-Object { $_.FullName }
                }
            }
        })
    }
    $cliHash = Get-ContentHash $AndroidCli
    $fingerprints = @('schema=2', "root=$root", $status.Trim(), "JAVA_HOME=$env:JAVA_HOME", "ANDROID_HOME=$env:ANDROID_HOME", "cli=$AndroidCli", $cliHash, ($files -join '|'))
    $controlHashes = @{}
    foreach ($control in @($controls | Sort-Object -Unique)) {
        if (Test-Path -LiteralPath $control -PathType Leaf) {
            $controlHashes[$control] = Get-ContentHash $control
            $fingerprints += "$control=$($controlHashes[$control])"
        }
    }
    $context = Get-TextHash ($fingerprints -join "`n")
    $cache = @{}
    if (Test-Path $cachePath) {
        try {
            $saved = Get-Content $cachePath -Raw | ConvertFrom-Json
            if ($saved.schema -eq 2) { foreach ($entry in $saved.entries) { $cache[$entry.file] = $entry } }
        } catch { Write-Host 'IDE cache was unreadable; rebuilding it rather than skipping checks.' }
    }
    $results = [Collections.Generic.List[object]]::new()
    $queue = @()
    foreach ($file in $files) {
        $hash = Get-ContentHash (Join-Path $root $file)
        $entry = $cache[$file]
        if (Test-ReusableIdeResult $entry $hash $context -Fresh:($Mode -eq 'Complete')) {
            $results.Add($entry)
            $summary.cached++
        } else { $queue += [pscustomobject]@{ file = $file; hash = $hash } }
    }
    Write-Host "IDE $Mode mode: $($queue.Count) to inspect, $($summary.cached) cached; $IdeWorkers workers."
    $ideWatch = [Diagnostics.Stopwatch]::StartNew()
    $cacheEntries = [Collections.Generic.List[object]]::new()
    foreach ($result in $results) { $cacheEntries.Add($result) }
    # Save each completed result atomically, so interruption does not lose all progress.
    Invoke-IdeQueue $queue $AndroidCli $root $IdeWorkers $IdeTimeoutSeconds | ForEach-Object {
        $result = $_
        $result | Add-Member -NotePropertyName context -NotePropertyValue $context -Force
        $results.Add($result)
        $summary.analyzed++
        if ($result.valid) { $cacheEntries.Add($result) }
        Write-AtomicJson $cachePath @{ schema = 2; entries = $cacheEntries.ToArray() }
        Write-Host "IDE $($summary.analyzed)/$($queue.Count): $($result.file) [$(@($result.findings).Count) findings; $($result.seconds)s]"
    }
    # Also removes deleted files and old invalidation-context entries on zero-work runs.
    Write-AtomicJson $cachePath @{ schema = 2; entries = $cacheEntries.ToArray() }
    $summary.ideSeconds = [Math]::Round($ideWatch.Elapsed.TotalSeconds, 3)
    $findings = @($results | ForEach-Object { $_.findings })
    $failures = @($results | Where-Object { -not $_.valid } | ForEach-Object { @{ file = $_.file; reason = $_.failure } })
    $summary.findings = $findings.Count
    $summary.analysisFailures = $failures.Count
    $summary.ideStatus = if ($failures.Count -gt 0) { 'COMPLETED_WITH_FAILURES' } else { 'COMPLETED' }
    Write-AtomicJson (Join-Path $reportDir 'ide-inspections.json') @{
        generatedAt = (Get-Date -Format o); mode = $Mode; partial = [bool]$IdeOnly
        context = $context; files = $files; findings = $findings; analysisFailures = $failures
        excludedProofreading = @($results | ForEach-Object { $_.excludedProofreading })
        navigationHints = @($results | ForEach-Object { $_.navigationHints })
        analyzed = $summary.analyzed; cached = $summary.cached; gradleExitCode = $summary.gradleExitCode
    }
    [IO.File]::WriteAllText((Join-Path $reportDir 'ide-inspections.txt'), (($results | Sort-Object file | ForEach-Object { $_.raw }) -join "`r`n"))
    $diff = Invoke-CheckedProcess 'git.exe' @('-c', 'core.safecrlf=false', 'diff', '--check') $root 30
    [IO.File]::WriteAllText((Join-Path $reportDir 'diff-check.txt'), $diff.Output)
    if ((Get-ProjectValidationState $root).Hash -ne $initialState.Hash) {
        throw 'Project inputs changed during validation. Rerun against a stable working tree.'
    }
    foreach ($control in $controlHashes.Keys) {
        if (-not (Test-Path -LiteralPath $control) -or (Get-ContentHash $control) -ne $controlHashes[$control]) {
            throw "Inspection/build configuration changed during validation: $control"
        }
    }
    $finalCheck = Invoke-CheckedProcess $AndroidCli @('studio', 'check') $root 30
    if ($finalCheck.ExitCode -ne 0 -or (Remove-Ansi $finalCheck.Output).Trim() -cne $status.Trim() -or
        (Get-ContentHash $AndroidCli) -ne $cliHash) {
        throw 'Studio/CLI state changed during validation. Finish sync/indexing and rerun.'
    }
    $summary.status = if ($summary.gradleExitCode -notin @($null, 0) -or $findings.Count -gt 0 -or $failures.Count -gt 0 -or $diff.ExitCode -ne 0) { 'FAIL' }
        elseif ($IdeOnly) { 'PASS_IDE_ONLY' } else { 'PASS' }
    $exitCode = if ($summary.status -eq 'FAIL') { 1 } else { 0 }
} catch {
    $summary.status = 'BLOCKED'
    if ($summary.ideStatus -eq 'PENDING') { $summary.ideStatus = 'BLOCKED' }
    $summary.blocker = $_.Exception.Message
    Write-Host "Validation BLOCKED: $($summary.blocker)"
} finally {
    $summary.finishedAt = Get-Date -Format o
    $summary.seconds = [Math]::Round($watch.Elapsed.TotalSeconds, 2)
    if ($lock) {
        Write-AtomicJson $summaryPath $summary
        $lock.Dispose()
    }
    Pop-Location
    if ($Mode -eq 'Quick') {
        Write-Host "$($summary.status) [Quick]: debug build/unit tests; lint and IDE skipped; $($summary.seconds)s."
    } else {
        Write-Host "$($summary.status) [$Mode]: IDE $($summary.ideStatus); $($summary.analyzed) inspected, $($summary.cached) cached, $($summary.findings) findings, $($summary.analysisFailures) analysis failures; $($summary.seconds)s."
    }
    Write-Host "Reports: $reportDir"
}
exit $exitCode
