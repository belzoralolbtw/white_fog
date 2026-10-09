param(
    [ValidateSet('selftest')][string]$Mode = 'selftest',
    [ValidateSet('normal', 'failure', 'timeout')][string]$Probe = 'normal'
)
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$logDir = Join-Path $root 'logs'
[IO.Directory]::CreateDirectory($logDir) | Out-Null
$stamp = (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$prefix = 'dark_spawn_selftest'
$log = Join-Path $logDir ($prefix + '_' + $stamp + '.txt')
$result = [IO.Path]::ChangeExtension($log, '.result')
$watch = [Diagnostics.Stopwatch]::StartNew()
$status = 'FAILURE'
$exitCode = 1
$writer = New-Object IO.StreamWriter($log, $false, $utf8)
$writer.AutoFlush = $true
$budgetMs = if ($Probe -eq 'timeout') { 1500 } else { 55000 }

function Invoke-OwnedProcess([string]$exe, [string]$arguments) {
    $remaining = $budgetMs - [int]$watch.ElapsedMilliseconds
    if ($remaining -le 0) { throw [TimeoutException]::new('Internal run budget exhausted') }
    $p = New-Object Diagnostics.Process
    $p.StartInfo = New-Object Diagnostics.ProcessStartInfo
    $p.StartInfo.FileName = $exe
    $p.StartInfo.Arguments = $arguments
    $p.StartInfo.WorkingDirectory = $root
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.CreateNoWindow = $true
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    $p.StartInfo.StandardOutputEncoding = $utf8
    $p.StartInfo.StandardErrorEncoding = $utf8
    try {
        if (-not $p.Start()) { throw 'Process did not start' }
        $writer.WriteLine("PROCESS pid=$($p.Id) exe=$exe args=$arguments elapsed_ms=$($watch.ElapsedMilliseconds)")
        $stdout = $p.StandardOutput.ReadToEndAsync()
        $stderr = $p.StandardError.ReadToEndAsync()
        if (-not $p.WaitForExit($remaining)) {
            $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
            if ($null -ne $owned -and $owned.ExecutablePath -eq $exe) {
                $writer.WriteLine("TIMEOUT owned_pid=$($p.Id) command_line=$($owned.CommandLine)")
                & "$env:SystemRoot\System32\taskkill.exe" /PID $p.Id /T /F | ForEach-Object { $writer.WriteLine($_) }
                if (-not $p.WaitForExit(2000)) { throw 'Owned process failed to terminate' }
            } elseif (-not $p.HasExited) {
                throw 'Cannot verify owned process executable for timeout cleanup'
            }
            $writer.Write($stdout.GetAwaiter().GetResult())
            $writer.Write($stderr.GetAwaiter().GetResult())
            throw [TimeoutException]::new('Internal foreground watchdog timeout')
        }
        $p.WaitForExit()
        $writer.Write($stdout.GetAwaiter().GetResult())
        $writer.Write($stderr.GetAwaiter().GetResult())
        $writer.WriteLine("PROCESS exit=$($p.ExitCode) elapsed_ms=$($watch.ElapsedMilliseconds)")
        if ($p.ExitCode -ne 0) { throw "Process exit=$($p.ExitCode): $exe" }
    } finally { $p.Dispose() }
}

try {
    $writer.WriteLine("DARK_SPAWN mode=$Mode probe=$Probe started=$(Get-Date -Format o) budget_ms=$budgetMs")
    $writer.WriteLine('note=logic-only sandbox, NOT runtime/live-spawn proof')
    $jdk = Join-Path $env:USERPROFILE 'jdk-26.0.2.1\bin'
    if ($Probe -eq 'failure') { throw 'Intentional runner failure-path probe' }
    if ($Probe -eq 'timeout') {
        Invoke-OwnedProcess (Join-Path $PSHOME 'powershell.exe') '-NoProfile -NonInteractive -Command "Start-Sleep -Seconds 10"'
    } else {
        $src = Join-Path $PSScriptRoot 'src'
        $out = Join-Path $PSScriptRoot ('build\' + $stamp)
        [IO.Directory]::CreateDirectory($out) | Out-Null
        $sources = @(Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
        # Compile the production pure policy/state themselves, read-only — not duplicated formulas.
        $sources += '"' + (Join-Path $root 'src\main\java\com\whitefog\darkness\spawn\DarkSpawnPolicy.java').Replace('\', '/') + '"'
        $sources += '"' + (Join-Path $root 'src\main\java\com\whitefog\darkness\spawn\DarkMobState.java').Replace('\', '/') + '"'
        $writer.WriteLine('implementation=production pure DarkSpawnPolicy + DarkMobState compiled read-only; no duplicated algorithm')
        $argfile = Join-Path $out 'sources.txt'
        [IO.File]::WriteAllLines($argfile, $sources, $utf8)
        Invoke-OwnedProcess (Join-Path $jdk 'javac.exe') "-J-Xmx128m --release 25 -encoding UTF-8 -Xlint:all -Werror -d `"$out`" @`"$argfile`""
        Invoke-OwnedProcess (Join-Path $jdk 'java.exe') "-Xmx128m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp `"$out`" com.whitefog.tests.spawn.SelfTest"
    }
    $status = 'SUCCESS'
    $exitCode = 0
} catch [TimeoutException] {
    $status = 'TIMEOUT'
    $exitCode = 124
    $writer.WriteLine($_.Exception.ToString())
    $writer.WriteLine($_.ScriptStackTrace)
} catch {
    $writer.WriteLine($_.Exception.ToString())
    $writer.WriteLine($_.ScriptStackTrace)
} finally {
    $watch.Stop()
    $writer.WriteLine("status=$status elapsed_ms=$($watch.ElapsedMilliseconds) finished=$(Get-Date -Format o)")
    $writer.Dispose()
    [IO.File]::WriteAllLines($result, @("status=$status", "elapsed_ms=$($watch.ElapsedMilliseconds)", "exit=$exitCode", "mode=$Mode", "probe=$Probe", "log=$log"), $utf8)
    "status=$status elapsed_ms=$($watch.ElapsedMilliseconds) log=$log result=$result"
}
exit $exitCode
