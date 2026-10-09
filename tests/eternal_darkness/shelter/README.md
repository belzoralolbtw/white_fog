# Shelter sandbox

This is a pure Java voxel-contract sandbox for roadmap stage 1.7. It does not import Minecraft classes and does not prove runtime collision geometry.

## Contract

- FIFO six-face order is `DOWN, UP, NORTH, SOUTH, WEST, EAST`.
- A loaded-only provider supplies immutable cell DTOs. A missing loaded read fails closed.
- Interior count is inclusive at 125; dimensions are inclusive at 9 x 5 x 9; minimum is 18 cells plus one clear two-cell column.
- Full collision blocks all six face transitions. Partial ordinary shapes, including fences and slabs, leak. Closed door/trapdoor masks contain only their actual collision boundary faces. A transition is blocked by `from.blocks(direction) || to.blocks(direction.opposite())`, so the paired transition is symmetric without artificially adding the opposite face to the cell. Open states contribute no wall mask or floor/roof support.
- Floor and roof are checked only at the bottom and top of each occupied XZ column. Door voxels can enter the interior from a tangential/inner side and then count toward volume, bbox and column support checks; no doorway exemption is applied. A detached door column without roof/floor therefore fails even with a closed panel. The coarse one-node-per-cell model cannot represent two air regions inside one door voxel; real collision geometry still requires architect/manual acceptance.
- Reason precedence: unavailable read → `UNLOADED`; invalid feet → `INVALID_START`; known build-height/world boundary → `OPEN_VOLUME`; attempted 126th cell or excessive bbox → `TOO_LARGE`; after completed BFS, endpoints → `NO_FLOOR`/`NO_ROOF`, then minimum volume/clear height → `TOO_SMALL`. Ordinary roof/floor holes usually expand into outside air and reach `TOO_LARGE` before endpoint checks; they are not automatically labelled `NO_ROOF`/`NO_FLOOR`. Provider/runtime read exceptions map to `OPEN_VOLUME` (ticket has no ERROR reason) and emit dimension/coordinates/error diagnostics. Synthetic sealed-but-unsupported DTO fixtures exercise endpoint reasons explicitly.
- `ShelterCache` is keyed by player UUID, dimension, feet position and tick. TTL is `<20` ticks, block changes use bbox+1 invalidation, unload/dimension/reconnect remove entries, and a cached true is never reused after a failed loaded check.

## Reference and API evidence

The flood-fill shape was independently adapted from Ad Astra's open-source `FloodFill3D`:

`https://github.com/terrarium-earth/Ad-Astra/blob/a9ae70c7b62af9389d52211bd6b6b69b510135f2/common/src/main/java/earth/terrarium/adastra/common/utils/floodfill/FloodFill3D.java`

The reference uses a bounded FIFO flood fill and collision-side coverage. This sandbox changes the contract for inclusive limits, fixed direction order, paired masks, loaded-only reads, column endpoint checks and fail-closed diagnostics; no code was copied.

Run `run_api_evidence.bat` to create a unique UTF-8 `logs/shelter_api_*.txt`. The log is produced by `javap` against the local MC 26.2 deobfuscated jar and Fabric API 0.161.0+26.2 modules. It records `DoorBlock`, `TrapDoorBlock`, `VoxelShape`/`BlockState` collision-face methods, `ServerChunkCache.getChunkNow`, `ServerChunkEvents.CHUNK_UNLOAD`, interaction block events and `ServerPlayConnectionEvents.Disconnect`. Fabric API exposes chunk unload and player interaction callbacks; there is no generic all-block-state-change callback in the inspected API modules, so production invalidation must also use the existing server block-change path or a narrow mixin.

Run `run_shelter_selftest.bat` for the bounded UTF-8 acceptance test. The latest normal run is `logs/shelter_selftest_20261009_150610_112_10f5c72d.txt`: 228 assertions, `status=SUCCESS`, 1228 ms total. Earlier API evidence: `logs/shelter_api_20261009_101021_460_ba539dea.txt` (SUCCESS, 5819 ms). The timeout probe `logs/shelter_selftest_20261009_104647_477_86fcfdd6.txt` returned `TIMEOUT/124` in 1857 ms after terminating only its owned tree. The intentional failure probe `logs/shelter_selftest_20261009_111821_898_56f1b4df.txt` returned `FAILURE/1` in 12 ms. All have matching `.result` files.

## Runner and applied runtime wiring

`runner.ps1` bounds compilation and execution together to 55 seconds (use an external timeout greater than 60 seconds), writes unique UTF-8 `.txt`/`.result` files, and records each owned PID. `-Probe failure` and `-Probe timeout` test failure and watchdog paths. No Python is added. The self-test compiles the existing pure `LightExposurePolicy.java` and `DarknessConfig.java` read-only alongside sandbox sources; it verifies the actual unchanged exposure policy, not a duplicated formula.

Main integration is **implemented** (stage 1.7, committed locally; live-room acceptance still pending). The applied change list is:

- New `src/main/java/com/whitefog/darkness/shelter/{Voxels,CollisionMasks,ShelterDetector,ShelterSnapshot,ShelterCache,ShelterProvider}.java` plus dev-only `ShelterRuntimeSelfTest.java`.
- `src/main/java/com/whitefog/darkness/LightExposureService.java`: `isSheltered(...)` now delegates to the loaded-only `ShelterProvider` before the existing sample.
- `src/main/java/com/whitefog/server/WhiteFogServer.java`: lifecycle clearing (join/disconnect/respawn/dimension/chunk-unload/level-unload/server-stop) and the dev startup fixture self-test.
- New `src/main/java/com/whitefog/mixin/shelter/LevelChunkShelterMixin.java` plus `src/main/resources/white_fog.mixins.json`: successful `LevelChunk.setBlockState` change invalidation.
- `AGENTS.md`: runtime status and evidence update.

No new payloads, persisted shelter truth, block/item registrations or independent server tick are needed. Live shelter behavior (cave/house/chunk boundary/two players) still requires architect/manual acceptance.
