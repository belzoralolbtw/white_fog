# White Fog — bounded client smoke (hotfix/regression fix этапа 1.3).
# Запускает `gradlew runClient` в ДОЧЕРНЕМ процессе, ждёт в логе dev-маркер клиентского
# инициализатора и результат bounded self-check клиентского миксина
# {"White Fog: client initializer ready (stage 1.3 regression fix), MultiPlayerGameModeMixin applied=true"},
# затем убивает ТОЛЬКО своё подтверждённое дерево процессов по сохранённому PID
# (taskkill /PID <rootPid> /T /F). Никаких sweep-обходов чужих java/cmd — нельзя убить игру
# пользователя или другую сборку. Внутренний hard-timeout 180 c; завершается всегда
# (try/finally), пишет result-файл и возвращает ненулевой exit code при статусе != SUCCESS.
#
# ВАЖНО: это smoke инициализации/применения миксина, а НЕ доказательство геймплея.
# `MultiPlayerGameMode` в ваниле создаётся только при входе в мир, поэтому self-check
# (dev-only, в WhiteFogClient) принудительно загружает класс и по reflection проверяет,
# что инъецированные обработчики попали в целевой класс.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$logs = Join-Path $root 'logs'
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }

$stamp = (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$log = Join-Path $logs "client_smoke_$stamp.txt"
$result = Join-Path $logs "client_smoke_$stamp.result"

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
        if ($needle -eq 'HUD_SUCCESS') { return $text -match 'WHITEFOG_HUD_SELFTEST widgets=\d+ registered=true status=SUCCESS' }
        return $text.Contains($needle)
    } catch {
        return $false
    }
}

# Точные маркеры провала применения миксина/краха клиента (raw error остаётся в логе).
$failureNeedles = @(
    'MultiPlayerGameModeMixin applied=false',
    'client mixin self-check failed',
    'Mixin apply failed',
    'MixinApplyError',
    'Mixin transformation of net.minecraft.client.multiplayer.MultiPlayerGameMode failed',
    'A mod crashed on startup',
    'Failed to create window',
    'GLFW error',
    'EXCEPTION_ACCESS_VIOLATION'
)

try {
    $inner = "chcp 65001 >nul & set CI=1 & gradlew.bat runClient --no-daemon --console=plain > `"$log`" 2>&1"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList "/c $inner" -PassThru -WindowStyle Hidden
    $rootPid = $proc.Id
    Write-Output "[client-smoke] launched cmd pid=$rootPid, log=$log, timeout=${timeoutSec}s"

    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if ((Test-LogContains $log 'WHITEFOG_SHELTER_SELFTEST') -and (Test-LogContains $log 'status=FAILURE')) {
            $status = 'FAILURE'
            break
        }
        if ((Test-LogContains $log 'WHITEFOG_HUD_SELFTEST') -and (Test-LogContains $log 'status=FAILURE')) {
            $status = 'FAILURE'
            break
        }
        if ((Test-LogContains $log 'MultiPlayerGameModeMixin applied=true') -and (Test-LogContains $log 'SHELTER_SUCCESS') -and (Test-LogContains $log 'HUD_SUCCESS')) {
            $status = 'SUCCESS'
            break
        }
        # applied=false / self-check error побеждает, даже если строка client initializer уже есть.
        if (Test-LogContains $log 'MultiPlayerGameModeMixin applied=false') {
            $status = 'FAILURE'
            break
        }
        $failed = $false
        foreach ($needle in $failureNeedles) {
            if (Test-LogContains $log $needle) { $failed = $true; break }
        }
        if ($failed) { $status = 'FAILURE'; break }
        if ($proc.HasExited) {
            # Процесс завершился без успешного маркера (краш/неожиданный выход).
            if ($status -eq 'TIMEOUT') { $status = 'FAILURE' }
            break
        }
    }
} catch {
    $status = 'FAILURE'
    Write-Output "[client-smoke] exception: $_"
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

    Write-Output "[client-smoke] status=$status"
    if (Test-Path $log) {
        Write-Output "----- tail of $log -----"
        Get-Content $log -Tail 25
    }
    Write-Output "result written: $result"
}

if ($status -eq 'SUCCESS') { exit 0 } else { exit 1 }
