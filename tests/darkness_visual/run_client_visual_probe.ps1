# White Fog — bounded client visual probe (darkness visual hotfix, этап 1.5).
# Запускает `gradlew runClient --no-daemon --console=plain` в foreground через cmd,
# ждёт dev-маркер WHITEFOG_DARKNESS_VISUAL_SELFTEST (миксины LightmapRenderStateExtractor/
# DarknessFogEnvironment применены), затем завершает ТОЛЬКО своё PID-дерево
# (taskkill /PID <rootPid> /T /F). Никаких sweep-обходов чужих java/cmd.
# Внутренний hard-timeout 180 c; очистка/запись результата в try/finally.
# Результат: logs\darkness_visual_probe_<stamp>.result со status=SUCCESS|TIMEOUT|FAILURE.
# ВАЖНО: доказывает только инициализацию/применение миксинов, НЕ пиксели.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$logs = Join-Path $root 'logs'
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$log = Join-Path $logs "darkness_visual_probe_$stamp.txt"
$result = Join-Path $logs "darkness_visual_probe_$stamp.result"

$timeoutSec = 180
$status = 'TIMEOUT'
$selfCheck = 'ABSENT'
$proc = $null
$rootPid = -1

function Test-LogContains([string]$path, [string]$needle) {
    if (-not (Test-Path $path)) { return $false }
    try {
        $fs = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $sr = New-Object System.IO.StreamReader($fs)
            try { $text = $sr.ReadToEnd() } finally { $sr.Dispose() }
        } finally { $fs.Dispose() }
        return $text.Contains($needle)
    } catch {
        return $false
    }
}

function Get-TextSafe([string]$path) {
    if (-not (Test-Path $path)) { return '' }
    try {
        $fs = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $sr = New-Object System.IO.StreamReader($fs)
            try { return $sr.ReadToEnd() } finally { $sr.Dispose() }
        } finally { $fs.Dispose() }
    } catch {
        return ''
    }
}

try {
    $inner = "chcp 65001 >nul & set CI=1 & gradlew.bat runClient --no-daemon --console=plain > `"$log`" 2>&1"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList "/c $inner" -PassThru -WindowStyle Hidden
    $rootPid = $proc.Id
    Write-Output "[darkness-visual-probe] launched cmd pid=$rootPid, log=$log, timeout=${timeoutSec}s"

    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (Test-LogContains $log 'WHITEFOG_DARKNESS_VISUAL_SELFTEST') {
            Start-Sleep -Seconds 2
            $text = Get-TextSafe $log
            if ($text -match 'WHITEFOG_DARKNESS_VISUAL_SELFTEST.*status=SUCCESS') {
                $selfCheck = 'SUCCESS'
                $status = 'SUCCESS'
            } else {
                $selfCheck = 'FAIL'
                $status = 'FAILURE'
            }
            break
        }
        $crash = Test-LogContains $log 'A mod crashed on startup'
        $windowErr = Test-LogContains $log 'Failed to create window'
        $accessErr = Test-LogContains $log 'EXCEPTION_ACCESS_VIOLATION'
        $mixinErr = Test-LogContains $log 'MixinApplyError'
        $mixinFail = Test-LogContains $log 'Mixin apply failed'
        if ($crash -or $windowErr -or $accessErr -or $mixinErr -or $mixinFail) {
            $status = 'FAILURE'
            break
        }
        if ($proc.HasExited) {
            if ($status -eq 'TIMEOUT') { $status = 'FAILURE' }
            break
        }
    }

    if ($status -eq 'SUCCESS') {
        $text = Get-TextSafe $log
        if ($text -match 'MixinApplyError') { $status = 'FAILURE'; $selfCheck = 'MIXIN_ERROR' }
    }
} catch {
    $status = 'FAILURE'
    Write-Output "[darkness-visual-probe] exception: $_"
} finally {
    if ($null -ne $proc -and -not $proc.HasExited) {
        try { & taskkill /PID $rootPid /T /F 2>&1 | Out-Null } catch { }
    }
    Start-Sleep -Seconds 4

    "status=$status" | Set-Content -Encoding utf8 $result
    "selfCheck=$selfCheck" | Add-Content -Encoding utf8 $result
    "rootPid=$rootPid" | Add-Content -Encoding utf8 $result
    "log=$log" | Add-Content -Encoding utf8 $result

    Write-Output "[darkness-visual-probe] status=$status selfCheck=$selfCheck"
    if (Test-Path $log) {
        $hit = Select-String -Path $log -Pattern 'WHITEFOG_DARKNESS_VISUAL_SELFTEST|MixinApplyError|Mixin apply failed|A mod crashed' -ErrorAction SilentlyContinue | Select-Object -Last 5
        foreach ($h in $hit) { Write-Output $h.Line.Trim() }
    }
    Write-Output "result written: $result"
}

if ($status -eq 'SUCCESS') { exit 0 } else { exit 1 }
