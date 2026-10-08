# AGENTS.md — White Fog (`white_fog`)

> Project memory and hand-off. Global rules live in `~/.config/opencode/AGENTS.md`; this file holds only project
> state and rules. Kept short on purpose: current state only — no build logs, no superseded hotfix history.

## Status

- **Target:** Minecraft **26.2** (deobfuscated), Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**,
  Java **25+** (built with JDK 26.0.2.1), Gradle **9.7.1**, Loom **1.18.3** (resolves 1.18.2).
  No Stonecutter / Forge / NeoForge. **No `mappings` line** (26.2 is deobfuscated).
- **Mod:** id `white_fog`, group `com.whitefog`, version `0.1.0`, environment `*` (dedicated server + client).
- **Implemented (feature — README/code stage label):**
  - **1.1** persistent server-authoritative player state (Fabric attachment) + S2C snapshot + client cache + HUD stub.
  - **1.2** vanilla crafting disabled: 2x2 grid, crafting table use, recipe book / place-recipe / creative set-slot,
    and all `CRAFTING` recipes removed from the recipe manager.
  - **1.3** server-authoritative block breaking (own timer, one commit) + client-prediction suppression, local denial
    message, vanilla swing on allowed blocks.
  - **1.4** `white_fog:flat_stone` station + `white_fog:small_stone` (top-face placement, one-interaction removal,
    pickup, recovery "2 cobblestone → 1 flat_stone over 100 ticks"; centered GUI item icons).
  - **1.5** eternal night (Overworld) + darkness exposure / Condition / speed penalty; **client-only visual
    adapter** removing the Darkness pulse/near-black (constant moderate darkening).
  - **1.6** fading vanilla light sources, per-dimension fuel store, refuel job, `G` Work Panel.
  - **1.7** right-click source menu (`Заправить`/`Потушить`/`Зажечь`), bounded HUD layout, offhand portable light.
  - **1.8 (local ticket on top of 1.7)** refuel = 20% of capacity, `Заправить` never changes `lit`, `(remaining, lit)`
    component with legacy migration, server-side inventory fuel tick, human menu/HUD text, constant fog factor.
  - **1.9 (bugfix ticket on top of 1.8)** reported-bug pass: (a) Darkness pulse removed by keeping the vanilla
    effect's blend factor stable (duration 60 / refresh-at-40 &gt; Darkness blend-advance 22) + client suppression;
    (b) light-source placement record now keyed by the ACTUAL placed block/position (fixes `wall_torch` id mismatch
    and any support-position quirk) so `nearest`/HUD find it; (c) menu buttons get a named horizontal text padding
    (`BUTTON_H_PAD`) in wide+compact layouts; (d) `PLAYER_GUIDE.md` fuel table.
  - **1.10 (deeper bugfix ticket on top of 1.9)** portable offhand light was inert — two real root causes found
    with `javap` and fixed: (a) `PortableLightClient` required `level instanceof ClientLevel`, but during mesh
    baking the brightness getter receives `RenderSectionRegion` (a `BlockAndLightGetter`, not a `ClientLevel`), so
    the block contribution was always 0 → now any `BlockGetter` is accepted and the world is resolved from
    `Minecraft.level`/`player`; (b) `EntityRenderer.getPackedLightCoords` in 26.2 reads
    `getBlockLightLevel(entity,pos)` directly (NOT `LightCoordsUtil`), so the hand/player had no dynamic light and
    the held torch looked black/invisible → new client mixin `PortableLightEntityRendererMixin` raises it. Also
    added a minimal section-rebuild tracker (no spatial engine) and a `STREAM_CODEC` round-trip self-test proving
    the `(remaining,lit)` component reaches the client intact.
  - **1.11 (bugfix ticket on top of 1.10)** reported-bug pass: (a) visual darkening ramped almost abruptly —
    slowed ONLY the visual fade-in speed `DarknessVisualConfig.FADE_IN_PER_SECOND` 2.0 → **0.4/s** (≈2.5 s ramp);
    exposure threshold, sample interval, vanilla Darkness duration/refresh and `FADE_OUT_PER_SECOND` (1.0/s)
    untouched, no pulse reintroduced; (b) HUD source line was misleading — with a valid burning offhand torch and
    no placed source it said «Источник: нет рядом» while also showing «В руке: Факел»; new pure
    `LightPanelFormat.hudSourceLine/hudFuelLine` now show «Источник: в руке — Факел» and the offhand `(remaining)`
    in «Осталось:», while a placed block keeps priority, empty/unlit/corrupt offhand keeps «нет рядом», and the
    `G` C2S refuel action still targets only a placed block (display split from action); (c) first-person torch
    light path hardened — new client `ItemInHandRendererMixin` raises the single packed hand light passed to
    `ItemInHandRenderer.submitHandsWithItems` to `max(vanilla, portable)`, so the item in hand is lit by its own
    offhand source first-person too (unlit/empty/corrupt unchanged, no fullbright, stack/model not swapped).
- **Verification state:** `gradlew build` + bounded server/client smokes pass; every sandbox passes. The current
  version was **personally verified in-game by the user across the main mechanics**, and that manual pass is what
  surfaced the fixed bugs (Darkness pulse, abrupt fade-in, misleading HUD source line, menu button sizing,
  first-person torch). Verified in-game: eternal night/exposure, darkness behavior/no pulse, fading light sources /
  fuel / menu / actions, `G` panel, offhand portable light, placement/drop/persistence, adaptive menus. Sandboxes and
  smokes remain **supplementary** automatic logic checks, **not** a substitute for the manual acceptance. Known
  accepted limitation: the first-person offhand torch may look absent/black while its dynamic light still works
  (confirmed by the user, treated as non-critical). `PLAYER_GUIDE.md` is a compact player description
  (status / features / interactions / fuel / limits / checks), not a manual checklist.
- **Roadmap note (important):** the current `ROADMAP_STEPS.md` contains a single ticket
  «Этап 1.7: Закрытое укрытие и адаптер света» (shelter detector). It is **NOT started / not implemented**
  (no `darkness/shelter/` package; `LightExposureService.isSheltered(...)` returns `false`). That ROADMAP ticket
  reuses the label "1.7" but is a different, newer ticket than the already-shipped README/AGENTS "Stage 1.7"
  (source menu + portable light). Do not confuse the two.
- **Git:** repo `https://github.com/belzoralolbtw/white_fog` (PUBLIC), `origin/master`. The current working version is
  fixed by commit **`439f5a5` — `docs: update project memory after release`** (project memory docs), on top of
  **`12db179` — `docs: update project memory after release`**, on top of
  **`ff5c05a` — `feat: add fading light sources and portable lighting`** (stages 1.6–1.11 plus the doc edits);
  all are already on `origin/master`. Never push future work without explicit user approval.

## Structure

```
src/main/java/com/whitefog/            # COMMON — must NOT import net.minecraft.client.*
  WhiteFog.java                        # common initializer: payload types -> attachment -> server services -> crafting lock
  WhiteFogConfig.java                  # project tunables/defaults (Russian comments)
  WhiteFogAttachments.java             # AttachmentType<PlayerSurvivalState> (persistent + copyOnDeath)
  state/PlayerSurvivalState.java       # player state: survival + darkness fields; NBT Codec; revision
  server/WhiteFogServer.java           # the ONE END_SERVER_TICK; join/respawn/dimension; disconnect; dev command
  server/SurvivalTicker.java / PlayerStateSyncService.java # interval logic + base state snapshot send
  crafting/CraftingLock.java           # 1.2: slot predicates, recipe filter, crafting-table use block, self-test
  breaking/BlockBreakRules.java / BlockBreakPolicy.java # 1.3: Category/ToolKind classification, ALLOW/DENY_* + durations
  breaking/BreakTimerService.java / BreakSession.java / StationRemoval.java # 1.3: timer, session, safe station removal
  content/WhiteFogContent.java         # 1.4/1.7: block/BlockItem/BlockEntityType/MenuType registration (setId required)
  content/block/FlatStoneBlock.java / SmallStoneBlock.java # 1.4: station + small stone (SHAPE, instabreak, VARIANT)
  content/block/entity/FlatStoneBlockEntity.java # 1.4: schemaVersion/owner/escrow/output/progress/mode/revision
  content/item/GroundPlacedBlockItem.java / content/menu/FlatStoneMenu.java # 1.4: top-face placement + empty menu
  content/menu/LightSourceMenu.java    # 1.7: empty source menu; clickMenuButton -> server; broadcastChanges -> refresh
  station/FlatStoneInteractions.java / FlatStoneRemoval / SmallStonePickup / StationDropHelper / RecoveryService # 1.4
  darkness/DarknessConfig.java / LightExposurePolicy.java / LightExposureService.java # 1.5: exposure/Condition/speed
  darkness/EternalNightWorld.java      # 1.5: Overworld clock=18000 + GameRules.ADVANCE_TIME=false
  darkness/light/LightConfig.java / LightFuelPolicy.java / LightSourceBlocks.java # 1.6: fuel/capacity + WHITE_FOG_LIT
  darkness/light/LightFuelComponent.java / LightSourceStore.java # 1.6/1.8: item component + per-dimension SavedData
  darkness/light/LightSourceService.java / LightSourceInteractions.java # 1.6/1.8: scan/tick/refuel/nearest/drop/receiver
  darkness/light/PortableLightPolicy.java / PortableLightService.java # 1.7/1.8: offhand light + inventory fuel tick
  darkness/light/LightPanelFormat.java / SourceActionPolicy.java / LightMenuLayout.java # 1.7/1.8 pure UI/action rules
  network/*.java                       # payload records (see Networking below) — registered once in WhiteFogPayloads
  mixin/AbstractContainerMenuMixin.java / RecipeManagerMixin.java / ServerGamePacketListenerImplMixin.java # 1.2
  mixin/ServerPlayerGameModeMixin.java # 1.3: handleBlockBreakAction HEAD -> BreakTimerService
  mixin/light/*.java                   # 1.6: lit property, emission, particles, placement, drop, piston, ignite
src/client/java/com/whitefog/client/   # CLIENT — client API only
  WhiteFogClient.java                  # ClientModInitializer: receivers, HUD, keybinds, disconnect clear, dev self-checks
  ClientPlayerState.java / ClientDarknessState.java / ClientLightState.java # client caches (never source of truth)
  network/WhiteFogClientNetworking.java / DarknessClientNetworking.java / LightClientNetworking.java # receivers
  hud/LightWorkPanelHud.java / hud/WhiteFogHud.java # 1.6/1.7 G panel + stub
  screen/FlatStoneScreen.java / LightSourceScreen.java # 1.4 / 1.7-1.8 adaptive screens
  portable/PortableLightClient.java    # 1.7/1.10 offhand dynamic light (local player only) + section-rebuild tracker
  darkness/DarknessVisualConfig.java / DarknessVisualGate.java # 1.5 visual adapter (frame-delta envelope)
  mixin/MultiPlayerGameModeMixin.java  # 1.3 client prediction: cancel vanilla, manual START/ABORT, swing on ALLOW
  mixin/PortableLightBrightnessGetterMixin.java # 1.7 LightCoordsUtil.BrightnessGetter hook (block mesh light)
  mixin/PortableLightEntityRendererMixin.java # 1.10 EntityRenderer.getBlockLightLevel hook (hand/player light)
  mixin/ItemInHandRendererMixin.java   # 1.11 first-person hand light arg raised to max(vanilla, portable)
  mixin/LightmapRenderStateExtractorMixin.java / DarknessFogEnvironmentMixin.java # 1.5 visual (no pulse / milder fog)
  dev/ItemModelSelfCheck.java          # 1.4 dev-only item-model bake check (WHITEFOG_ITEM_MODEL_SELFTEST)
src/main/resources/                    # fabric.mod.json, white_fog.mixins.json, client mixin config in src/client/resources
  assets/white_fog/...                 # flat_stone + small_stone + unlit source models/textures/lang
  assets/minecraft/blockstates/*.json  # 1.6: overrides vanilla torch/wall_torch/soul_*/lantern to add white_fog_lit
  data/white_fog/loot_table/blocks/    # 1.4: flat_stone.json, small_stone.json (no survives_explosion)
tests/                                 # independent sandboxes (NOT part of build, never touch src/): break_timer/ station/
                                       #   item_gui_center/ eternal_darkness/{exposure,light,portable_light}/ darkness_visual/
                                       #   darkness_light_fix/ portable_light_dynamic/
scripts/build.bat|server_smoke.bat|client_smoke.bat
README.md  ROADMAP_STEPS.md  PLAYER_GUIDE.md   # compact player guide (not a checklist)
```

`common` (`src/main`) must stay free of `net.minecraft.client.*`; verify after a build by scanning
`build/classes/java/main` (command in "Build & run").

## Done / Current behavior

- **Player state (1.1).** One attachment per player (`persistent(...).copyOnDeath()`), stored in playerdata
  (survives save/load, chunk unload, disconnect/reconnect). Fields: `weightKg`, `fatigue`, `body`,
  `perceivedTemperatureC`, `calories`, `water`, `condition`, `clothingWetness`, `dysentery`, `sleep`, `work`,
  `activeGoal`; darkness section: `darknessSchema`, `lightExposure`, `safeLightTicks`, `sampleRemainderTicks`,
  `darknessConditionMilli`, `darknessRevision` (migrates from `condition`). Serialized via `CompoundTag`
  (`toNbt`/`fromNbt`, `CompoundTag.CODEC.xmap`). Dev-only read-only command `/whitefog debug [player]`.
- **Networking.** Custom payloads, all registered exactly once in `WhiteFogPayloads` (common, before any receiver):
  S2C `white_fog:player_state_sync`, `white_fog:darkness_snapshot`, `white_fog:light_source_snapshot`,
  `white_fog:light_refuel_result`, `white_fog:source_panel`; C2S `white_fog:light_refuel`.
  Registering a receiver before its payload type is a crash.
- **Crafting lock (1.2).** `AbstractContainerMenuMixin` cancels `clicked` for the 2x2 grid/result of `InventoryMenu`
  and any `CraftingMenu` (all click types, incl. client prediction); `RecipeManagerMixin` strips every
  `RecipeType.CRAFTING` recipe (start + `/reload`); `ServerGamePacketListenerImplMixin` blocks place-recipe /
  creative set-slot into crafting slots; `BlockEvents.USE_WITHOUT_ITEM` fails crafting-table use. Message
  «Крафтить на бегу нельзя».
- **Block breaking (1.3).** `ServerPlayerGameModeMixin` injects `handleBlockBreakAction` at HEAD and delegates to
  `BreakTimerService`; the server timer is the only commit point. Session stores UUID/dimension/pos/item copy/
  startState/startTick/requiredTicks; validated every tick (alive, loaded, in-world height, same dimension/item
  with components, same state, not hot, not filled, within range, permissions/spawn-protection/adventure).
  Categories: `SOFT_PLANT/LEAF/SOIL/ICE/SNOW`, `LANTERN`, `STATION_WOOD/HARD/HOT/FILLED`, `HARD`, `UNCLASSIFIED`
  (vanilla). Hard = `requiresCorrectToolForDrops()` / `#logs` / `#planks` / glass. Durations (ticks): soft by hand
  40; soil hand 60, shovel 10; ice 80; snow 40; lantern 40; station 40; hard with a mod pickaxe 120/80/40
  (bronze/iron/steel — **not registered yet**, stage 6.4). Plants/leaves drop nothing (even shears/axe). Vanilla
  pickaxe/axe do not break hard blocks. Refusal message «Слишком крепко — нужен инструмент» (action bar,
  per-reason cooldown). Client-only `MultiPlayerGameModeMixin` suppresses vanilla prediction for all managed
  categories, sends exactly one manual START for `ALLOW` (via shadowed `startPrediction`) and ABORT on release/
  target change, shows the refusal locally, and returns `true` on `ALLOW` so vanilla swing/particles play.
- **Flat stone (1.4).** `white_fog:flat_stone` (block + BlockItem, stack 16, BlockEntity, empty `FlatStoneMenu`/
  `FlatStoneScreen`) and `white_fog:small_stone` (block + BlockItem, stack 64, no BlockEntity). Placement only on
  the top face of a full solid support (liquids/denied rights rejected without spending the item). Right-click opens
  the (empty) station menu; Shift+right-click with an empty main hand removes an idle station in one interaction
  (busy → «Сначала забери материалы и результат»); a small stone is picked up with the main hand and also drops as
  normal block loot on left click; recovery "2 cobblestone → 1 flat_stone over 100 ticks" runs as a world
  interaction (movement/damage cancel without loss). `PushReaction.BLOCK` for the station, `DESTROY` for the stone.
  Item models use a separate `models/item/*` with corrected `display.gui` so both icons render centered.
- **Eternal night + exposure (1.5).** Overworld clock pinned to 18000 with `GameRules.ADVANCE_TIME=false`
  (`EternalNightWorld`, re-applied on external `/time`). `LightExposureService` samples once per second per
  survival/adventure player from the eye-cell block light: `0..4` → +1 (+1 more if sky is visible and no shelter);
  `5..8` → 0; `>=9` → −2; `victorySafe` → −2. Continuous bright rest (600 ticks = 30 s) resets exposure to 0.
  At `exposure>=50` vanilla Darkness is applied (duration 60 ticks; refreshed only when the remaining duration is
  ≤40 ticks, i.e. always above Darkness's blend-advance 22, so the blend factor never dips and the screen does not
  pulse; never removes a foreign effect); at `>=75` a transient `white_fog:darkness_slow` modifier (×0.85) disables sprint; at `>=90` Condition drains
  0.05/sample and reaching 0 kills via the normal death path. Snapshots on join/respawn/dimension/change/heartbeat.
- **Darkness visual adapter (1.5, client-only).** `DarknessVisualGate` (exposure≥50 + fresh snapshot + alive/not
  creative/spectator, local player only, bounded frame-delta envelope with a 5 s tail covering the 82-tick effect
  drain) + `LightmapRenderStateExtractorMixin`
  (constant `darknessEffectScale`, brightness floor) + `DarknessFogEnvironmentMixin` (fog ≥48, constant factor;
  `voidFactor`/`BLINDNESS`/foreign Darkness preserved). Visual fade-in was slowed (1.11) to
  `FADE_IN_PER_SECOND = 0.4/s` (≈2.5 s) while `FADE_OUT_PER_SECOND` stays 1.0/s; the gate still goes `active()`
  immediately (pulse stays suppressed), so this only changes how fast the adapter strength ramps. Honest limit:
  at light 0 without ambient the frame can still be dark.
- **Fading light sources (1.6).** Vanilla `torch`/`wall_torch`, `soul_torch`/`soul_wall_torch`, `lantern`/
  `soul_lantern`, `campfire`/`soul_campfire` gain exactly one boolean property `white_fog_lit` (campfire reuses
  vanilla `LIT`); unlit emission is 0 and unlit torch/wall torch emit no particles. Per-dimension `LightSourceStore`
  (SavedData) holds per-position records `{sourceUuid, expectedBlockId, remainingTicks, revision, managedByPost}`
  plus initialized-chunk flags: generated sources get a one-time bonus on chunk load (torch 6000, soul torch 4000,
  lantern 12000, soul lantern 8000, campfire 8000 if lit), manual placement starts from the item component (empty =
  unlit, no bonus) and is recorded by the ACTUAL placed block/position — `LightSourceService.commitPlacement` reads
  the real block state at `BlockPlaceContext#getClickedPos()` (26.2 = placement position) and stores its real
  `blockId`, with a narrow 6-neighbor fallback, so `wall_torch`/support-position quirks cannot drop the record.
  The server ticks only loaded+lit records (−1/tick, `1→0` extinguishes); unlit pauses. A 20-tick
  refuel job re-validates everything and consumes exactly one accepted fuel item. Drop/break writes `remaining`+`lit`
  into the single vanilla drop; pistons do not move a managed source that has a store record.
- **Fuel policy + item component (1.6/1.8).** Capacities: torch 12000, soul torch 8000, lantern 24000, soul lantern
  16000, campfire 16000. Coal/charcoal adds exactly 20% (2400/1600/4800/3200/3200); campfire stick +2000, log +8000;
  overflow is refused («Топливный запас заполнен», 40-tick cooldown) without spending. `white_fog:light_fuel` =
  `(remainingTicks, lit)`; persistent codec reads the legacy int-only form (`>0 → lit=true`, `0/negative → 0/false`)
  and encodes the pair (network = VAR_INT+BOOL). A charged stack must have `count==1`; a charged `count>1` is "corrupt"
  (no light, no burn, operations/placement refused, no split).
- **Source menu (1.7).** Right-click a managed source opens the server-authoritative `white_fog:light_source` menu
  (no slots; buttons via vanilla `clickMenuButton`): `Заправить` id 1 (20-tick job), `Потушить` id 2 (immediate,
  preserves fuel), `Зажечь` id 3 (immediate, requires `remaining>0`, spends nothing). `managedByPost` allows
  inspect, refuses actions.   State arrives via S2C `source_panel`; the panel refreshes through `broadcastChanges`.
  Campfire opens the menu with an empty hand only (eating/shovel/flint stay vanilla). All actions re-validate
  rights/range/LOS/source UUID/revision server-side. `LightMenuLayout` sizes each button as
  `labelWidth + 2*BUTTON_H_PAD` in both wide and compact modes, so labels are never tighter than the button.
- **HUD (1.6/1.7/1.11).** `G` toggles a bounded, content-sized Work Panel (named constants, text clamped so nothing
  overflows on narrow windows): nearest source, human time-to-empty `Осталось: X мин Y сек`, offhand portable light
  `В руке: Факел`/`Факел душ`, and a custom eternal-night clock `Ночь · HH:MM` from the world clock (no misleading
  vanilla day). Since 1.11 the source line is built by pure `LightPanelFormat.hudSourceLine/hudFuelLine`:
  placed block wins («Источник: Факел · горит»); otherwise a valid burning offhand light is shown as the source
  («Источник: в руке — Факел» + its `(remaining)` in «Осталось:»); empty/unlit/corrupt offhand keeps
  «Источник: нет рядом». This is display only — `G`'s C2S refuel still targets a placed block only.
- **Portable light (1.7/1.8/1.10).** A charged `torch`/`soul_torch` (`lit && remaining>0 && count==1`) in the
  offhand gives client-side dynamic light (local player only, emission 14/10, 1-per-block falloff) through **three**
  hooks: `LightCoordsUtil.BrightnessGetter` (block-mesh light), `EntityRenderer.getBlockLightLevel` (hand/player
  model light, added in 1.10), and `ItemInHandRenderer.submitHandsWithItems` (first-person hand light arg, added in
  1.11 via `PortableLightClient.raisePackedLight`), and raises the server exposure input to
  `max(vanilla blockLight, emission)` before the unchanged policy. `PortableLightClient` accepts any `BlockGetter` — in 26.2 mesh baking passes a
  `RenderSectionRegion`, not a `ClientLevel` (the 1.10 root cause). A minimal tracker
  (`PortableLightClient.tickSectionRebuilds`, called from `END_CLIENT_TICK`) marks sections dirty via
  `Minecraft.levelExtractor.setSectionDirty(...)` when the offhand emission/position changes, so the mesh is
  re-lit as the player moves (no spatial engine). The inventory tick burns lit source items in the main inventory and
  the offhand exactly once each (`−1`/tick, `1→0` becomes unlit); unlit items pause; item entities/remote inventories
  are not ticked. The tick lives inside the single `END_SERVER_TICK` (no second handler).

## TODO / known gaps

- **First-person torch appearance (accepted limitation).** The offhand torch may look absent/black in first person
  even though its dynamic light works; the user confirmed this in-game and it is treated as non-critical (stage
  1.11). The related 1.9 report (charged offhand torch "not visible in hand", `G` says «Источник: нет рядом») was
  re-audited in 1.10/1.11 and the real root causes were found with `javap` and fixed: the `instanceof ClientLevel`
  gate rejected `RenderSectionRegion` for block-mesh light, and the hand-light path
  (`EntityRenderer.getPackedLightCoords` → `getBlockLightLevel`) plus the `ItemInHandRenderer.submitHandsWithItems`
  packed light arg now raise the offhand contribution. A `STREAM_CODEC` round-trip self-test proves the
  `(remaining,lit)` component survives the network codec (so `streamRoundTrip=true` in the server smoke). Runtime
  diagnostics `WHITEFOG_PORTABLE_LIGHT_STATE` / `WHITEFOG_FP_HAND_LIGHT` are available if the visual issue is
  revisited. No custom torch item model exists; vanilla `minecraft:item/torch` renders 26 quads.
- **ROADMAP «Этап 1.7: Закрытое укрытие» (shelter detector) — not started.** `isSheltered` still returns `false`,
  so the "open sky adds +1 exposure inside a roofed room" rule only depends on `canSeeSky` for now. Planned files:
  `darkness/shelter/{ShelterDetector,ShelterCache,ShelterSnapshot}.java` + `ShelterProvider.isSheltered(player)`.
- **Mod pickaxes** `white_fog:bronze_pickaxe` / `iron_pickaxe` / `steel_pickaxe` are not registered (stage 6.4),
  so hard blocks/stations cannot yet be mined with a mod pickaxe (vanilla tools are refused).
- **Worldgen (stage 2.1)** not implemented: `flat_stone`/`small_stone` do not generate naturally.
- **Station job recipes (stage 3.5)** not implemented: the station menu is empty; the job/escrow/output fields are
  schema-only. `FlatStoneBlock` is `UNCLASSIFIED` for break rules, so an empty station breaks by normal loot.
- **Survival mechanics not implemented yet:** real fatigue/calories/water/condition drain, temperature, clothing
  wetness, dysentery, sleep/work, active goal; the base HUD is still a stub.
- Not part of the manual main-mechanics pass / not separately stress-tested: death/`copyOnDeath`, rejoin after death,
  dimension change, chunk unload/load, two players on one block, spawn-protection/adventure, station save/load with a
  job, hopper/explosion/piston station edge cases, fill/drop round-trip under stress, creative-mode fuel consumption.
- Creative players are not excluded from the inventory fuel tick (matches vanilla; keep if wanted). Any future C2S
  action payload must register its type in the common initializer before its receiver.

## Build & run

All commands from the repo root. Builders run **without a daemon**. Bound every long-running process and read its
log before the next run; kill only your own PID tree (never sweep-kill `java`/`gradle`).

```bat
:: build + UTF-8 log in logs\build_<stamp>.txt
scripts\build.bat
:: or directly:
gradlew.bat build --no-daemon --console=plain
```

Bounded smokes (internal hard timeout; terminate only their own PID tree; non-zero exit unless `status=SUCCESS`):
```bat
scripts\server_smoke.bat   :: writes logs\server_smoke_<stamp>.result
scripts\client_smoke.bat   :: writes logs\client_smoke_<stamp>.result
```

Sandbox self-tests (pure logic, foreground watchdog, UTF-8 log + `.result` in `logs\`; `exit 124` = TIMEOUT):
```bat
tests\break_timer\run_break_timer_selftest.bat
tests\station\run_station_selftest.bat
tests\item_gui_center\run_gui_icon_center_selftest.bat
tests\item_gui_center\run_client_itemmodel_probe.bat
tests\eternal_darkness\{exposure,light,portable_light}\run_*_selftest.bat
tests\darkness_visual\run_darkness_visual_selftest.bat
tests\darkness_visual\run_client_visual_probe.bat
tests\darkness_light_fix\run_darkness_light_fix_selftest.bat   :: 1.9 fix logic (blend stability, placement recording, button padding)
tests\portable_light_dynamic\run_portable_light_dynamic_selftest.bat  :: 1.10 root cause (RenderSectionRegion gate, falloff, entity light, section radius)
```
Expected sandbox sizes: break_timer 118, station 270, item_gui_center 14, exposure 89, light 69,
portable_light 469, darkness_visual 67, darkness_light_fix 23, portable_light_dynamic 81. Sandboxes are **logic-only and NOT runtime proof** — say so in reports.

Key smoke evidence strings (grep fresh logs): `WHITEFOG_CRAFTING_SELFTEST ... status=SUCCESS`,
`block-break rules registered (stage 1.3 ...)`, `darkness light-exposure service registered (stage 1.5 ...)`,
`eternal night enabled (advance_time=false, day_time=18000)`, `WHITEFOG_LIGHT_SELFTEST ... status=SUCCESS`,
`WHITEFOG_LIGHT_FUEL_CODEC_SELFTEST ... streamRoundTrip=true ... status=SUCCESS`, and on the client
`MultiPlayerGameModeMixin applied=true`, `WHITEFOG_PORTABLE_LIGHT_SELFTEST handlers=true` (checks all three
hooks: `LightCoordsUtil.BrightnessGetter`, `EntityRenderer`, `ItemInHandRenderer`), `WHITEFOG_DARKNESS_VISUAL_SELFTEST handlers=true`.
In a real world, `WHITEFOG_PORTABLE_LIGHT_STATE offhand=... emission=... dynamicAtEye=...` is logged once per
offhand light-state change and `WHITEFOG_FP_HAND_LIGHT offhandEmission=... packedBlockBefore=... dynamic=...`
once per first-person hand-light state change (runtime diagnostics; absent in headless smoke because there is no player).

```powershell
# empty output => common is clean
Get-ChildItem -Recurse build\classes\java\main -Filter *.class |
  ForEach-Object { Select-String -Path $_.FullName -Pattern 'net/minecraft/client' -SimpleMatch -Encoding Default }
```

## Project rules (invariants)

- **MC 26.2 Fabric only.** Pin exact versions in `gradle.properties`; never add a `mappings` line.
- **Client/server split.** `src/main` (common) must never import `net.minecraft.client.*`; client code lives only in
  `src/client`. Never call client classes from common/server.
- **One server tick.** Register exactly one `ServerTickEvents.END_SERVER_TICK` (`WhiteFogServer.onEndServerTick`);
  piggyback long-running processes on it. No second player/server tick handler.
- **Payload before receiver.** Register each custom payload type once in the common initializer (`WhiteFogPayloads`)
  before registering its receiver: `PayloadTypeRegistry.serverboundPlay()` for C2S, `clientboundPlay()` for S2C.
- **Verify, never invent.** Before using a class/method/signature/mixin target/JSON shape, confirm it against the
  real 26.2 deobf jars with `javap` (`%USERPROFILE%\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\`
  `minecraft-common-deobf\26.2\` and `...-clientonly-deobf\26.2\`) and the Fabric API jar. An invented identifier is
  the top cause of breakage; data-pack/asset JSON must match the 26.2 schema, not just exist.
- **Server-authoritative.** All timers, distances, resource spends, damage and permissions are checked on the
  server; the client only displays state and sends requests. Spend resources atomically after all checks pass.
- **Registration.** `BlockBehaviour.Properties#setId` / `Item.Properties#setId` are required in 26.2.
  `MenuType`'s constructor is package-private (opened by the `fabric-menu-api-v1` classtweaker); register screens
  with `MenuScreens.register`.
- **HUD** follows global §10: compact content-sized panels, fixed zones, one style, constants for geometry,
  frame-delta animation. Keybinds use `com.mojang.blaze3d.platform.InputConstants.KEY_*`, never raw GLFW codes.
- **Bounded processes.** Bounded tests self-terminate; builders run `--no-daemon --console=plain`; output goes to a
  UTF-8 file (not PowerShell `>`); read logs/`.result` after every run; never launch bare `runClient`/`runServer`.
  Record the PID and kill only your own tree.
- **Tests first, then apply.** Sandboxes live under `tests/` and must prove logic before applying to `src/`.
  They are not runtime proof — never claim runtime/gameplay verification from a sandbox.
- **Git.** Keep this `AGENTS.md` in the repo. Never commit build artifacts/logs/secrets. Never push without explicit
  user approval.

## Decisions / 26.2 traps

- **Attachment API** (`fabric-data-attachment-api-v1`) instead of static fields; `copyOnDeath()` + explicit resync on
  respawn and dimension change. Base sync is a custom S2C NBT snapshot (explicit rate control).
- **Crafting lock via predicates + minimal mixins.** `RecipeManagerMixin` filters `RecipeType.CRAFTING` in
  `apply(RecipeMap, ...)` (runs at start and every `/reload`); one `AbstractContainerMenuMixin` covers all click
  paths incl. client prediction. Closing the transient grid returns items (no loss/duplication).
- **Block breaking: one server timer.** Cancel vanilla `handleBlockBreakAction` at HEAD; commit only on the server
  tick after re-validating. A premature client `STOP` does not cancel the session (26.2 sends STOP on its own
  optimistic completion and `handlePlayerAction` acks the sequence unconditionally); cancel on ABORT/validation.
  Fail-closed on error (never hand control back to vanilla for a managed block).
- **Client prediction:** suppress vanilla for every managed category; `ALLOW` → manual `ServerboundPlayerActionPacket`
  START via shadowed `startPrediction` + `ABORT` on release/change, and return `true` from `continueDestroyBlock`
  so `Minecraft#continueAttack` plays vanilla particles/swing (`DENY_*` → `false` + local overlay message).
- **Flat stone:** own blocks, not a re-used vanilla slab; interactions via Fabric `BlockEvents`; menu via vanilla
  `openMenu`; recovery as a pure server tick; removal/pickup remove the block with loot disabled and insert exactly
  one item (`Inventory#add`, remainder as an `ItemEntity` with pickup delay). Station contents are emitted from
  `FlatStoneBlockEntity#preRemoveSideEffects` (never duplicate the block item).
- **Darkness visual adapter is client-only.** Server formula/thresholds are untouched; the gate keys off the last
  server `darkness_snapshot`. Constant `darknessEffectScale` + brightness floor + milder/constant fog; `voidFactor`,
  `BLINDNESS` and foreign Darkness outside the gate are preserved.
- **Darkness pulse root cause.** Vanilla `MobEffects.DARKNESS` has blend-duration 22 (`setBlendDuration(22)`, javap
  26.2) and `BlendState.tick` fades out once `duration <= 22`. Refreshing at ≤20 made the blend factor dip every
  ~second (pulse). Fix: duration 60 + refresh-at-40, so the factor never leaves 1; the client tail is 5 s to cover
  the 82-tick drain. `DarknessConfig.DARK_BLEND_ADVANCE_TICKS` documents the value.
- **Fading lights:** inject exactly one `white_fog_lit` property (torch/lantern) and reuse `LIT` for campfires; drive
  emission/particles off it; keep a per-dimension `SavedData` store; lazy chunk init grants the generation bonus
  once; the refuel job is the only fuel-spending path; the drop path writes fuel into the single vanilla drop.
- **Fuel semantics:** `Заправить` never lights and never partially spends; `Зажечь` is the only `unlit→lit`; coal =
  20% of capacity; `(remaining, lit)` lives on the item so block↔item round-trips. Inventory fuel tick uses the one
  `END_SERVER_TICK`, iterating `Inventory#getNonEquipmentItems()` + offhand once each.
- **26.2 name traps (javap-checked):** `net.minecraft.resources.Identifier` (not `ResourceLocation`);
  `net.minecraft.world.phys.Vec3`; `net.minecraft.world.entity.EntityTypes`. `ItemStack` has no `is(TagKey)` —
  use `stack.typeHolder().is(tag)`. Game time is `Level#getLevelData().getGameTime()`. Day-night is
  `GameRules.ADVANCE_TIME` + the world clock (`ServerClockManager.setTotalTicks`), not `dayTime`; `ClickType` is
  `ContainerInput`. `BlockEntity` persistence uses `ValueInput`/`ValueOutput` (the attachment uses `CompoundTag`);
  `BlockBehaviour#neighborChanged` takes `Orientation`. Item components need persistent + network codecs.
- **Dynamic-light traps (javap 26.2):** during mesh baking `LightCoordsUtil.BrightnessGetter.lambda$static$0`
  receives a `net.minecraft.client.renderer.chunk.RenderSectionRegion` (a `BlockAndLightGetter`, **not** a
  `ClientLevel`) — do not gate on `instanceof ClientLevel`. `EntityRenderer.getPackedLightCoords` reads
  `getBlockLightLevel(entity,pos)` directly (via `entity.level().getBrightness(BLOCK,…)`), it does **not** call
  `LightCoordsUtil` — so entity/hand light needs its own `EntityRenderer.getBlockLightLevel` hook. Section
  re-mesh is requested with `Minecraft.levelExtractor.setSectionDirty(x,y,z)` (public).
- **First-person hand light (1.11, javap 26.2).** `GameRenderer#renderItemInHand` feeds exactly one packed light
  into `ItemInHandRenderer#submitHandsWithItems(float,PoseStack,SubmitNodeCollector,LocalPlayer,int)`. Our
  `ItemInHandRendererMixin` `@ModifyVariable(index=5, argsOnly=true)` raises it to `max(vanilla block, portable)`
  via `PortableLightClient.raisePackedLight`. Idempotent with the `EntityRenderer` hook; unlit/empty/corrupt
  unchanged; no stack/model swap, no fullbright.
- **HUD source display split from action (1.11).** `LightPanelFormat.hudSourceLine/hudFuelLine` are pure; the HUD
  shows a valid burning offhand as «Источник: в руке — …» when no block is nearby, but `handleLightPanelKey`
  still sends a C2S refuel only for a placed block.
- **Visual fade-in (1.11).** Only `DarknessVisualConfig.FADE_IN_PER_SECOND` changed 2.0 → 0.4/s; server
  thresholds/sample interval and `FADE_OUT_PER_SECOND` unchanged.
- **References (adapted, not copied):** Fabric API (`fabric-events-interaction-v0`, `fabric-menu-api-v1`,
  `fabric-networking-api-v1`, `fabric-lifecycle-events-v1`, `fabric-key-mapping-api-v1`, `fabric-rendering-v1`);
  `Patbox/polymer` (mining), `gnembon/fabric-carpet` (menu click), `Tschipp/CarryOn` (station removal), `Wynntils`/
  `cheatutils` (prediction), `LambdAurora/LambDynamicLights` 26.2 (light: `BrightnessGetterMixin`,
  `EntityRendererMixin`, `SimpleChunkRebuildScheduler`), `Sjouwer/gamma-utils` (fog). Check real jars.
