# White Fog (`white_fog`)

A Minecraft **26.2** **Fabric** mod that builds a survival foundation on a
**server-authoritative player state** and replaces several vanilla interactions
(crafting, block breaking) with explicit, server-verified rules.

## Status / roadmap

- **Upper-level roadmap stages 1.1–1.9 are implemented.** Stages 1.1–1.6 and the post-1.6 bugfix/stabilization work
  were manually verified in-game by the user across the main mechanics.
- All post-1.6 work (fading light sources, the Darkness pulse fix, dynamic light, the source menu and the menu/UI
  passes) shipped as **post-1.6 bugfix/stabilization, not as separate roadmap stages**. The actual roadmap stages
  after that are 1.7 (shelter detector), 1.8 (dark-ambient spawning) and 1.9 (compact light/goal HUD); stages
  1.7–1.9 have **automatic verification only** (sandbox, build, bounded smokes) and their **live acceptance is
  still pending**.
- **Roadmap stage 1.7 «Закрытое укрытие и адаптер света» (shelter detector) is implemented** in
  `darkness/shelter/` and wired into `LightExposureService.isSheltered(...)`. Its **automatic verification**
  (sandbox `tests/eternal_darkness/shelter/`, build and bounded server/client smokes) passes; the **live in-game
  acceptance is still pending** (cave room, built house, stationary player / another player's door change, chunk
  boundary and two players).
- **Roadmap stage 1.8 «Опасность тёмных участков» (dark-ambient hostile spawning) is implemented** in
  `darkness/spawn/` (pure `DarkSpawnPolicy`/`DarkMobState` plus the server `DarkSpawnService`/`DarkSpawnStore`)
  and runs as a second pass inside the single `END_SERVER_TICK`. Its **automatic verification** (sandbox
  `tests/eternal_darkness/spawn/`, build and smokes) passes; **live-world acceptance is pending**.
- **Roadmap stage 1.9 «Компактный HUD света и целей» is implemented** in `darkness/goal/`,
  `darkness/SnapshotRevisionGate`, `darkness/light/LightTieBreak` and the client `client/hud/` widgets behind one
  `root_hud` element. Its **automatic verification** (sandbox `tests/eternal_darkness/hud/`, build and smokes that
  assert `WHITEFOG_HUD_SELFTEST ... status=SUCCESS`) passes; the **live visual GUI acceptance is pending**. The
  current in-game display differs from the original stage-1.9 design — see **HUD display mode**.

## Requirements

| Component  | Version            |
|------------|--------------------|
| Minecraft  | 26.2 (deobfuscated) |
| Fabric Loader | 0.19.5          |
| Fabric API | 0.161.0+26.2       |
| Java       | 25+ (built with JDK 26) |
| Gradle     | 9.7.1 (via wrapper)|

## Build

Build without a background daemon (guarantees the process returns on success and on failure):

```bat
gradlew.bat build --no-daemon --console=plain
```

Convenience wrapper that also writes a UTF-8 log to `logs\`:

```bat
scripts\build.bat
```

Run the client (requires manual stop; do not launch bare):

```bat
gradlew.bat runClient --no-daemon --console=plain
```

## Implemented (roadmap stages 1.1–1.9 + post-1.6 bugfixes)

- **Stage 1.1** — persistent, server-authoritative player survival state
  (Fabric Data Attachment API v1, NBT-backed, `copyOnDeath`) with an S2C
  snapshot sync, a client cache and a HUD stub.
- **Stage 1.2** — vanilla crafting disabled: the 2x2 inventory grid, the
  crafting table (open + use + place-recipe + creative set-slot paths) and the
  recipe book are blocked, and all `CRAFTING` recipes are removed from the
  recipe manager.
- **Stage 1.3** — server-authoritative block breaking. Block categories and
  tool kinds decide `ALLOW`/`DENY_*` and a server-side timer performs the only
  commit; soft blocks by hand are slow, stations and hard blocks follow their
  own rules, filled/hot stations are refused. Includes the client-prediction
  **hotfix**, **regression fix** and **swing fix** (no false local progress,
  local refusal message, vanilla hit animation on allowed blocks).
- **Stage 1.4** — flat-stone station and small stones. Adds the blocks
  `white_fog:flat_stone` (a portable station backed by a `BlockEntity` storing
  a schema version, job owner, escrow, progress, output, mode and revision) and
  `white_fog:small_stone` (a plain stackable block). Both can only be placed on
  the top face of a full solid support (liquids and denied rights are
  rejected). Ordinary right-click opens an empty `FlatStoneMenu` /
  `FlatStoneScreen`; Shift + right-click with an empty main hand removes an
  idle station in one interaction (a busy station is refused with a message
  asking for its materials and result first); a small stone is picked up with
  either hand (main-hand only) and also drops as normal block loot on left
  click; and the backup world interaction "2 cobblestone → 1 flat_stone over
  100 ticks" is available. Includes the GUI icon **hotfixes**: separate item
  models with corrected `display.gui` scale/translation so `small_stone` and
  `flat_stone` render centered in inventory slots.
- **Stage 1.5** — eternal night and darkness exposure. The Overworld is pinned
  to midnight via the 26.2 world-clock system (`GameRules.ADVANCE_TIME=false` +
  the default clock held at 18000), while the new server-authoritative
  `LightExposureService` samples each survival/adventure player once per second
  from the block light in their eye cell: darkness builds exposure, bright light
  both drains it and restores `Condition`, and continuous bright rest (600 ticks)
  resets it. Exposure adds a vanilla Darkness effect, a named movement-speed
  penalty (`white_fog:darkness_slow`, ×0.85) with sprint disabled, and — past
  `exposure>=90` — drains `Condition` to a normal death. The new fields
  (`darknessSchema`, `lightExposure`, `safeLightTicks`, `sampleRemainderTicks`,
  `darknessConditionMilli`, `darknessRevision`) live in the existing attachment
  and migrate from the legacy `Condition`; a `white_fog:darkness_snapshot` S2C
  payload keeps the client cache in sync. Manually verified in-game (see
  Status / roadmap).
- **Stage 1.6** — fading light sources. Vanilla `torch`/`wall_torch`,
  `soul_torch`/`soul_wall_torch`, `lantern`/`soul_lantern` and
  `campfire`/`soul_campfire` keep their IDs but gain a persistent, per-dimension
  fuel store: a single boolean state property `white_fog_lit` is injected into
  the torch/lantern state definitions (the campfire reuses vanilla `LIT`), the
  light emission drops to 0 when unlit, and dark client models/textures are
  shipped for the unlit variants. Fuel is carried by the item component
  `white_fog:light_fuel` (`remainingTicks`; extended with a `lit` flag in
  a post-1.6 pass, with legacy int migration). Generated sources get a
  one-time bonus per loaded chunk, placement reads the component (empty = unlit),
  a server-side 20-tick refuel job atomically consumes one accepted fuel item,
  and a Work Panel (`G`, `InputConstants.KEY_G`) shows the nearest source and
  sends the C2S refuel request (`white_fog:light_refuel`). An unlit source is
  still discovered by the Work Panel (`nearest` deliberately ignores `lit`) so an
  empty, just-placed source can be refuelled or re-lit; being unlit it emits no
  block light and therefore does not affect darkness exposure. Manually
  verified in-game (see Status / roadmap).
- **Stage 1.7** — closed-shelter detector. A read-only server-side detector
  (`darkness/shelter/`: `Voxels`, `CollisionMasks`, `ShelterDetector`,
  `ShelterSnapshot`, `ShelterCache`, `ShelterProvider`) decides whether the
  player stands inside a closed room, using a bounded 6-face FIFO voxel fill
  (fixed order DOWN/UP/NORTH/SOUTH/WEST/EAST), collision-based sealing (full
  collision volumes block; closed `DoorBlock`/`TrapDoorBlock` masks come from the
  actual collision-union boundary coverage, never block names; OPEN is permeable)
  and per-column floor/roof checks. Limits: max 125 interior cells inclusive,
  bbox 9×5×9, min 18 cells plus a clear height-two column, 20-tick runtime cache;
  unloaded/read-error returns `false` fail-closed. It is wired in as
  `LightExposureService.isSheltered(...)` **before** the unchanged
  `LightExposurePolicy`, so:
  - inside a closed shelter, staying in darkness still accumulates exposure
    (**+1/sample** for block light `0..4`),
  - open sky **outside** a shelter adds the extra **+1**,
  - bright light (**`>=9`**) drains exposure by **−2** as before.
  Shelter never creates light, never heals and never blocks a mob path; it only
  removes/reduces the open-sky penalty, so it slows but does not stop dark
  accumulation. Includes a read-only description of a `DDA` station scan (first
  non-air voxel, not a precise vanilla shape hit) for diagnostics. Automatic
  verification only (sandbox `tests/eternal_darkness/shelter/`, build, smokes);
  live in-game acceptance is pending.
- **Post-1.6: source menu, HUD layout fix and portable light (bugfix/stabilization,
  not a roadmap stage).**
  Right-clicking a managed light source (`torch`/`wall_torch`, `soul_torch`/
  `soul_wall_torch`, `lantern`/`soul_lantern`, `campfire`/`soul_campfire`) opens a
  compact server-authoritative `white_fog:light_source` screen (176×150, three
  buttons `Заправить`/`Потушить`/`Зажечь`, vanilla `clickMenuButton`; the state
  arrives via the new S2C `white_fog:source_panel`). Extinguish and re-light are
  immediate and never spend fuel (remaining is preserved, revision bumped); refuel
  reuses the existing 20-tick job (and, after a later post-1.6 pass, never changes `lit`);
  `managedByPost` sources can be inspected but
  refuse every action; all actions re-validate rights/distance/LOS/source UUID/
  revision server-side. The `G` Work Panel was re-laid-out with named constants
  and a bounded, content-sized panel (rows clamped so nothing overflows on narrow
  windows) and shows the nearest source, fuel with a human time-to-empty, the
  offhand portable light and a custom eternal-night clock `Ночь · HH:MM` based on
  the world clock (never the misleading vanilla day label). **Portable light**: a
  charged `torch`/`soul_torch` in the offhand (`white_fog:light_fuel`
  `remainingTicks>0`; missing/0/negative = no light; a charged `count>1` stack is
  rejected without splitting) adds client-side dynamic light through the verified
  `LightCoordsUtil.BrightnessGetter` hook (adapted from LambDynamicLights 26.2,
  local player offhand only, emission 14/10, 1-per-block falloff) and raises the
  server exposure input to `max(vanilla blockLight, emission)` before the
  unchanged `LightExposurePolicy`. No new block/item/entity IDs; only a `MenuType`
  and the S2C payload. Initially the charge was never consumed (a deliberate
  limitation); a later post-1.6 pass adds the server-authoritative inventory fuel tick.
  Manually verified in-game (see Status / roadmap).
- **Post-1.6: refuel/light semantics, `lit` persistence, inventory fuel tick,
  menu/HUD text and constant fog darkness (bugfix/stabilization, not a roadmap
  stage).**
  - Refuel adds **exactly 20% of the capacity** per coal/charcoal (torch 2400,
    soul torch 1600, lantern 4800, soul lantern 3200, campfire 3200; campfire
    stick/log keep 2000/8000), so a source at 20% becomes 40%; overflow is
    refused (`Топливный запас заполнен`) without spending the item.
  - `Заправить` **never changes `lit`**: an unlit source stays unlit after
    refuelling; `Зажечь` is the only `unlit -> lit` action and spends no fuel;
    `Потушить` is `lit -> unlit` preserving fuel. The refuel fallback that used to
    auto-light was removed from both the menu and the `G` path.
  - The `white_fog:light_fuel` component now persists **`remainingTicks` and
    `lit`**; the persistent codec reads the legacy int-only form and migrates
    `remaining>0 -> lit=true`, `0 -> false`, negatives to `0/false`. Placement
    reads both fields, destruction/drop writes both into the single vanilla item,
    and the block/item round-trip preserves both.
  - A new server-authoritative **inventory fuel tick** (inside the existing single
    `END_SERVER_TICK`, no second handler) burns lit source items in the main
    inventory and the offhand exactly once each: `-1` per server tick, `1 -> 0`
    becomes unlit; unlit items pause; item entities/remote inventories are not
    ticked. Offhand portable light/exposure use only the offhand item
    (`lit && remaining>0 && count==1`).
  - The source menu and `G` HUD show fuel as a human duration only
    (`Осталось: X мин Y сек`) and the offhand as `В руке: Факел` / `Факел душ`
    (no raw ticks, no `свет 14`); the menu panel is laid out by the new common
    `LightMenuLayout` (real font measurement, compact/vertical buttons on narrow
    windows, nothing overflows).
  - The client fog darkness factor is now constant for a stable active gate,
    independent of the vanilla effect blend/partial tick (`fogDarknessFactorConstant`),
    which removes the distant Darkness pulse; `voidFactor`, foreign Darkness
  outside the mod gate and `BLINDNESS` are preserved, and the server formula /
  real block light are unchanged.
  Manually verified in-game (see Status / roadmap).
- **Post-1.6: drop/place fuel round-trip, diagnostics and dev command
  (bugfix/stabilization, not a roadmap stage).**
  - `white_fog:light_fuel` `(remaining, lit)` is preserved across the whole
    block↔item round-trip by the shared `LightFuelRoundTrip` used by the
    production placement and drop paths: breaking a source yields exactly one
    item carrying the same `remaining` **and** `lit`, and placing it back
    continues from that state (no reset, no doubling). A charged `count>1` stack
    is refused without splitting.
  - `/whitefog debug [player]` (dev-only, read-only) prints a structured snapshot
    (Russian-friendly): shelter validity/reason, exposure and current/last
    sample, effective light, Darkness state, hands/offhand light, the nearest
    source, and break/recovery/light job and lifecycle state. Event-only
    failures and placement/drop/state transitions are additionally emitted as
    structured `WHITEFOG_LIGHT_*` markers; debug lines also go to INFO as
    `WHITEFOG_DEBUG`. Diagnostics never create exposure sessions or shelter-cache
    entries and do not change gameplay policy.
  - Automatic verification: sandboxes (fuel codec round-trip, light round-trip,
    darkness light fix, shelter), build and bounded server/client smokes pass.
    Live in-game acceptance of the diagnostic command and the whole action chain
    is still pending.
- **Stage 1.8** — dark-ambient hostile spawning (server-authoritative). A second pass inside the
  single `END_SERVER_TICK` (`WhiteFogServer.onEndServerTick` → `DarkSpawnService.tickAll`) runs only
  every 100 loaded server ticks. Per non-Peaceful level it collects loaded **block-ticking** chunks
  (`ServerChunkCache.chunkMap.forEachBlockTickingChunk`) and, for a chunk near a vulnerable player
  (`exposure>=75`, 24..32 blocks from the chunk centre), may add exactly one vanilla
  zombie/skeleton/spider/creeper carrying a persistent `DARK_AMBIENT` marker. The caps (2 per chunk,
  12 per dimension) are checked before the candidate search and reserved again before
  `addFreshEntity`; the seed/RNG, the 8 candidate offsets and the type rotation
  (`successfulSpawns % 4`) are deterministic; a candidate needs a full solid floor, air, no fluid,
  no collision, block light `<=4` (the sky channel is ignored) and
  `!ShelterProvider.isShelteredAt(...)`. Peaceful disables the pass wholesale; vanilla natural
  spawning, loot and combat are untouched. **Automatic verification only** (sandbox
  `tests/eternal_darkness/spawn/`, build, smokes); live-world acceptance is pending.
- **Stage 1.9** — compact light/goal HUD. One root HUD element (`white_fog:root_hud`, `WhiteFogHud`,
  Fabric `HudElementRegistry.addLast`) owns the panels; the old HUD stub and the `G` Work Panel are
  consolidated into it, so there is exactly one registration. The existing
  `white_fog:darkness_snapshot` payload is extended in place (`revision/light/exposure/conditionMilli/
  shelter/source/remaining/goal/post/final/wave`, one hand-written `StreamCodec.of`) and is sent when
  changed no more often than every 5 ticks, plus a 100-tick heartbeat and immediately on
  join/respawn/dimension; the client accepts `revision >= last` (equal = heartbeat) and clears on
  disconnect. Five exact `white_fog:*` goals live in the pure `DarkGoalService` with monotonic
  completed flags persisted in `PlayerSurvivalState`. The `Текущее задание` goal panel is localized
  (`goal.white_fog.*`, never raw IDs), wraps to at most two ellipsized lines and is hidden below
  320×180. **Automatic verification only**; live visual GUI acceptance (pixels, real Cyrillic widths,
  overlap, action-bar text) is pending. See **HUD display mode** for the current visibility rules.

### HUD display mode

The current in-game display (2026-10-09 user request) replaces the original stage-1.9 compact
rendering while keeping its production classes and server data:

- The **`G` Work Panel is permanently visible** in normal gameplay, bottom-left. The `G` key **no
  longer toggles or gates the panel**; it stays a separate quick action that sends the C2S refuel
  request for a placed source (`WhiteFogClient.consumeClick()` → `white_fog:light_refuel`). Visibility
  is decided by the pure `DarkHudLayout.workPanelVisible(...)` and hidden only when the whole gameplay
  HUD is hidden (no player, death, spectator, F1, any open `Screen`).
- The Work Panel is content-sized in the shared style and shows seven rows: title, the new thin
  `Тьма: E%` darkness/exposure bar (value = server `DarknessSnapshotPayload.exposure`, normalized and
  eased by the pure `DarkHudLayout.exposureNormalized`/`DarknessBar`), the nearest placed source, the
  fuel/time-to-empty, the offhand light (`В руке: Факел` / `Факел душ`), the eternal-night clock
  `Ночь · HH:MM`, and a hint/result line for the last operation.
- The compact `Текущее задание` panel is **back in the top-right** (`GoalWidget`), gated by the pure
  `DarkHudLayout.stageGoalVisible(...)`: localized goals, at most two ellipsized lines, a 120
  real-pixel cap, a 4 px right margin, hidden below 320×180.
- Darkness warnings are **no longer a HUD window**: the root never updates or renders `WarningWidget`
  and draws no warning rectangle. The pure `DarkWarningPolicy` (thresholds 75/90, a 40-tick visual
  cooldown on reason change, exactly one emission per reason entry) drives a normal action-bar message
  via `LocalPlayer.sendOverlayMessage`, localized as `Найди свет` (exposure ≥ 75) and
  `Тьма истощает тебя` (exposure ≥ 90).
- The earlier compact status rows `Свет: L/15` / `Тьма: E%` / `Укрытие: да|нет` / source
  (`LightWidget` + `ExposureWidget`) are **not rendered** in this mode: the widgets stay constructed
  and their classes/data are kept for future use, but the pure
  `DarkHudLayout.shouldShowCompactStageHud()`/`compactStageHudVisible(...)` always return `false` and
  the root does not draw them.

## Not yet implemented

- The mod pickaxes `white_fog:bronze_pickaxe` / `white_fog:iron_pickaxe` /
  `white_fog:steel_pickaxe` are **not registered yet** (planned for stage 6.4).
  Until then mining hard blocks and hard stations with a mod pickaxe is
  unavailable; vanilla tools are refused for those categories.
- **Natural world generation (stage 2.1)** is not implemented yet, so
  `flat_stone` and `small_stone` do not appear in the world naturally.
- **Station job recipes (stage 3.5)** are not implemented yet: the station menu
  is empty apart from a recipe-tab placeholder, and no tool recipes exist.

## Tests

- `tests\break_timer\run_break_timer_selftest.bat` — logic-only sandbox for the
  break-timer lifecycle (not a runtime proof).
- `tests\station\run_station_selftest.bat` — logic-only sandbox for the station
  and small-stone interactions (not a runtime proof).
- `tests\item_gui_center\run_gui_icon_center_selftest.bat` — matrix-proof of the
  item GUI icon centering using the real 26.2 `ItemTransform`;
  `tests\item_gui_center\run_client_itemmodel_probe.bat` — bounded `runClient`
  probe that the item models bake.
- `tests\eternal_darkness\exposure\run_eternal_darkness_selftest.bat` —
  logic-only sandbox for the light-exposure policy, sampling cadence, migration,
  serialization and snapshot sync (not a runtime proof).
- `tests\eternal_darkness\light\run_light_selftest.bat` — logic-only sandbox for
  the fading light sources: capacities, component normalization, overflow, unlit
  pause, source replacement, split/merge, `managedByPost`, generated bonuses,
  nearest/unlit discoverability (an unlit/empty source is still found so it can be
  refuelled or re-lit, while it emits no light) and the oracle cases (lantern
  countdown, unload pause, ItemEntity, 1→0 extinguish) (not a runtime proof).
- `tests\eternal_darkness\portable_light\run_portable_light_selftest.bat` —
  logic-only sandbox for the portable offhand light, the source-panel action state
  (extinguish/relight preserve fuel, refuel accepted/full/no-fuel, `managedByPost`
  refuses, status codes), the **post-1.6** semantics (coal = 20% of capacity,
  refuel never mutates `lit`, `Зажечь` only `unlit -> lit`, `(remaining, lit)`
  persistence with place/drop round-trip, inventory countdown, offhand emission
  limited to lit+count==1, HUD strings without raw ticks/light level and constant
  no-pulse fog outputs), the `Ночь · HH:MM` clock formatting and the bounded
  panel layout from 60 px to 320 px (not a runtime proof; 459 checks).
- `tests\eternal_darkness\shelter\run_shelter_selftest.bat` — logic-only sandbox
  for the stage 1.7 closed-shelter detector (production pure logic): closed/intact
  rooms, closed vs open door, roof hole, water in the start cell, floor hole, slab
  gap, 125/126-cell and 9×10 bbox limits, unloaded face and an empty cache after a
  dimension change; plus seven production cache regressions (OPEN_VOLUME/read
  errors/UNLOADED, unchanged cache). `run_api_evidence.bat` records the `javap`
  signatures and local jar hashes. Not a runtime proof; live acceptance pending.
- `tests\eternal_darkness\spawn\run_dark_spawn_selftest.bat` — logic-only sandbox for the stage 1.8
  dark-ambient spawn policy/state (production pure classes): roll/permits, exposure 74/75, distance
  23.99/24/32/32.01, block light 4/5, per-chunk/per-dimension caps, type rotation, target selection,
  duplicate-UUID idempotency, unload/load cleanup, deterministic seed and persistent attempt
  round-trip. Not a runtime/live-spawn proof; live acceptance pending.
- `tests\eternal_darkness\hud\run_hud_selftest.bat` — logic-only sandbox for the stage 1.9 compact
  HUD (production pure `DarkGoalService`, `SnapshotRevisionGate`, `LightTieBreak`,
  `PortableLightPolicy`, `LightConfig`, `DarkHudLayout`, `HudSourceDisplay`, `DarkWarningPolicy`,
  `DarknessBar`) plus source-scan regressions for the single root, the `G` key separation, the
  retained widgets and the action-bar warning path. Not a pixel/runtime proof; live GUI acceptance
  pending.
- `tests\darkness_visual\run_darkness_visual_selftest.bat` and
  `tests\portable_light_dynamic\run_portable_light_dynamic_selftest.bat` —
  logic-only sandboxes for the client visual adapter (no-pulse envelope) and the
  dynamic-light root causes (`RenderSectionRegion` gate, falloff, entity light,
  section radius). Not a runtime proof.
- `scripts\server_smoke.bat` / `scripts\client_smoke.bat` — bounded smoke runs
  (dedicated server / client) that terminate only their own process tree.

## License

**All Rights Reserved** — Copyright © 2026 belzoralolbtw.

The project is proprietary. Copying, modification, forks, redistribution,
publication, sublicensing, sale, commercial use, and inclusion of its code or
assets in any other project or mod are not permitted without prior written
permission from the copyright holder. Only personal, non-commercial use of an
unmodified distributed version is allowed. See [LICENSE](LICENSE) for the full
terms. Minecraft, Fabric and third-party dependencies remain the property of
their respective owners; this license covers only materials owned by the author.
