Set-StrictMode -Version 2.0

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string]$Value)
    # Windows CommandLineToArgvW quoting, including quotes and trailing slashes.
    '"' + ([regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1')) + '"'
}

function Invoke-CheckedProcess {
    param(
        [string]$Program, [string[]]$Arguments = @(), [string]$WorkingDirectory,
        [int]$TimeoutSeconds = 120
    )
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = New-Object Diagnostics.Process
    try {
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $Program
        $quoted = @($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
        if ([IO.Path]::GetExtension($Program) -in '.bat', '.cmd') {
            $start.FileName = $env:ComSpec
            $start.Arguments = '/d /s /c "' + (ConvertTo-NativeArgument $Program) + ' ' + $quoted + '"'
        } else {
            $start.Arguments = $quoted
        }
        $start.WorkingDirectory = $WorkingDirectory
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $process.StartInfo = $start
        if (-not $process.Start()) { throw "Could not start $Program" }
        # Drain both streams concurrently so a full stderr pipe cannot deadlock.
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
        if ($timedOut) {
            # Only terminate this invocation and its descendants, not other IDEs/builds.
            & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F 2>&1 | Out-Null
            if (-not $process.WaitForExit(5000)) { $process.Kill() }
        }
        if (-not $stdout.Wait(5000) -or -not $stderr.Wait(5000)) {
            throw "Output streams did not close for $Program"
        }
        [pscustomobject]@{
            ExitCode = if ($timedOut) { -1 } else { $process.ExitCode }
            TimedOut = $timedOut
            Output = $stdout.Result + $stderr.Result
            Seconds = [Math]::Round($watch.Elapsed.TotalSeconds, 3)
        }
    } catch {
        [pscustomobject]@{ ExitCode = -1; TimedOut = $false; Output = $_.Exception.Message; Seconds = $watch.Elapsed.TotalSeconds }
    } finally {
        $process.Dispose()
    }
}

function Remove-Ansi {
    param([string]$Text)
    $Text -replace '\x1B\[[0-?]*[ -/]*[@-~]', ''
}

function ConvertFrom-IdeOutput {
    param([string]$Text, [string]$File, [string]$AbsolutePath)
    $text = Remove-Ansi $Text
    $pattern = '(?ms)^(?<severity>ERROR|WARNING|WEAK WARNING|INFO) in [^\r\n]+\r?\nline:\s*(?<line>\d+),\s*column:\s*(?<column>\d+)\r?\nmessage:\s*(?<message>.*?)(?=\r?\n-+|\z)'
    $diagnostics = [regex]::Matches($text, $pattern)
    $headers = [regex]::Matches($text, '(?m)^[A-Z][A-Z _]* in ').Count
    $clean = $text -match '(?m)^No issues found!\s*$'
    $identity = [regex]::Match($text, '(?m)^Analyzing file:\s*(.+?)\s*$')
    $valid = $identity.Success -and
        ($identity.Groups[1].Value.Replace('\', '/') -ieq $AbsolutePath.Replace('\', '/')) -and
        ($headers -eq $diagnostics.Count) -and
        (($clean -and $diagnostics.Count -eq 0) -or (-not $clean -and $diagnostics.Count -gt 0))
    $findings = @()
    $excluded = @()
    $navigation = @()
    foreach ($diagnostic in $diagnostics) {
        $item = [pscustomobject]@{
            file = $File; severity = $diagnostic.Groups['severity'].Value
            line = [int]$diagnostic.Groups['line'].Value
            column = [int]$diagnostic.Groups['column'].Value
            message = $diagnostic.Groups['message'].Value.Trim()
        }
        if ($item.message -match '(?i)^(Typo:|Spelling mistake:|Grammar error:)') {
            $excluded += $item
        } elseif ($item.severity -eq 'INFO' -and $item.message -eq 'Open in browser (Ctrl+Click, Ctrl+B)') {
            $navigation += $item
        } else {
            $findings += $item
        }
    }
    [pscustomobject]@{ Valid = $valid; Findings = $findings; Excluded = $excluded; Navigation = $navigation; Text = $text }
}

function Get-ContentHash {
    param([string]$Path)
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Get-TextHash {
    param([string]$Text)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).Replace('-', '') }
    finally { $algorithm.Dispose() }
}

function Write-AtomicJson {
    param([string]$Path, $Value)
    $temporary = "$Path.$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        [IO.File]::WriteAllText($temporary, ($Value | ConvertTo-Json -Depth 14), (New-Object Text.UTF8Encoding($false)))
        if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temporary, $Path, [NullString]::Value) }
        else { [IO.File]::Move($temporary, $Path) }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
    }
}

function Get-ValidationTasks {
    param([ValidateSet('Quick', 'Diff', 'Complete')][string]$Mode = 'Quick')
    if ($Mode -eq 'Quick') {
        @(':app:assembleDebug', ':app:testDebugUnitTest')
    } elseif ($Mode -eq 'Diff') {
        @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
    } else {
        foreach ($variant in 'Debug', 'Release', 'Staging') {
            ":app:assemble$variant"
            ":app:test${variant}UnitTest"
            ":app:lint$variant"
        }
    }
}

function Test-ReusableIdeResult {
    param($Entry, [string]$Hash, [string]$Context, [switch]$Fresh)
    if ($Fresh -or $null -eq $Entry) { return $false }
    foreach ($name in 'valid', 'hash', 'context', 'file', 'findings', 'raw', 'excludedProofreading', 'navigationHints') {
        if ($Entry.PSObject.Properties.Name -notcontains $name) { return $false }
    }
    # Failed/incomplete analysis is never reusable. Findings ARE reusable and stay blocking.
    ($Entry.valid -is [bool]) -and $Entry.valid -eq $true -and
        $Entry.hash -eq $Hash -and $Entry.context -eq $Context
}

function Get-ProjectValidationState {
    param([string]$Root)
    $listing = Invoke-CheckedProcess 'git.exe' @('ls-files', '--cached', '--others', '--exclude-standard') $Root 30
    if ($listing.ExitCode -ne 0) { throw "Cannot enumerate project files: $($listing.Output)" }
    $files = @($listing.Output -split '\r?\n' | Where-Object { $_ } | Sort-Object -Unique)
    $checksums = foreach ($file in $files) {
        if ($file -match '(\.(kt|kts|java|xml|properties|toml|gradle|ps1|psm1|json)$|(^|/)\.editorconfig$)' -and
            (Test-Path -LiteralPath (Join-Path $Root $file) -PathType Leaf)) {
            "$file=$(Get-ContentHash (Join-Path $Root $file))"
        }
    }
    [pscustomobject]@{ Files = $files; Hash = Get-TextHash ($checksums -join "`n") }
}

function Invoke-IdeFile {
    param([string]$Cli, [string]$Root, [string]$File, [string]$Hash, [int]$TimeoutSeconds = 90)
    $absolute = Join-Path $Root $File
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $result = $null
    $parsed = $null
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        $result = Invoke-CheckedProcess $Cli @('studio', 'analyze-file', '--project', $Root, $File) $Root $TimeoutSeconds
        $parsed = ConvertFrom-IdeOutput $result.Output $File $absolute
        $stale = $false
        if ($result.ExitCode -eq 0 -and $parsed.Valid) {
            $lines = [IO.File]::ReadAllLines($absolute)
            foreach ($finding in $parsed.Findings) {
                if ($finding.line -lt 1 -or $finding.line -gt $lines.Length) { $stale = $true; break }
                $line = $lines[$finding.line - 1]
                if (($finding.message -like 'The opt-in annotation is redundant:*' -and $line -notmatch '@(?:file:)?OptIn') -or
                    ($finding.message -eq 'Missing trailing comma' -and $line -match ',\s*$')) {
                    $stale = $true; break
                }
            }
        }
        $valid = $result.ExitCode -eq 0 -and $parsed.Valid -and -not $stale
        if ($valid -or $result.TimedOut) { break }
        # Retry only failed/unrecognized/obviously stale output, not every successful file.
    }
    $unchanged = (Test-Path -LiteralPath $absolute) -and (Get-ContentHash $absolute) -eq $Hash
    [pscustomobject]@{
        file = $File; hash = $Hash; valid = ($valid -and $unchanged)
        findings = @($parsed.Findings); excludedProofreading = @($parsed.Excluded)
        navigationHints = @($parsed.Navigation); raw = $parsed.Text
        attempts = $attempt; seconds = [Math]::Round($watch.Elapsed.TotalSeconds, 3)
        failure = if (-not $unchanged) { 'File changed during analysis; rerun validation.' }
            elseif (-not $valid) { "IDE analysis failed, timed out, returned stale or unrecognized output (exit $($result.ExitCode))." }
            else { $null }
    }
}

function Invoke-IdeQueue {
    param([object[]]$Items, [string]$Cli, [string]$Root, [int]$Workers = 2, [int]$TimeoutSeconds = 90)
    if ($Items.Count -eq 0) { return }
    $pool = [RunspaceFactory]::CreateRunspacePool(1, $Workers)
    $pending = [Collections.Generic.List[object]]::new()
    $pool.Open()
    $module = $PSCommandPath
    $worker = {
        param($modulePath, $cliPath, $rootPath, $file, $hash, $timeout)
        $ErrorActionPreference = 'Stop'
        Import-Module $modulePath
        Invoke-IdeFile $cliPath $rootPath $file $hash $timeout
    }
    try {
        # At most Workers requests in flight; no process/job per file for orchestration.
        $index = 0
        while ($index -lt $Items.Count -or $pending.Count -gt 0) {
            while ($index -lt $Items.Count -and $pending.Count -lt $Workers) {
                $item = $Items[$index++]
                $ps = [PowerShell]::Create()
                $ps.RunspacePool = $pool
                [void]$ps.AddScript($worker.ToString()).AddArgument($module).AddArgument($Cli).AddArgument($Root).AddArgument($item.file).AddArgument($item.hash).AddArgument($TimeoutSeconds)
                $pending.Add([pscustomobject]@{ PowerShell = $ps; Handle = $ps.BeginInvoke(); Item = $item })
            }
            # Wait on handles, not a busy polling loop.
            $handles = [Threading.WaitHandle[]]@($pending | ForEach-Object { $_.Handle.AsyncWaitHandle })
            $completed = [Threading.WaitHandle]::WaitAny($handles)
            $job = $pending[$completed]
            try {
                $values = @($job.PowerShell.EndInvoke($job.Handle))
                if ($job.PowerShell.HadErrors -or $values.Count -ne 1) {
                    throw ($job.PowerShell.Streams.Error | Out-String)
                }
                $values[0]
            } catch {
                [pscustomobject]@{
                    file = $job.Item.file; hash = $job.Item.hash; valid = $false
                    findings = @(); excludedProofreading = @(); navigationHints = @()
                    raw = ''; attempts = 0; seconds = 0; failure = "Inspection worker failed: $_"
                }
            } finally {
                $job.PowerShell.Dispose()
                $pending.RemoveAt($completed)
            }
        }
    } finally {
        foreach ($job in $pending) { $job.PowerShell.Stop(); $job.PowerShell.Dispose() }
        $pool.Close(); $pool.Dispose()
    }
}

Export-ModuleMember -Function *
