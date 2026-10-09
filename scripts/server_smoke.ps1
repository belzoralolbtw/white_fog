# White Fog — smoke test dedicated server.
# Запускает `gradlew runServer` в ДОЧЕРНЕМ процессе, ждёт строку "Done (" во внутреннем
# hard-timeout, затем убивает ТОЛЬКО своё подтверждённое дерево процессов по сохранённому PID
# (taskkill /PID <rootPid> /T /F). Никаких sweep-обходов чужих java/cmd — нельзя убить игру
# пользователя или другую сборку. Завершается всегда (try/finally), пишет result-файл и
# возвращает ненулевой exit code при статусе, отличном от SUCCESS.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$logs = Join-Path $root 'logs'
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }

$stamp = (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$log = Join-Path $logs "server_smoke_$stamp.txt"
$result = Join-Path $logs "server_smoke_$stamp.result"

$timeoutSec = 180
$watch = [Diagnostics.Stopwatch]::StartNew()
$status = 'TIMEOUT'
$proc = $null
$rootPid = -1

# Чтение лога, который в данный момент пишет cmd: открываем с FileShare.ReadWrite.
function Test-LogContains([string]$path, [string]$needle) {
    if (-not (Test-Path $path)) { return $false }
    try {
        $fs = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $sr = New-Object System.IO.StreamReader($fs)
            try { $text = $sr.ReadToEnd() } finally { $sr.Dispose() }
        } finally { $fs.Dispose() }
        if ($needle -eq 'SHELTER_SUCCESS') { return $text -match 'WHITEFOG_SHELTER_SELFTEST assertions=\d+ handlers=true elapsed_ms=\d+ status=SUCCESS' }
        return $text.Contains($needle)
    } catch {
        return $false
    }
}

try {
    $inner = "chcp 65001 >nul & gradlew.bat runServer --no-daemon --console=plain > `"$log`" 2>&1"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList "/c $inner" -PassThru -WindowStyle Hidden
    $rootPid = $proc.Id
    Write-Output "[smoke] launched cmd pid=$rootPid, log=$log, timeout=${timeoutSec}s"

    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if ((Test-LogContains $log 'WHITEFOG_SHELTER_SELFTEST') -and (Test-LogContains $log 'status=FAILURE')) {
            $status = 'FAILURE'
            break
        }
        if ((Test-LogContains $log 'Done (') -and (Test-LogContains $log 'SHELTER_SUCCESS')) {
            $status = 'SUCCESS'
            break
        }
        if (Test-LogContains $log 'Cannot register handler') {
            $status = 'FAILURE'
            break
        }
        if (Test-LogContains $log 'Registry remapping failed') {
            $status = 'FAILURE'
            break
        }
        if (Test-LogContains $log 'A mod crashed') {
            $status = 'FAILURE'
            break
        }
        if ($proc.HasExited) {
            if ($status -eq 'TIMEOUT') { $status = 'FAILURE' }
            break
        }
    }
} catch {
    $status = 'FAILURE'
    Write-Output "[smoke] exception: $_"
} finally {
    # Завершаем ТОЛЬКО собственное дерево процессов по подтверждённому PID запуска.
    # Никаких выборок чужих java/cmd по маске командной строки.
    if ($null -ne $proc -and -not $proc.HasExited) {
        try {
            $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$rootPid"
            if ($null -eq $owned -or -not $owned.CommandLine.Contains($log)) { throw 'Cannot verify owned smoke command' }
            & taskkill /PID $rootPid /T /F 2>&1 | Out-Null
            if (-not $proc.WaitForExit(2000)) { throw 'Owned smoke tree did not exit' }
        } catch { $status = 'FAILURE'; $_.Exception.ToString() | Add-Content -Encoding utf8 $log }
    }
    Start-Sleep -Seconds 4

    "status=$status" | Set-Content -Encoding utf8 $result
    "rootPid=$rootPid" | Add-Content -Encoding utf8 $result
    "elapsed_ms=$($watch.ElapsedMilliseconds)" | Add-Content -Encoding utf8 $result
    "log=$log" | Add-Content -Encoding utf8 $result

    Write-Output "[smoke] status=$status"
    if (Test-Path $log) {
        Write-Output "----- tail of $log -----"
        Get-Content $log -Tail 25
    }
    Write-Output "result written: $result"
}

if ($status -eq 'SUCCESS') { exit 0 } else { exit 1 }
