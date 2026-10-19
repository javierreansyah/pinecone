param([switch]$IncludeProcessTests)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path (Split-Path $PSScriptRoot -Parent) 'Validation.psm1') -Force
$count = 0
function Assert-Validation {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Validation self-test failed: $Message" }
    $script:count++
}

$absolute = 'C:\project with spaces\app\File.kt'
$prefix = "Analyzing file: $absolute`n"
$diagnostic = "INFO in $absolute`nline: 2, column: 0`nmessage: Missing trailing comma`n`n----------------------------------------`n"
$parsed = ConvertFrom-IdeOutput ($prefix + $diagnostic) 'app/File.kt' $absolute
Assert-Validation ($parsed.Valid -and $parsed.Findings.Count -eq 1) 'INFO style findings must remain blocking'
$clean = ConvertFrom-IdeOutput ($prefix + 'No issues found!') 'app/File.kt' $absolute
Assert-Validation ($clean.Valid -and $clean.Findings.Count -eq 0) 'recognize a genuine clean response'
$ansi = [char]27
$colored = ConvertFrom-IdeOutput ($prefix + "${ansi}[34m" + $diagnostic + "${ansi}[0m") 'app/File.kt' $absolute
Assert-Validation ($colored.Valid -and $colored.Findings.Count -eq 1) 'strip terminal color codes'
$unknown = ConvertFrom-IdeOutput ($prefix + 'something unexpected') 'app/File.kt' $absolute
Assert-Validation (-not $unknown.Valid) 'unknown CLI output must not pass'
$truncated = ConvertFrom-IdeOutput ($prefix + "WARNING in $absolute`nmessage: incomplete") 'app/File.kt' $absolute
Assert-Validation (-not $truncated.Valid) 'truncated diagnostics must not pass'
$wrong = ConvertFrom-IdeOutput ($prefix + 'No issues found!') 'app/File.kt' 'C:\other\File.kt'
Assert-Validation (-not $wrong.Valid) 'another file must not count as validated'
$mixed = ConvertFrom-IdeOutput ($prefix + $diagnostic + 'No issues found!') 'app/File.kt' $absolute
Assert-Validation (-not $mixed.Valid) 'contradictory clean/diagnostic output must not pass'
$typo = ConvertFrom-IdeOutput ($prefix + ($diagnostic -replace 'Missing trailing comma', "Typo: In word 'pinecone'")) 'app/File.kt' $absolute
Assert-Validation ($typo.Valid -and $typo.Findings.Count -eq 0 -and $typo.Excluded.Count -eq 1) 'record proofreading separately'
$link = ConvertFrom-IdeOutput ($prefix + ($diagnostic -replace 'Missing trailing comma', 'Open in browser (Ctrl+Click, Ctrl+B)')) 'app/File.kt' $absolute
Assert-Validation ($link.Valid -and $link.Navigation.Count -eq 1) 'record URL navigation hints separately'

$entry = [pscustomobject]@{
    file = 'File.kt'; valid = $true; hash = 'one'; context = 'profile'
    findings = @('an unresolved warning'); raw = 'inspection output'
    excludedProofreading = @(); navigationHints = @()
}
Assert-Validation (Test-ReusableIdeResult $entry 'one' 'profile') 'reuse unchanged diagnostics, including unresolved findings'
Assert-Validation ($entry.findings.Count -eq 1) 'cache reuse must retain existing findings'
Assert-Validation (-not (Test-ReusableIdeResult $entry 'two' 'profile')) 'content changes invalidate cache'
Assert-Validation (-not (Test-ReusableIdeResult $entry 'one' 'new-profile')) 'profile/toolchain changes invalidate cache'
Assert-Validation (-not (Test-ReusableIdeResult $entry 'one' 'profile' -Fresh)) 'Complete must inspect even unchanged files'
$entry.valid = $false
Assert-Validation (-not (Test-ReusableIdeResult $entry 'one' 'profile')) 'failed analyses must be retried'
Assert-Validation (-not (Test-ReusableIdeResult $null 'one' 'profile')) 'cold cache requires analysis'
$malformed = [pscustomobject]@{ valid = $true; context = 'profile' }
Assert-Validation (-not (Test-ReusableIdeResult $malformed 'one' 'profile')) 'malformed cache entries must be reinspected'
$quick = @(Get-ValidationTasks Quick)
$diff = @(Get-ValidationTasks Diff)
$complete = @(Get-ValidationTasks Complete)
Assert-Validation ($quick.Count -eq 2 -and $quick -contains ':app:assembleDebug' -and $quick -contains ':app:testDebugUnitTest' -and @($quick | Where-Object { $_ -match 'lint' }).Count -eq 0) 'Quick runs only debug build and unit tests, never lint'
Assert-Validation ((@(Get-ValidationTasks) -join '|') -eq ($quick -join '|')) 'omitting mode must select Quick tasks'
Assert-Validation ($diff.Count -eq 3 -and $diff -contains ':app:lintDebug' -and $diff -notcontains ':app:assembleRelease') 'Diff adds debug lint without other variants'
Assert-Validation ($complete.Count -eq 9) 'Complete must request nine build/test/lint tasks'
foreach ($variant in 'Debug', 'Release', 'Staging') {
    Assert-Validation ($complete -contains ":app:assemble$variant" -and $complete -contains ":app:test${variant}UnitTest" -and $complete -contains ":app:lint$variant") "Complete covers $variant builds, tests, and lint"
}
Assert-Validation ((ConvertTo-NativeArgument 'C:\path with spaces\') -eq '"C:\path with spaces\\"') 'quote trailing backslashes safely'
Assert-Validation ((ConvertTo-NativeArgument 'say "hello"') -eq '"say \"hello\""') 'quote embedded quotes safely'

if ($IncludeProcessTests) {
    $temporary = Join-Path $env:TEMP ("reader-validation-tests-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $temporary | Out-Null
    $oldFixtureRoot = $env:VALIDATION_TEST_ROOT
    $oldFixtureFinding = $env:VALIDATION_TEST_FINDING
    try {
        $json = Join-Path $temporary 'atomic.json'
        Write-AtomicJson $json @{ value = 1 }
        Write-AtomicJson $json @{ value = 2 }
        Assert-Validation ((Get-Content $json -Raw | ConvertFrom-Json).value -eq 2) 'atomic report replacement'
        $fixture = Join-Path $temporary 'fixture.ps1'
        @'
if ($args[0] -eq 'fail') { [Console]::Error.WriteLine('expected stderr'); exit 7 }
if ($args[0] -eq 'sleep') { Start-Sleep -Seconds 20; exit 0 }
if ($args[0] -eq 'echo-args') { $args | Select-Object -Skip 1 | ForEach-Object { Write-Output $_ }; exit 0 }
if ($args[0] -eq 'studio' -and $args[1] -eq 'check') {
    Write-Output "READY Test $env:VALIDATION_TEST_ROOT"
    exit 0
}
if ($args[0] -eq 'studio' -and $args[1] -eq 'analyze-file') {
    $path = Join-Path $args[3] $args[4]
    Write-Output "Analyzing file: $path"
    if ($env:VALIDATION_TEST_FINDING -eq '1') {
        Write-Output "INFO in $path"
        Write-Output 'line: 1, column: 0'
        Write-Output 'message: Missing trailing comma'
        Write-Output '----------------------------------------'
    } else { Write-Output 'No issues found!' }
    exit 0
}
exit 9
'@ | Set-Content $fixture -Encoding UTF8
        $cli = Join-Path $temporary 'mock cli.cmd'
        "@echo off`r`n`"$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe`" -NoProfile -ExecutionPolicy Bypass -File `"$fixture`" %*" | Set-Content $cli -Encoding ASCII
        $failed = Invoke-CheckedProcess $cli @('fail') $temporary 10
        Assert-Validation ($failed.ExitCode -eq 7 -and $failed.Output -match 'expected stderr') 'native stderr must not hide a failure or abort orchestration'
        $echo = Invoke-CheckedProcess $cli @('echo-args', 'a path with spaces', 'trailing\') $temporary 10
        Assert-Validation ($echo.ExitCode -eq 0 -and $echo.Output -match 'a path with spaces' -and $echo.Output -match 'trailing\\') 'native argument round trip'
        $timeout = Invoke-CheckedProcess $cli @('sleep') $temporary 1
        Assert-Validation ($timeout.TimedOut -and $timeout.ExitCode -ne 0 -and $timeout.Seconds -lt 10) 'bounded timeout and process-tree cleanup'
        $items = @('one.kt', 'two.kt') | ForEach-Object {
            [IO.File]::WriteAllText((Join-Path $temporary $_), 'val value = 1')
            [pscustomobject]@{ file = $_; hash = Get-ContentHash (Join-Path $temporary $_) }
        }
        $results = @(Invoke-IdeQueue $items $cli $temporary 2 15)
        Assert-Validation ($results.Count -eq 2 -and @($results | Where-Object { -not $_.valid }).Count -eq 0) 'bounded parallel inspection returns every file'
        $items[0].hash = 'old-content'
        $changed = Invoke-IdeFile $cli $temporary $items[0].file $items[0].hash 15
        Assert-Validation (-not $changed.valid -and $changed.failure -match 'changed during analysis') 'never cache a result for changed contents'

        # End-to-end orchestration tests use an isolated Git repo and fake Gradle/IDE.
        # They never build, lint, or inspect the actual application.
        $project = Join-Path $temporary 'fixture project'
        $projectScripts = Join-Path $project 'scripts'
        $projectTests = Join-Path $projectScripts 'tests'
        New-Item -ItemType Directory -Force $projectTests | Out-Null
        $scriptsRoot = Split-Path $PSScriptRoot -Parent
        Copy-Item (Join-Path $scriptsRoot 'validate.ps1') $projectScripts
        Copy-Item (Join-Path $scriptsRoot 'Validation.psm1') $projectScripts
        Copy-Item $PSCommandPath $projectTests
        '/build' | Set-Content (Join-Path $project '.gitignore') -Encoding ASCII
        [IO.File]::WriteAllText((Join-Path $project 'one.kt'), "val first = 1`nval second = 2")
        $argumentsLog = Join-Path $temporary 'gradle-arguments.txt'
        $wrapper = Join-Path $project 'gradlew.bat'
        "@echo off`r`necho %*>`"$argumentsLog`"`r`nexit /b 0" | Set-Content $wrapper -Encoding ASCII
        $initialized = Invoke-CheckedProcess 'git.exe' @('init', '--quiet') $project 10
        Assert-Validation ($initialized.ExitCode -eq 0) 'initialize isolated fixture repository'
        $runner = Join-Path $projectScripts 'validate.ps1'
        $powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
        $baseArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner)
        $projectSummary = Join-Path $project 'build/reports/validation/summary.json'
        $projectCache = Join-Path $project 'build/reports/validation/ide-cache.json'
        $missingCli = Join-Path $temporary 'not-installed.exe'
        $defaultRun = Invoke-CheckedProcess $powershell ($baseArguments + @('-AndroidCli', $missingCli)) $project 30
        $defaultSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($defaultRun.ExitCode -eq 0 -and $defaultSummary.mode -eq 'Quick' -and $defaultSummary.status -eq 'PASS') 'default Quick passes without Studio or an installed CLI'
        Assert-Validation ($defaultSummary.ideStatus -eq 'SKIPPED' -and $null -eq $defaultSummary.findings -and -not (Test-Path $projectCache)) 'Quick skips IDE results/cache rather than claiming zero findings'
        $quickArguments = Get-Content $argumentsLog -Raw
        Assert-Validation ($quickArguments -match ':app:assembleDebug' -and $quickArguments -match ':app:testDebugUnitTest' -and $quickArguments -notmatch 'lint|Release|Staging') 'Quick actually invokes only build/test tasks'
        $invalid = Invoke-CheckedProcess $powershell ($baseArguments + @('-IdeOnly')) $project 30
        Assert-Validation ($invalid.ExitCode -eq 2) 'Quick cannot silently turn into an inspection-only pass'

        # Even after Quick has passed, a failing build/test invocation must fail Quick.
        "@echo off`r`nexit /b 7" | Set-Content $wrapper -Encoding ASCII
        $buildFailure = Invoke-CheckedProcess $powershell $baseArguments $project 30
        $failedSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($buildFailure.ExitCode -eq 1 -and $failedSummary.gradleExitCode -eq 7) 'Quick propagates failed build/test exit codes'
        "@echo off`r`necho %*>`"$argumentsLog`"`r`nexit /b 0" | Set-Content $wrapper -Encoding ASCII

        $env:VALIDATION_TEST_ROOT = $project
        $env:VALIDATION_TEST_FINDING = '1'
        $diffArguments = $baseArguments + @('-Mode', 'Diff', '-AndroidCli', $cli)
        $coldDiff = Invoke-CheckedProcess $powershell $diffArguments $project 45
        $coldSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($coldDiff.ExitCode -eq 1 -and $coldSummary.analyzed -eq 1 -and $coldSummary.findings -eq 1) 'Diff inspects a cold fixture and blocks its INFO finding'
        $env:VALIDATION_TEST_FINDING = '0'
        $warmDiff = Invoke-CheckedProcess $powershell $diffArguments $project 45
        $warmSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($warmDiff.ExitCode -eq 1 -and $warmSummary.analyzed -eq 0 -and $warmSummary.cached -eq 1 -and $warmSummary.findings -eq 1) 'Diff retains cached findings without per-file CLI calls'
        $cacheBeforeQuick = Get-ContentHash $projectCache
        $quickWithFindings = Invoke-CheckedProcess $powershell ($baseArguments + @('-AndroidCli', $missingCli)) $project 30
        $quickWithFindingsSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($quickWithFindings.ExitCode -eq 0 -and $quickWithFindingsSummary.ideStatus -eq 'SKIPPED' -and $null -eq $quickWithFindingsSummary.findings) 'Quick passes its own scope despite unresolved cached IDE findings'
        Assert-Validation ((Get-ContentHash $projectCache) -eq $cacheBeforeQuick) 'Quick must not rewrite or clear the existing IDE cache'
        $completeRun = Invoke-CheckedProcess $powershell ($baseArguments + @('-Mode', 'Complete', '-AndroidCli', $cli)) $project 45
        $completeSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($completeRun.ExitCode -eq 0 -and $completeSummary.analyzed -eq 1 -and $completeSummary.cached -eq 0 -and $completeSummary.findings -eq 0) 'Complete freshly inspects even unchanged fixture files'
        $completeArguments = Get-Content $argumentsLog -Raw
        foreach ($task in $complete) {
            Assert-Validation ($completeArguments.Contains($task)) "Complete actually invokes $task in the fixture"
        }
        [IO.File]::AppendAllText((Join-Path $project 'one.kt'), "`nval third = 3")
        $changedDiff = Invoke-CheckedProcess $powershell $diffArguments $project 45
        $changedSummary = Get-Content $projectSummary -Raw | ConvertFrom-Json
        Assert-Validation ($changedDiff.ExitCode -eq 0 -and $changedSummary.analyzed -eq 1) 'Diff reinspects changed file contents'
        $disconnected = Invoke-CheckedProcess $powershell ($baseArguments + @('-Mode', 'Diff', '-AndroidCli', $missingCli)) $project 30
        Assert-Validation ($disconnected.ExitCode -eq 2) 'Diff cannot pass disconnected merely because a cache exists'
    } finally {
        $env:VALIDATION_TEST_ROOT = $oldFixtureRoot
        $env:VALIDATION_TEST_FINDING = $oldFixtureFinding
        # This unique directory contains only fixtures and Git metadata created here.
        [IO.Directory]::Delete($temporary, $true)
    }
}
Write-Host "Validation self-tests: $count passed."
