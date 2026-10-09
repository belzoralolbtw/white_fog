param(
    [ValidateSet('api', 'selftest')][string]$Mode = 'selftest',
    [ValidateSet('normal', 'failure', 'timeout')][string]$Probe = 'normal'
)
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$logDir = Join-Path $root 'logs'
[IO.Directory]::CreateDirectory($logDir) | Out-Null
$stamp = (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$prefix = if ($Mode -eq 'api') { 'shelter_api' } else { 'shelter_selftest' }
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
            # This Process instance was started by this runner. Check exact ownership before tree kill.
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
    $writer.WriteLine("SHELTER mode=$Mode probe=$Probe started=$(Get-Date -Format o) budget_ms=$budgetMs")
    $writer.WriteLine('note=logic-only sandbox, NOT runtime/collision geometry proof')
    $jdk = Join-Path $env:USERPROFILE 'jdk-26.0.2.1\bin'
    if ($Mode -eq 'api') {
        $common = Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common-deobf\26.2\minecraft-common-deobf-26.2.jar'
        $modules = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api'
        $lifecycle = Join-Path $modules 'fabric-lifecycle-events-v1\4.1.4+29b6eb019e\54a508470dcea81513e39001b5c0bf9fae0f1d5\fabric-lifecycle-events-v1-4.1.4+29b6eb019e.jar'
        $interaction = Join-Path $modules 'fabric-events-interaction-v0\5.2.8+515ac5339e\98650380bb4e65a3deb6254e7c10a171eb907477\fabric-events-interaction-v0-5.2.8+515ac5339e.jar'
        $network = Join-Path $modules 'fabric-networking-api-v1\6.3.4+2989c6a09e\608860809ec66fd65ca764c4157e96030f330537\fabric-networking-api-v1-6.3.4+2989c6a09e.jar'
        foreach ($jar in @($common, $lifecycle, $interaction, $network)) {
            $writer.WriteLine("JAR $jar SHA256=$((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash)")
        }
        $classpath = "$common;$lifecycle;$interaction;$network"
        $classes = @(
            'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase',
            'net.minecraft.world.phys.shapes.VoxelShape', 'net.minecraft.world.phys.shapes.Shapes',
            'net.minecraft.world.level.block.Block', 'net.minecraft.world.phys.shapes.BooleanOp',
            'net.minecraft.world.phys.shapes.CollisionContext',
            'net.minecraft.world.level.block.DoorBlock', 'net.minecraft.world.level.block.TrapDoorBlock',
            'net.minecraft.world.level.Level', 'net.minecraft.world.level.chunk.LevelChunk',
            'net.minecraft.world.level.chunk.ChunkAccess', 'net.minecraft.world.level.chunk.LevelChunk$EntityCreationType',
            'net.minecraft.world.level.block.HorizontalDirectionalBlock',
            'net.minecraft.server.level.ServerChunkCache', 'net.minecraft.server.level.ServerLevel',
            'net.minecraft.world.level.BlockGetter', 'net.minecraft.world.level.LevelHeightAccessor',
            'net.minecraft.world.level.ChunkPos', 'net.minecraft.world.phys.AABB',
            'net.minecraft.world.level.block.state.StateHolder', 'net.minecraft.world.level.block.Blocks',
            'net.minecraft.world.level.block.state.properties.DoubleBlockHalf',
            'net.minecraft.world.level.block.state.properties.Half',
            'net.minecraft.world.level.block.state.properties.SlabType',
            'net.minecraft.world.level.block.SlabBlock', 'net.minecraft.world.level.material.FluidState',
            'net.minecraft.core.Direction', 'net.minecraft.core.BlockPos',
            'net.minecraft.world.entity.Entity', 'net.minecraft.resources.ResourceKey',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents$Load',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopped',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents$Unload',
            'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents$Unload',
            'net.fabricmc.fabric.api.event.player.BlockEvents',
            'net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents$After',
            'net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Disconnect'
        )
        foreach ($class in $classes) {
            Invoke-OwnedProcess (Join-Path $jdk 'javap.exe') "-p -c -classpath `"$classpath`" $class"
        }
        foreach ($jar in @($lifecycle, $interaction)) {
            Invoke-OwnedProcess (Join-Path $jdk 'jar.exe') "tf `"$jar`""
        }
    } else {
        if ($Probe -eq 'failure') { throw 'Intentional runner failure-path probe' }
        if ($Probe -eq 'timeout') {
            Invoke-OwnedProcess (Join-Path $PSHOME 'powershell.exe') '-NoProfile -NonInteractive -Command "Start-Sleep -Seconds 10"'
        } else {
            $src = Join-Path $PSScriptRoot 'src'
            $out = Join-Path $PSScriptRoot ('build\' + $stamp)
            [IO.Directory]::CreateDirectory($out) | Out-Null
            $sources = @(Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
            # Compile the existing pure exposure policy itself, read-only, not a duplicated formula.
            $sources += '"' + (Join-Path $root 'src\main\java\com\whitefog\darkness\LightExposurePolicy.java').Replace('\', '/') + '"'
            $sources += '"' + (Join-Path $root 'src\main\java\com\whitefog\darkness\DarknessConfig.java').Replace('\', '/') + '"'
            foreach ($name in @('Voxels', 'CollisionMasks', 'ShelterDetector', 'ShelterSnapshot', 'ShelterCache')) {
                $sources += '"' + (Join-Path $root "src\main\java\com\whitefog\darkness\shelter\$name.java").Replace('\', '/') + '"'
            }
            $writer.WriteLine('implementation=production pure shelter classes compiled read-only; no duplicated algorithm')
            $argfile = Join-Path $out 'sources.txt'
            [IO.File]::WriteAllLines($argfile, $sources, $utf8)
            Invoke-OwnedProcess (Join-Path $jdk 'javac.exe') "-J-Xmx128m --release 25 -encoding UTF-8 -Xlint:all -Werror -d `"$out`" @`"$argfile`""
            Invoke-OwnedProcess (Join-Path $jdk 'java.exe') "-Xmx128m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp `"$out`" com.whitefog.tests.shelter.SelfTest"
        }
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
