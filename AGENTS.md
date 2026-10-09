# AGENTS.md — White Fog (`white_fog`)

> Project memory and hand-off. Global rules live in `~/.config/opencode/AGENTS.md`; this file holds only project
> state and rules. Kept short on purpose: current state only — no build logs, no superseded hotfix history.

## Status

- **Target:** Minecraft **26.2** (deobfuscated), Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**,
  Java **25+** (built with JDK 26.0.2.1), Gradle **9.7.1**, Loom **1.18.3** (resolves 1.18.2).
  No Stonecutter / Forge / NeoForge. **No `mappings` line** (26.2 is deobfuscated).
- **Mod:** id `white_fog`, group `com.whitefog`, version `0.1.0`, environment `*` (dedicated server + client).
- **Implemented (top-level roadmap stage, or post-1.6 bugfix/stabilization):**
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
  - **1.7** closed shelter detector + loaded-only collision adapter + UUID runtime cache, wired into the existing
    exposure sample and server lifecycle. Automatic verification is recorded below; live-room acceptance is pending.
  - **1.8** bounded dark-ambient hostile spawning: every 100 loaded server ticks, loaded ticking chunks near a
    vulnerable player (exposure>=75, 24..32 blocks from chunk centre) may add one vanilla
    zombie/skeleton/spider/creeper with a persistent `DARK_AMBIENT` marker, per-chunk cap 2 / per-dimension cap 12.
    Deterministic seed/RNG, loaded-only collision + block-light + shelter checks, Peaceful disables it entirely.
    Automatic verification recorded below; live-world acceptance is pending.
  - **1.9** compact light/goal HUD: the existing `DarknessSnapshotPayload` is extended (single payload) with
    `{revision, light, exposure, conditionMilli, shelter, sourceItemId?, sourceRemainingTicks, goalId, postCount,
    finalState, finalRemainingTicks, finalFuelTicks, waveRemainingTicks}`; snapshot policy = changed no more often
    than 5 ticks + 100-tick heartbeat + immediate join/respawn/dimension; a pure `DarkGoalService` (five exact
    `white_fog:*` goal IDs, monotonic completed flags persisted in the existing `PlayerSurvivalState` attachment
    with absent=false + `copyOnDeath`); one root HUD (`WhiteFogHud`) owns the status panel (`LightWidget` +
    `ExposureWidget`), `GoalWidget`, `WarningWidget` and the existing `G` `LightWorkPanelHud` child; compact
    pre-scale constants (HEIGHT 18 / PAD 4 / ICON 12 / TEXT_GAP 4 / BAR_HEIGHT 2 / ROW_GAP 4 / MAX_TEXT_WIDTH 240),
    scale 0.5, fixed zones, ru_ru/en_us for all five goals + HUD labels/warnings. **Display mode changed 2026-10-09
    by user request:** the compact status is hidden (classes/data kept), the Work Panel is shown permanently, and
    (newest change, same day) the Work Panel gained a darkness/exposure bar, the current-goal panel is back in the
    top-right, and warnings became action-bar messages (see the two "display-mode change" bullets below). Automatic
    verification recorded below; live GUI/manual acceptance is pending.
  - **After stage 1.6 (bugfix/stabilization — NOT a separate roadmap stage):** right-click source menu
    (`Заправить`/`Потушить`/`Зажечь`), bounded HUD layout, offhand portable light; refuel = 20% of capacity,
    `Заправить` never changes `lit`, `(remaining, lit)` component with legacy migration, server-side inventory
    fuel tick, human menu/HUD text, constant fog factor.
  - **Further post-1.6 bugfix/stabilization passes (NOT roadmap stages):** later reported-bug passes fixed the Darkness
    pulse by keeping the vanilla blend factor stable (duration 60 / refresh-at-40 &gt; Darkness blend-advance 22,
    plus client suppression); keyed the light-source placement record by the ACTUAL placed block/position (fixes
    `wall_torch` id mismatch and any support-position quirk) so `nearest`/HUD find it; added the `BUTTON_H_PAD`
    menu padding; made the portable offhand light actually work (accept any `BlockGetter` — mesh baking passes
    `RenderSectionRegion`, not a `ClientLevel`; `PortableLightEntityRendererMixin` for the hand/player light via
    `EntityRenderer.getBlockLightLevel`; `ItemInHandRendererMixin` raising
    `ItemInHandRenderer.submitHandsWithItems`'s packed hand light to `max(vanilla, portable)`; a minimal
    section-rebuild tracker; a `STREAM_CODEC` round-trip self-test); slowed the visual fade-in
    (`DarknessVisualConfig.FADE_IN_PER_SECOND` 2.0 → **0.4/s**, ≈2.5 s, `FADE_OUT_PER_SECOND` unchanged); and split
    the HUD source line from the `G` refuel action (`LightPanelFormat.hudSourceLine/hudFuelLine`).
- **Verification state:** `gradlew build` + bounded server/client smokes pass; every sandbox passes. The current
  version — **upper-level roadmap stages 1.1–1.6 complete, plus the post-1.6 bugfix/stabilization passes and the
  stage 1.7 shelter / diagnostics / light-round-trip work (automatic verification only; its live-room acceptance is
  still pending)** — was **personally verified in-game by the user across the main mechanics**, and that manual pass
  is what surfaced the fixed bugs (Darkness pulse, abrupt fade-in, misleading HUD source line, menu button sizing,
  first-person torch). Verified in-game: eternal night/exposure, darkness behavior/no pulse, fading light sources /
  fuel / menu / actions, `G` panel, offhand portable light, placement/drop/persistence, adaptive menus. The stage
  1.7 shelter detector, the debug diagnostics and the light drop/placement round-trip have only automated evidence
  (sandboxes, startup fixtures, build, smokes) and still require live in-game acceptance. Sandboxes and
  smokes remain **supplementary** automatic logic checks, **not** a substitute for the manual acceptance. Known
  accepted limitation: the first-person offhand torch may look absent/black while its dynamic light still works
  (confirmed by the user, treated as non-critical). `PLAYER_GUIDE.md` is a compact player description
  (status / features / interactions / fuel / limits / checks), not a manual checklist.
- **Roadmap status (important):** the upper-level roadmap stages **1.1–1.6 are complete; stage 1.7 (shelter),
  stage 1.8 (dark-ambient spawning) and stage 1.9 (compact light/goal HUD) are implemented (automatic verification
  recorded below; live-room/live-world/live-GUI acceptance still pending)**; all later work on fading
  sources, the Darkness pulse, dynamic light, HUD, menu and UI shipped as **post-1.6 bugfix/stabilization, not as
  separate roadmap stages 1.7/1.8/1.10/1.11** (the post-1.6 HUD/menu/UI passes predate the 1.9 ticket; the 1.9
  ticket itself IS the compact light/goal HUD). The single ticket «Этап 1.7: Закрытое укрытие и адаптер света»
  (shelter detector) is implemented in `darkness/shelter/` and
  connected through `LightExposureService.isSheltered(...)`. `ROADMAP_STEPS.md` is kept **physically in the working
  tree but removed from the Git repository/GitHub** (`git rm --cached`, ignored in `.gitignore`), so it is a
  local-only planning file. Do not confuse that
  ticket's label "1.7" with the source-menu / portable-light work, which was a post-1.6 bugfix, not a roadmap stage.
- **Stage 1.7 integration (2026-10-09, approved main, committed locally; not pushed):** production pure classes are tested
  directly by `tests/eternal_darkness/shelter/runner.ps1`; the original 216 assertions plus five shape-neighbor
  dependency checks pass: `logs/shelter_selftest_20261009_125000_342_ebe9fe26.{txt,result}`, 221 assertions, strict
  `javac -Xlint:all -Werror`, SUCCESS, 1136 ms total / 98 ms Java. Existing API evidence:
  `logs/shelter_api_20261009_123107_242_4a78bd76.txt`, SUCCESS, 13215 ms, local MC/Fabric jar hashes and bytecode.
  Ad Astra `FloodFill3D.java` reference was read again; independently adapted FIFO/face-sealing idea, not copied.
  Main source integration was explicitly approved by the user; existing dirty work was preserved.
  The architect personally reran the sandbox, build and both smokes and read their complete raw logs/results.
  Build/smoke acceptance is supplementary; live-room/manual acceptance remains pending.
  Final build via `scripts/build.bat`: `logs/build_20261009_125526.txt`, SUCCESS, 5 s, Loom 1.18.3, exit 0.
  Server: `logs/server_smoke_20261009_125231_994_bff572b8.{txt,result}`, SUCCESS, 34365 ms;
  client: `logs/client_smoke_20261009_125707_347_ba6ab741.{txt,result}`, SUCCESS, 19335 ms.
  Both logs report `WHITEFOG_SHELTER_SELFTEST assertions=84 handlers=true status=SUCCESS` (91/65 ms);
  these are real vanilla shape fixtures + transformed handler checks, not live world acceptance.
  Client Darkness/portable-light self-tests also report SUCCESS. Smoke roots 15100 and 4808 exited (CIM empty);
  the prior common class scan has zero client references; architect `git diff --check` is clean except CRLF warnings.
  Runtime adapter correction: CHECK was replaced with read-only entity-map lookup.
- **Diagnostics/drop round-trip pass (2026-10-09, committed locally; not pushed):** added read-only
  `darkness/LightDiagnostics.java` and expanded dev-only `/whitefog debug [player]` with Russian-friendly
  shelter/exposure/light/Darkness/hands/source/lifecycle details. `LightExposureService` only keeps last-sample
  diagnostic bookkeeping; gameplay policy is unchanged. Structured `WHITEFOG_LIGHT_*` markers cover event-only
  shelter failures and light placement/drop/state transitions. `BreakTimerService` captures pre-removal source
  state before `destroyBlock`, so `Block.popResource` preserves `lit=false` for fueled extinguished standing and
  wall torches. Reference: LambDynamicLights `api/src/main/java/dev/lambdaurora/lambdynlights/api/item/ItemLightSource.java`
  (`https://github.com/LambdAurora/LambDynamicLights/blob/1.21.11/api/src/main/java/dev/lambdaurora/lambdynlights/api/item/ItemLightSource.java`).
  Exact sandbox round-trips, build, server smoke and client smoke pass; API evidence is in `logs/api_diag_20261009.txt`
  and `logs/api_diag_20261009_bytecode.txt`.
- **Architect correction pass (2026-10-09, committed locally; not pushed — live/manual acceptance still pending):** vanilla player break now wraps
  `ServerPlayerGameMode.destroyBlock(BlockPos)` with MixinExtras 0.5.5 `@WrapMethod` and `try/finally`;
  custom break restores the prior frame in `finally`. Context matches level identity and immutable position;
  unmatched contexts use the real neighboring state, expected block id and actual item kind are checked, AIR
  does not manufacture fuel. Shared `LightFuelRoundTrip` is used by production placement/drop conversions.
  Static `SUPPORTED` was removed; server startup executes 98 fixture assertions using actual BlockState,
  ItemStack/component, isolated LightSourceStore, scoped context, duplicate/negative and stream-codec cases.
  Latest marker: `WHITEFOG_LIGHT_ROUNDTRIP_SELFTEST assertions=98 ... status=SUCCESS`.
  `ShelterCache.diagnose` has seven production sandbox regressions for OPEN_VOLUME/read errors/UNLOADED and
  unchanged cache (228 assertions total). Current/last-sample data is separated, diagnostics does not create
  exposure sessions or shelter cache entries, persistent describe output is restored, debug lines also go to
  INFO `WHITEFOG_DEBUG`. Report includes break/recovery jobs, light job/menu revision, actual night rule/clock,
  and loaded-only bounded DDA station fields; DDA selects the first non-air voxel, not precise vanilla shape hit.
  Added transition logs for break start/commit/cancel/deny, station pickup/removal/menu, recovery, player lifecycle,
  manual light actions/refuel completion/cancellation and shelter/exposure threshold changes.
  Remaining review gaps: exhaustive refusal reason coverage/rate limiting, precise station shape targeting,
  actual action-chain/live world acceptance and whole-command runtime fixture coverage are NOT proven.
  Latest evidence: `logs/darkness_light_fix_selftest_20261009_151656.txt` (67, SUCCESS);
  `logs/shelter_selftest_20261009_150610_112_10f5c72d.{txt,result}` (228, SUCCESS, 1228 ms);
  `logs/build_20261009_151722.txt` (SUCCESS, 7s);
  `logs/server_smoke_20261009_151802_951_6ac7d630.{txt,result}` and
  `logs/client_smoke_20261009_151846_665_c116fd4c.{txt,result}` (SUCCESS).
  API evidence: `logs/api_servergamemode_20261009.txt`, `api_blockdrops_20261009.txt`,
  `api_mixinextras_20261009.txt`, `api_diag_look_20261009.txt`. Re-read LambDynamicLights ItemLightSource
  reference saved in `logs/reference_ItemLightSource_20261009.java`; it is item-light predicate design, not a
  fuel persistence implementation. Startup fixtures/sandboxes are supplementary, not live pickup proof.
- **Stage 1.8 dark-ambient spawning (2026-10-09, local; not committed/pushed automatically, live acceptance pending):**
  pure classes `darkness/spawn/{DarkSpawnPolicy,DarkMobState}.java` are tested directly by
  `tests/eternal_darkness/spawn/runner.ps1` (103 assertions, strict `javac -Xlint:all -Werror`, UTF-8 log +
  `.result`): `logs/dark_spawn_selftest_20261009_171445_342_5579ec0c.{txt,result}`, SUCCESS, 1078 ms (88 ms Java);
  the bounded timeout probe yields `status=TIMEOUT`/`exit=124` (`logs/dark_spawn_selftest_20261009_171650_239_03b44437.*`).
  Build `logs/build_20261009_171520.txt` SUCCESS; server smoke
  `logs/server_smoke_20261009_171541_392_ec1741bc.{txt,result}` SUCCESS (logs
  `White Fog: dark-ambient spawn service registered (stage 1.8, server-authoritative)`, no errors);
  client smoke `logs/client_smoke_20261009_171617_837_793f046e.{txt,result}` SUCCESS; common class scan clean.
  Server-side adapter classes (`DarkSpawnService`, `DarkSpawnStore`) are compiled by Gradle only (not covered by
  the pure sandbox). New API was verified with `javap` against the 26.2 deobf jars: `EntityType.spawn/create`,
  `EntitySpawnReason.*`, `Mob.checkSpawnRules/checkSpawnObstruction/finalizeSpawn`, `WorldBorder.isWithinBounds`,
  `CollisionGetter.noCollision`, `ChunkMap.forEachBlockTickingChunk`, `SavedDataStorage.computeIfAbsent/get`,
  Fabric `ServerEntityEvents.ENTITY_LOAD/UNLOAD` (hooks `ServerLevel.EntityCallbacks.onTrackingStart/onTrackingEnd`)
  and the attachment `EntityMixin` persistence inject into `Entity#save/load`. Open-source references read & adapted
  (not copied): `Glitchfiend/SereneSeasons` `RandomUpdateHandler` (ticking-chunk + player-proximity iteration),
  `VazkiiMods/Botania` `GaiaGuardianEntity` (`finalizeSpawn` + `addFreshEntity`), `MinecraftForge`
  `ForgeEventFactory#checkSpawnPosition` (`checkSpawnRules && checkSpawnObstruction`),
  `AlexModGuy/AlexsMobs` / `bonsaistudi0s/Creeper-Overhaul` (`isDarkEnoughToSpawn` pattern). Sandbox/fixtures are
  **supplementary**, not live-spawn proof.
- **Stage 1.9 compact light/goal HUD (2026-10-09, local; not committed/pushed automatically, live GUI acceptance
  pending):** pure classes `darkness/goal/DarkGoalService.java`, `darkness/SnapshotRevisionGate.java`,
  `darkness/light/LightTieBreak.java` and client `client/hud/DarkHudLayout.java` are tested directly by
  `tests/eternal_darkness/hud/runner.ps1` (116 assertions, strict `javac -Xlint:all -Werror`, UTF-8 log + `.result`):
  `logs/hud_selftest_20261009_210706_814_724448b3.{txt,result}`, SUCCESS, 945 ms (17 ms Java). Build
  `logs/build_20261009_211345.txt` SUCCESS; server smoke
  `logs/server_smoke_20261009_211416_856_bb19851f.{txt,result}` SUCCESS; client smoke
  `logs/client_smoke_20261009_211500_507_5b25af3e.{txt,result}` SUCCESS with
  `WHITEFOG_HUD_SELFTEST widgets=5 registered=true status=SUCCESS`; common class scan is clean (no
  `net/minecraft/client` references). `DarknessSnapshotPayload` now uses a hand-written `StreamCodec.of` (13 fields,
  nullable source id; `StreamCodec.composite` caps at 12); server source field comes from the existing
  `LightSourceService.nearest(eyePos, 8)`; goal flags/post/final/wave persist in `PlayerSurvivalState` (absent=false).
  `LightSourceService.isTieBetter` delegates to the pure `LightTieBreak`. The single HUD entrypoint is
  `WhiteFogHud` (Fabric `root_hud`); `LightWorkPanelHud` is now a child widget, not a separate registration.
  Sandbox/fixtures are **supplementary**, not live-GUI proof.
- **HUD bugfix/stabilization (2026-10-09, local; NOT a roadmap stage; live GUI acceptance pending):** fixed the two
  reported compact-HUD bugs and unified the `G` panel style. (1) Offhand source: `LightWidget` now resolves the
  displayed source through the pure `client/hud/HudSourceDisplay.java`, which merges the server snapshot placed
  source with the LOCAL offhand using the exact production `PortableLightService.kindForStack` /
  `LightFuelComponent` / `PortableLightPolicy.active` semantics (no divergent validity formula); deterministic
  priority = placed source wins, else valid burning offhand (`lit=true`, `remaining>0`, `count==1`, recognized
  torch/soul torch), else `Источник не найден`. Offhand shows localized `В руке: <Факел|Факел душ> · N с`
  (`hud.white_fog.source.held`/`source.kind.torch`/`source.kind.soul_torch`, ru_ru/en_us) with the real offhand item
  icon and `N=ceil(remaining/20)`; display-only — `G` still refuels the placed block only. (2) Goal boundary:
  `GoalWidget` no longer uses the unused `PAD+ICON+TEXT_GAP` text X; `DarkHudLayout.goalTextX()==PAD` and the new
  `goalPanelLogicalWidth()` guarantee `textX + textWidth <= panel` after scaling at 320x180/1280x720/1920x1080,
  preserving the 120 real-px cap, 4 px margin and ≤2 ellipsized lines. (3) Unified style: `LightWorkPanelHud` was
  refactored off its old `BG/BORDER/TITLE/TEXT/MUTED` palette and oversized geometry onto `DarkHudLayout`
  (`COLOR_BACKGROUND/ACCENT/DANGER/TEXT`, `PAD/ROW_GAP/HEIGHT/MAX_TEXT_WIDTH/SCALE`), gets its content-sized logical
  size + scaled pose from `WhiteFogHud`, and shares the accent top edge/shadowed text. `DarkHudLayout.WORK_PANEL_ROWS=6`
  (later raised to 7 in the darkness-bar pass) and `statusVisibleWithWorkPanel(true)==false`: while the `G` panel is visible the compact status is hidden
  (single bottom-left zone, no overlap). Sandbox extended: `tests/eternal_darkness/hud/runner.ps1` now also compiles
  `PortableLightPolicy`/`LightConfig`/`HudSourceDisplay` read-only and runs **263 assertions**, SUCCESS in
  `logs/hud_selftest_20261009_214010_245_410d985a.{txt,result}` (1144 ms); build
  `logs/build_20261009_214023.txt` SUCCESS; server smoke
  `logs/server_smoke_20261009_214050_887_d4e875c6.{txt,result}` SUCCESS; client smoke
  `logs/client_smoke_20261009_214126_986_86cd39ef.{txt,result}` SUCCESS with
  `WHITEFOG_HUD_SELFTEST widgets=5 registered=true status=SUCCESS`; common class scan clean. Automatic evidence only;
  the visual live acceptance (pixels/Cyrillic widths/overlap) is still pending.
- **Stage 1.9 display-mode change (2026-10-09, local; NOT a roadmap stage; live GUI acceptance pending):** by user
  request the compact stage-1.9 HUD display is **replaced** by the permanently visible light Work Panel; the
  production classes/data are kept for future use. Pure policy added to `client/hud/DarkHudLayout.java`:
  `shouldShowCompactStageHud()` (always `false`), `compactStageHudVisible(gameHidden, hasData)` (always `false`),
  `workPanelAlwaysVisible()` (always `true`) and `workPanelVisible(gameHidden)` (hidden only when the whole
  gameplay HUD is hidden). `WhiteFogHud.extractRenderState` now derives `compactVisible`/`panelVisible` from that
  policy: the status (`LightWidget`+`ExposureWidget`), goal (`GoalWidget`) and warning (`WarningWidget`) are still
  constructed but never render; the Work Panel (`LightWorkPanelHud`) renders whenever `!gameHidden`. Gameplay hide
  conditions are unchanged (no player, death, spectator, F1, any open `Screen`); chat only gates the now-disabled
  compact status. `G` remains a **separate** quick action (`consumeClick()` + C2S `white_fog:light_refuel` in
  `WhiteFogClient`) and does **not** control panel visibility; the HUD root no longer references `lightPanelKey`.
  No second HUD registration (still exactly one `HudElementRegistry.addLast(WhiteFog.id("root_hud"), ...)`).
  Sandbox `tests/eternal_darkness/hud/` extended (source-scan regressions for one root / key separation / retained
  widgets + pure-policy assertions) to **279 assertions**, SUCCESS in
  `logs/hud_selftest_20261009_215808_668_c4f80704.{txt,result}`; build `logs/build_20261009_215826.txt` SUCCESS;
  server smoke `logs/server_smoke_20261009_215847_165_a52bfaf0.{txt,result}` SUCCESS; client smoke
  `logs/client_smoke_20261009_215916_192_d49fee8e.{txt,result}` SUCCESS with
  `WHITEFOG_HUD_SELFTEST widgets=5 registered=true status=SUCCESS`; common class scan clean. Automatic evidence only;
  no live visual acceptance.
- **Work Panel darkness bar + goal top-right + action-bar warnings (2026-10-09, local; NOT a roadmap stage; live GUI
  acceptance pending):** newest display-mode pass. (1) The permanent bottom-left Work Panel is the **only** bottom
  panel and now contains a thin darkness/exposure bar with the localized label `Тьма: E%` (`hud.white_fog.dark`).
  The bar value comes from the server `DarknessSnapshotPayload.exposure` via `ClientDarknessState.lightExposure()`
  (client never computes exposure), is normalized/clamped 0..1 by the pure `DarkHudLayout.exposureNormalized`,
  and is smoothed by the pure `client/hud/DarknessBar.java` with the shared `DarkHudLayout.approach` (frame dt).
  New pure layout: `WORK_PANEL_ROWS = 7` (title + bar + five info rows), `darknessBarRow() = 1`,
  `darknessBarLogicalY()`, `darknessBarTrackWidth(panelWidthLogical)`; the bar stays inside row 1, inside the panel
  and above the hotbar zone at 180/720/1080. The bar track is drawn in `LightWorkPanelHud.renderBackground`, the
  accent→danger gradient fill in `renderIcons` — same palette/thickness as the old status bar. (2) The compact
  `Текущее задание` panel is **back** in the top-right as the existing `GoalWidget` child, enabled only by the pure
  `DarkHudLayout.stageGoalVisible(gameHidden, hasData, width, height)` (hidden: no player/death/spectator/F1/open
  `Screen`, no server snapshot, or <320x180); it still uses localized `goal.white_fog.*` (never raw IDs), ≤2 wrapped
  lines, ellipsis, 4 px right margin, 120 real-px cap. (3) Warnings are **no longer a HUD window**: `WarningWidget`
  stays constructed (constants now delegate to `DarkWarningPolicy`) but `WhiteFogHud` never updates or renders it,
  and no warning rectangle is drawn. Instead the pure `client/hud/DarkWarningPolicy.java` (thresholds 75/90, reasons
  NONE/FIND_LIGHT/DRAIN, 40-tick visual cooldown, one emission per reason change, never NONE, frame-rate independent)
  drives a normal action-bar message: on emit the root calls `LocalPlayer.sendOverlayMessage(Component)` with
  `hud.white_fog.warning.find_light`/`.drain` (same pattern as `Слишком крепко — нужен инструмент`,
  `LocalPlayer#sendOverlayMessage → ChatListener.handleOverlay`, javap-checked), only while a gameplay player exists.
  (4) Unchanged: exactly one `HudElementRegistry.addLast(WhiteFog.id("root_hud"), ...)`; the compact status
  (`LightWidget`+`ExposureWidget`) stays constructed but hidden; `G` remains the separate `consumeClick` +
  C2S `white_fog:light_refuel` action and does not control panel visibility; stage-1.9 server snapshot/goal
  persistence untouched. Sandbox `tests/eternal_darkness/hud/runner.ps1` now also compiles `DarkWarningPolicy` +
  `DarknessBar` read-only and runs **596 assertions**, SUCCESS in
  `logs/hud_selftest_20261009_221835_374_80f72b78.{txt,result}` (1110 ms); build `logs/build_20261009_221846.txt`
  SUCCESS; server smoke `logs/server_smoke_20261009_221909_703_82388929.{txt,result}` SUCCESS; client smoke
  `logs/client_smoke_20261009_221940_073_cd06c9a4.{txt,result}` SUCCESS with
  `WHITEFOG_HUD_SELFTEST widgets=5 registered=true status=SUCCESS`; common class scan clean. Automatic evidence only;
  no live pixel/visual acceptance (real Cyrillic widths, bar/panel overlap, action-bar text, goal wrapping at GUI
  scales 1..4 remain unproven).
- **Git (current publication state, 2026-10-09):** repo `https://github.com/belzoralolbtw/white_fog` (PUBLIC),
  `origin/master`; `origin/master == local HEAD`, working tree clean. **Everything through stage 1.7 + 1.8 + 1.9 and
  the HUD work is now published.** Current base commit
  **`aa2ff8933adfee30b11d7d4c471652225bc2cdb8`** (`feat: add dark ambient spawning and compact HUD`). Full chain on
  `origin/master`: `ff5c05a` (`feat: add fading light sources and portable lighting`, post-1.6 work) →
  `12db179`/`439f5a5` (`docs: update project memory after release`) → `3aaac84` (`docs: record manual gameplay
  acceptance`) → `75a0999` (`docs: align roadmap stage status`) → `9c28fba` (`docs: update project memory after
  roadmap sync`) → `af17f8d` (`chore: replace CC0 with proprietary license`) → `f48a6eb` (`feat: complete shelter and
  light diagnostics stage 1.7`) → `bdb610d` (`docs: publish stage 1.7 documentation`) → **`aa2ff89`** (`feat: add dark
  ambient spawning and compact HUD`: stage 1.8 + 1.9 + HUD). The "committed locally; not pushed" /
  "local; not committed/pushed" labels in the historical bullets above record the state at the time and are now
  superseded by this pushed chain. `ROADMAP_STEPS.md` stays removed from the index (file on disk, `.gitignore`d).
  Never push future work without explicit user approval.

## Structure

```
src/main/java/com/whitefog/            # COMMON — must NOT import net.minecraft.client.*
  WhiteFog.java                        # common initializer: payload types -> attachment -> server services -> crafting lock
  WhiteFogConfig.java                  # project tunables/defaults (Russian comments)
  WhiteFogAttachments.java             # AttachmentType<PlayerSurvivalState> (persistent + copyOnDeath)
  state/PlayerSurvivalState.java       # player state: survival + darkness fields; 1.9 goal flags/post/final/wave; NBT Codec; revision
  server/WhiteFogServer.java           # the ONE END_SERVER_TICK; join/respawn/dimension; disconnect; dev command
  server/SurvivalTicker.java / PlayerStateSyncService.java # interval logic + base state snapshot send
  crafting/CraftingLock.java           # 1.2: slot predicates, recipe filter, crafting-table use block, self-test
  breaking/BlockBreakRules.java / BlockBreakPolicy.java # 1.3: Category/ToolKind classification, ALLOW/DENY_* + durations
  breaking/BreakTimerService.java / BreakSession.java / StationRemoval.java # 1.3: timer, session, safe station removal
  content/WhiteFogContent.java         # 1.4 + post-1.6: block/BlockItem/BlockEntityType/MenuType registration (setId required)
  content/block/FlatStoneBlock.java / SmallStoneBlock.java # 1.4: station + small stone (SHAPE, instabreak, VARIANT)
  content/block/entity/FlatStoneBlockEntity.java # 1.4: schemaVersion/owner/escrow/output/progress/mode/revision
  content/item/GroundPlacedBlockItem.java / content/menu/FlatStoneMenu.java # 1.4: top-face placement + empty menu
  content/menu/LightSourceMenu.java    # post-1.6: empty source menu; clickMenuButton -> server; broadcastChanges -> refresh
  station/FlatStoneInteractions.java / FlatStoneRemoval / SmallStonePickup / StationDropHelper / RecoveryService # 1.4
  darkness/DarknessConfig.java / LightExposurePolicy.java / LightExposureService.java # 1.5: exposure/Condition/speed
  darkness/EternalNightWorld.java      # 1.5: Overworld clock=18000 + GameRules.ADVANCE_TIME=false (+ diagnostics record)
  darkness/LightDiagnostics.java       # 1.7: read-only diagnostic snapshot (shelter/exposure/light/hands) for `/whitefog debug`
  darkness/shelter/{Voxels,CollisionMasks,ShelterDetector,ShelterSnapshot,ShelterCache,ShelterProvider}.java # 1.7
  darkness/shelter/ShelterRuntimeSelfTest.java # dev-only real vanilla collision fixtures + mixin handler check
  darkness/shelter/ShelterProvider.java also exposes read-only `isShelteredAt(ServerLevel,BlockPos)` (stage 1.8)
  darkness/spawn/DarkSpawnPolicy.java / DarkMobState.java # 1.8: pure seed/RNG/candidate/cap policy + attempts/index/reservations
  darkness/spawn/DarkSpawnService.java / DarkSpawnStore.java # 1.8: server adapter (attachment marker + lifecycle) + per-dimension SavedData
  darkness/goal/DarkGoalService.java   # 1.9: pure five exact white_fog:* goal IDs + monotonic flags + active goal (no MC)
  darkness/SnapshotRevisionGate.java   # 1.9: pure client revision order gate (equal revision = heartbeat)
  darkness/light/LightConfig.java / LightFuelPolicy.java / LightSourceBlocks.java # 1.6: fuel/capacity + WHITE_FOG_LIT (+1.9 itemId)
  darkness/light/LightTieBreak.java    # 1.9: pure deterministic nearest tie-break (x/y/z)
  darkness/light/LightFuelComponent.java / LightSourceStore.java # 1.6 + post-1.6: item component + per-dimension SavedData
  darkness/light/LightSourceService.java / LightSourceInteractions.java # 1.6 + post-1.6: scan/tick/refuel/nearest/drop/receiver
  darkness/light/LightFuelRoundTrip.java # 1.7: shared placement/drop fuel conversion + server-startup 98-assertion fixture self-test
  darkness/light/PortableLightPolicy.java / PortableLightService.java # post-1.6: offhand light + inventory fuel tick
  darkness/light/LightPanelFormat.java / SourceActionPolicy.java / LightMenuLayout.java # post-1.6 pure UI/action rules
  network/*.java                       # payload records (see Networking below) — registered once in WhiteFogPayloads
                                       #   1.9: DarknessSnapshotPayload extended (13 fields, hand-written StreamCodec.of)
  mixin/AbstractContainerMenuMixin.java / RecipeManagerMixin.java / ServerGamePacketListenerImplMixin.java # 1.2
  mixin/ServerPlayerGameModeMixin.java # 1.3: handleBlockBreakAction HEAD -> BreakTimerService
  mixin/light/*.java                   # 1.6: lit property, emission, particles, placement, drop, piston, ignite
  mixin/light/ServerPlayerGameModeDropContextMixin.java # 1.7: @WrapMethod destroyBlock -> push/restore DropContext (pre-removal state)
  mixin/shelter/LevelChunkShelterMixin.java # 1.7: successful setBlockState RETURN -> bbox+1 invalidation
src/client/java/com/whitefog/client/   # CLIENT — client API only
  WhiteFogClient.java                  # ClientModInitializer: receivers, HUD, keybinds, disconnect clear, dev self-checks
  ClientPlayerState.java / ClientDarknessState.java / ClientLightState.java # client caches (never source of truth)
  network/WhiteFogClientNetworking.java / DarknessClientNetworking.java / LightClientNetworking.java # receivers
  hud/LightWorkPanelHud.java / hud/WhiteFogHud.java # 1.6 + post-1.6 Work Panel (child, now permanent) + single 1.9 root HUD
  hud/{DarkHudLayout,HudWidget,HudSourceDisplay,LightWidget,ExposureWidget,GoalWidget,WarningWidget,DarkWarningPolicy,DarknessBar}.java # 1.9: status (constructed, hidden) + top-right goal (stageGoalVisible) + darkness bar (DarknessBar, WORK_PANEL_ROWS=7) + action-bar warning policy (DarkWarningPolicy); offhand source display; visibility policy
  screen/FlatStoneScreen.java / LightSourceScreen.java # 1.4 / post-1.6 adaptive screens
  portable/PortableLightClient.java    # post-1.6 offhand dynamic light (local player only) + section-rebuild tracker
  darkness/DarknessVisualConfig.java / DarknessVisualGate.java # 1.5 visual adapter (frame-delta envelope)
  mixin/MultiPlayerGameModeMixin.java  # 1.3 client prediction: cancel vanilla, manual START/ABORT, swing on ALLOW
  mixin/PortableLightBrightnessGetterMixin.java # post-1.6 LightCoordsUtil.BrightnessGetter hook (block mesh light)
  mixin/PortableLightEntityRendererMixin.java # post-1.6 EntityRenderer.getBlockLightLevel hook (hand/player light)
  mixin/ItemInHandRendererMixin.java   # post-1.6 first-person hand light arg raised to max(vanilla, portable)
  mixin/LightmapRenderStateExtractorMixin.java / DarknessFogEnvironmentMixin.java # 1.5 visual (no pulse / milder fog)
  dev/ItemModelSelfCheck.java          # 1.4 dev-only item-model bake check (WHITEFOG_ITEM_MODEL_SELFTEST)
src/main/resources/                    # fabric.mod.json, white_fog.mixins.json, client mixin config in src/client/resources
  assets/white_fog/...                 # flat_stone + small_stone + unlit source models/textures/lang
  assets/minecraft/blockstates/*.json  # 1.6: overrides vanilla torch/wall_torch/soul_*/lantern to add white_fog_lit
  data/white_fog/loot_table/blocks/    # 1.4: flat_stone.json, small_stone.json (no survives_explosion)
tests/                                 # independent sandboxes (NOT part of build, never touch src/): break_timer/ station/
                                       #   item_gui_center/ eternal_darkness/{exposure,light,portable_light,shelter,spawn,hud}/ darkness_visual/
                                       #   darkness_light_fix/ portable_light_dynamic/
scripts/build.bat|server_smoke.bat|client_smoke.bat
README.md  PLAYER_GUIDE.md   # README = project/roadmap; PLAYER_GUIDE = compact player guide (not a checklist)
ROADMAP_STEPS.md         # local-only planning file: kept on disk, REMOVED from the repo/GitHub (.gitignore)
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
  `voidFactor`/`BLINDNESS`/foreign Darkness preserved). Visual fade-in was slowed (post-1.6) to
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
- **Fuel policy + item component (1.6 + post-1.6).** Capacities: torch 12000, soul torch 8000, lantern 24000, soul lantern
  16000, campfire 16000. Coal/charcoal adds exactly 20% (2400/1600/4800/3200/3200); campfire stick +2000, log +8000;
  overflow is refused («Топливный запас заполнен», 40-tick cooldown) without spending. `white_fog:light_fuel` =
  `(remainingTicks, lit)`; persistent codec reads the legacy int-only form (`>0 → lit=true`, `0/negative → 0/false`)
  and encodes the pair (network = VAR_INT+BOOL). A charged stack must have `count==1`; a charged `count>1` is "corrupt"
  (no light, no burn, operations/placement refused, no split).
- **Source menu (post-1.6).** Right-click a managed source opens the server-authoritative `white_fog:light_source` menu
  (no slots; buttons via vanilla `clickMenuButton`): `Заправить` id 1 (20-tick job), `Потушить` id 2 (immediate,
  preserves fuel), `Зажечь` id 3 (immediate, requires `remaining>0`, spends nothing). `managedByPost` allows
  inspect, refuses actions.   State arrives via S2C `source_panel`; the panel refreshes through `broadcastChanges`.
  Campfire opens the menu with an empty hand only (eating/shovel/flint stay vanilla). All actions re-validate
  rights/range/LOS/source UUID/revision server-side. `LightMenuLayout` sizes each button as
  `labelWidth + 2*BUTTON_H_PAD` in both wide and compact modes, so labels are never tighter than the button.
- **HUD (1.6 + post-1.6).** The bounded, content-sized Work Panel (named constants, text clamped so nothing
  overflows on narrow windows) is **permanently visible** during normal gameplay — it is **not** toggled by `G`; the
  pure `DarkHudLayout.workPanelAlwaysVisible()` policy shows it whenever the gameplay HUD is shown. It contains:
  nearest source, human time-to-empty `Осталось: X мин Y сек`, offhand portable light
  `В руке: Факел`/`Факел душ`, a custom eternal-night clock `Ночь · HH:MM` from the world clock (no misleading
  vanilla day), and (newest pass) a thin `Тьма: E%` darkness/exposure bar. In a later post-1.6 pass the source line
  is built by pure `LightPanelFormat.hudSourceLine/hudFuelLine`:
  placed block wins («Источник: Факел · горит»); otherwise a valid burning offhand light is shown as the source
  («Источник: в руке — Факел» + its `(remaining)` in «Осталось:»); empty/unlit/corrupt offhand keeps
  «Источник: нет рядом». `G` remains a **separate** quick refuel action (`consumeClick()` + C2S
  `white_fog:light_refuel`) that never controls panel visibility, and this refuel is display-independent — it still
  targets a placed block only.
- **Portable light (post-1.6).** A charged `torch`/`soul_torch` (`lit && remaining>0 && count==1`) in the
  offhand gives client-side dynamic light (local player only, emission 14/10, 1-per-block falloff) through **three**
  hooks: `LightCoordsUtil.BrightnessGetter` (block-mesh light), `EntityRenderer.getBlockLightLevel` (hand/player
  model light, added in a post-1.6 pass), and `ItemInHandRenderer.submitHandsWithItems` (first-person hand light arg, added in
  a later post-1.6 pass via `PortableLightClient.raisePackedLight`), and raises the server exposure input to
  `max(vanilla blockLight, emission)` before the unchanged policy. `PortableLightClient` accepts any `BlockGetter` — in 26.2 mesh baking passes a
  `RenderSectionRegion`, not a `ClientLevel` (the root cause). A minimal tracker
  (`PortableLightClient.tickSectionRebuilds`, called from `END_CLIENT_TICK`) marks sections dirty via
  `Minecraft.levelExtractor.setSectionDirty(...)` when the offhand emission/position changes, so the mesh is
  re-lit as the player moves (no spatial engine). The inventory tick burns lit source items in the main inventory and
  the offhand exactly once each (`−1`/tick, `1→0` becomes unlit); unlit items pause; item entities/remote inventories
  are not ticked. The tick lives inside the single `END_SERVER_TICK` (no second handler).
- **Dark-ambient hostile spawning (1.8, server-authoritative).** A second pass inside the same `END_SERVER_TICK`
  (`WhiteFogServer.onEndServerTick` → `DarkSpawnService.tickAll`) runs only when `tickCount % 100 == 0`. Per
  non-Peaceful level it collects loaded **block-ticking** chunks (`ServerChunkCache.chunkMap.forEachBlockTickingChunk`),
  builds the alive survival/adventure player inputs, and for each chunk: target = nearest eligible player within
  `[24,32]` blocks of the chunk centre (tie → lexicographically smaller UUID); cap `2` per chunk / `12` per dimension
  is checked BEFORE the candidate search and again via an in-tick reservation before `addFreshEntity`; seed =
  `worldSeed ^ chunkX*341873128712 ^ chunkZ*132897987541 ^ floor(gameTime/100)`; `new Random(seed).nextInt(100)`,
  roll `0..24` permits; mob kind = `successfulSpawns % 4` (zombie/skeleton/spider/creeper). Candidate: 8 packed X/Z
  offsets `0..15`, Y floor top-down in `targetFeetY±8`, feet = `floorY+1`; requires full solid floor
  (`isCollisionShapeFullBlock` + `isFaceSturdy(UP)`), air at feet/head, no fluid, full mob AABB `noCollision`,
  feet block light `<=4` (sky channel ignored), and `!ShelterProvider.isShelteredAt(...)`. Spawn uses the vanilla
  sequence `EntityType.create` → `snapTo` → `finalizeSpawn` → `checkSpawnRules`/`checkSpawnObstruction` → world border
  → `noCollision` → `addFreshEntity`; on success `successfulSpawns++` and the marker is attached; on failure the
  reservation is released and the counter is untouched. The marker is a Fabric persistent entity attachment
  `white_fog:dark_ambient` = `{origin, spawnChunk, spawnUuid}` (serialized by the attachment API's `Entity#save/load`
  inject — no custom mixin, no `getPersistentData()`); a per-dimension `DarkSpawnStore` (SavedData) persists only
  `{lastAttemptGameTime, successfulSpawns}` per chunk, while a pure `DarkMobState` keeps the runtime loaded index
  (restored from the attachment by `ServerEntityEvents.ENTITY_LOAD`/`UNLOAD`) and per-tick reservations. Event-only
  log `WHITEFOG_DARK_SPAWN ...` on each actual spawn; no per-tick spam. Natural spawning/loot/combat and the 1.3
  break protections are untouched; existing light stays unsafe (it removes the `light<=4` candidate, not the mobs).
- **Compact light/goal HUD (1.9).** One root HUD element (`white_fog:root_hud`, `WhiteFogHud`) is registered via Fabric
  `HudElementRegistry.addLast`; the old `WhiteFogHud` stub and `LightWorkPanelHud` are consolidated into it (the Work
  Panel is a child widget, not a second registration). The extended single `DarknessSnapshotPayload`
  (`revision, light, exposure, conditionMilli, shelter, sourceItemId?, sourceRemainingTicks, goalId, postCount,
  finalState, finalRemainingTicks, finalFuelTicks, waveRemainingTicks`; null source ⇒ `sourceRemainingTicks = -1`)
  is sent changed no more often than 5 ticks, plus a 100-tick heartbeat, plus immediately on join/respawn/dimension.
  The client `ClientDarknessState` accepts `revision >= last` (equal = heartbeat) via the pure
  `SnapshotRevisionGate` and clears revision+fields on disconnect. `DarkGoalService` holds five exact `white_fog:*`
  IDs, monotonic completed flags and active goal (first incomplete; marking a later goal early persists but does not
  skip ahead); flags live in `PlayerSurvivalState` (absent=false, `copyOnDeath`). **Display mode (2026-10-09 user
  request, newest pass):** the compact status panel (`LightWidget`+`ExposureWidget`, bottom-left) is constructed but
  **never renders** (`DarkHudLayout.shouldShowCompactStageHud()` false); the **Work Panel is permanently visible**
  (`DarkHudLayout.workPanelAlwaysVisible()` true) as the only bottom-left panel and now includes the thin `Тьма: E%`
  darkness/exposure bar (server `DarknessSnapshotPayload.exposure` via `ClientDarknessState.lightExposure()`, shared
  `DarknessBar`/`DarkHudLayout` geometry); the `Текущее задание` goal panel is **active in the top-right** via
  `DarkHudLayout.stageGoalVisible(...)` using localized `goal.white_fog.*` keys (never raw IDs), max 120 real px,
  ≤2 wrapped lines + ellipsis, hidden <320×180; warnings are **vanilla action-bar messages**, not a HUD window —
  `WarningWidget` is constructed but never rendered, while the pure `DarkWarningPolicy` (≥75
  `hud.white_fog.warning.find_light` / ≥90 `.drain`, 40-tick cooldown on reason change) calls
  `LocalPlayer.sendOverlayMessage(Component)`. The whole custom HUD stays hidden on F1 (`Hud.isHidden()`), any open
  `Screen`, spectator and death (no fabricated zeros before a snapshot); `G` remains only the **separate**
  `consumeClick()` + C2S `white_fog:light_refuel` action and does not control panel visibility.

## TODO / known gaps

- **Stage 1.9 live GUI acceptance pending (display mode changed 2026-10-09, newest pass):** the compact status
  (`LightWidget`+`ExposureWidget`) stays **hidden** (classes/data kept), the **permanently visible Work Panel** is
  the only bottom-left panel and now also shows a thin `Тьма: E%` darkness bar (server exposure, shared
  `DarknessBar`/`DarkHudLayout` geometry), the `Текущее задание` goal panel is back in the top-right
  (`DarkHudLayout.stageGoalVisible`, `GoalWidget`), and warnings are **action-bar messages** driven by
  `DarkWarningPolicy` (≥75 `Найди свет` / ≥90 `Тьма истощает тебя`, 40-tick cooldown on reason change), never a HUD
  rectangle. Live acceptance that matters: the Work Panel really renders all seven rows (title/bar/source/fuel/
  offhand/clock/hint) in normal gameplay without holding `G`, that `G` still triggers refuel separately, that the
  goal appears top-right and wraps to ≤2 ellipsized lines with the 120 real-px cap, that the action-bar warning
  appears once per reason change and not every frame, that everything hides on F1 / any open `Screen` / death /
  spectator and never draws over a menu, with real Cyrillic text widths, pose reset and no overlap with the vanilla
  HUD / hotbar at GUI scales 1..4 on 1280×720 / 1920×1080 / narrow 320×180 at 20/60/144 FPS. All of it has
  automated evidence only; the sandbox and the `WHITEFOG_HUD_SELFTEST` marker are **logic-only** and do NOT prove
  pixels, action-bar rendering or runtime layout. There are no world providers yet for
  `postCount`/`finalState`/`finalRemainingTicks`/`finalFuelTicks`/`waveRemainingTicks` (DTO defaults =
  0 / LOCKED / 72000 / 0 / 0); goal flags are set only through `PlayerSurvivalState.markGoal(String)`.

- **Stage 1.8 live acceptance pending:** needs an in-game world check that a placed torch really removes the new
  extra spawns (the candidate check rejects block light>=5), that a house with an open entrance gives no automatic
  immunity (only enclosure does, via the shelter check), that the marker survives save/reload, that death loot is
  ordinary vanilla loot once, and that Peaceful produces no extra mobs. The `Block-ticking` iteration and the
  shelter check per candidate make this the most CPU-sensitive pass; only automated evidence exists today.
  The candidate search only checks the mob AABB / two air cells / block light / one read-only shelter call — it is
  not a statistical spawn-rate model, and a `successfulSpawns % 4` type rotation is per chunk.

- **Stage 1.7 live acceptance pending:** cave room, built house, stationary player/other player's door change,
  chunk boundary and two players still require in-game testing. One node per voxel cannot represent two separate
  air regions in a door voxel; reachable door cells count toward volume/bbox and need their own floor/roof.
  Ordinary partial shapes leak deliberately. Feet fit uses a centered 0.6-wide box in the feet cell and empty
  collision context, not the player's precise offset/pose. Custom shapes reading outside their one-cell halo
  fail closed; neighbor reads are dependencies. Dev unloaded diagnostics use DEBUG; read errors use WARN.

- **First-person torch appearance (accepted limitation).** The offhand torch may look absent/black in first person
  even though its dynamic light works; the user confirmed this in-game and it is treated as non-critical (post-1.6
  bugfix pass). The earlier report (charged offhand torch "not visible in hand", `G` says «Источник: нет рядом») was
  re-audited in the post-1.6 passes and the real root causes were found with `javap` and fixed: the `instanceof ClientLevel`
  gate rejected `RenderSectionRegion` for block-mesh light, and the hand-light path
  (`EntityRenderer.getPackedLightCoords` → `getBlockLightLevel`) plus the `ItemInHandRenderer.submitHandsWithItems`
  packed light arg now raise the offhand contribution. A `STREAM_CODEC` round-trip self-test proves the
  `(remaining,lit)` component survives the network codec (so `streamRoundTrip=true` in the server smoke). Runtime
  diagnostics `WHITEFOG_PORTABLE_LIGHT_STATE` / `WHITEFOG_FP_HAND_LIGHT` are available if the visual issue is
  revisited. No custom torch item model exists; vanilla `minecraft:item/torch` renders 26 quads.
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
tests\eternal_darkness\shelter\run_shelter_selftest.bat   :: production pure logic, 228 assertions
tests\eternal_darkness\shelter\run_api_evidence.bat       :: javap + jar hashes, UTF-8 evidence
tests\eternal_darkness\spawn\run_dark_spawn_selftest.bat  :: stage 1.8 production pure policy/state, 103 assertions
tests\eternal_darkness\hud\run_hud_selftest.bat           :: stage 1.9 production pure goal/revision/tie/layout + offhand source + darkness bar + warning policy + single-root/visibility policy, 596 assertions
tests\darkness_visual\run_darkness_visual_selftest.bat
tests\darkness_visual\run_client_visual_probe.bat
tests\darkness_light_fix\run_darkness_light_fix_selftest.bat   :: post-1.6 fix logic (blend stability, placement recording, button padding)
tests\portable_light_dynamic\run_portable_light_dynamic_selftest.bat  :: post-1.6 root cause (RenderSectionRegion gate, falloff, entity light, section radius)
```
Expected sandbox sizes: break_timer 118, station 270, item_gui_center 14, exposure 89, light 69,
portable_light 469, darkness_visual 67, darkness_light_fix 67, portable_light_dynamic 81, shelter 228,
dark_spawn 103, hud 596.
Sandboxes are **logic-only and NOT runtime proof** — say so in reports.

Key smoke evidence strings (grep fresh logs): `WHITEFOG_CRAFTING_SELFTEST ... status=SUCCESS`,
`block-break rules registered (stage 1.3 ...)`, `darkness light-exposure service registered (stage 1.5 ...)`,
`eternal night enabled (advance_time=false, day_time=18000)`, `WHITEFOG_LIGHT_SELFTEST ... status=SUCCESS`,
`WHITEFOG_LIGHT_FUEL_CODEC_SELFTEST ... streamRoundTrip=true ... status=SUCCESS`,
`WHITEFOG_LIGHT_ROUNDTRIP_SELFTEST assertions=98 ... status=SUCCESS`,
`WHITEFOG_SHELTER_SELFTEST assertions=<n> handlers=true elapsed_ms=<n> status=SUCCESS`,
on the client `WHITEFOG_HUD_SELFTEST widgets=5 registered=true status=SUCCESS`,
`dark-ambient spawn service registered (stage 1.8 ...)` (and, on a real spawn, event-only
`WHITEFOG_DARK_SPAWN origin=DARK_AMBIENT ...`), and on the client
`MultiPlayerGameModeMixin applied=true`, `WHITEFOG_PORTABLE_LIGHT_SELFTEST handlers=true` (checks all three
hooks: `LightCoordsUtil.BrightnessGetter`, `EntityRenderer`, `ItemInHandRenderer`), `WHITEFOG_DARKNESS_VISUAL_SELFTEST handlers=true`.
The smoke scripts now also gate on the server/client `WHITEFOG_SHELTER_SELFTEST ... status=SUCCESS` marker and the
client `WHITEFOG_HUD_SELFTEST ... status=SUCCESS` marker (an observed `status=FAILURE` fails the smoke), stamp runs
with millisecond+GUID suffixes, verify the owned PID's command line before `taskkill /T /F`, and write `elapsed_ms=`
into the `.result`.
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
- **License.** Proprietary **All Rights Reserved** (© 2026 belzoralolbtw) — no copying, forks or redistribution
  without prior written permission; earlier CC0-published versions keep their original terms (see `LICENSE`).
- **Git.** Keep this `AGENTS.md` in the repo. Never commit build artifacts/logs/secrets. Never push without explicit
  user approval.

## Decisions / 26.2 traps

- **Shelter (1.7):** fixed FIFO DOWN/UP/NORTH/SOUTH/WEST/EAST, inclusive 125 cells and bbox 9x5x9,
  minimum 18 plus clear height-two column. Full collision volumes block; closed DoorBlock/TrapDoorBlock masks
  come from actual collision union boundary coverage (no names); OPEN is permeable. Floor/roof checked per
  occupied XZ endpoint. Provider uses only `ServerChunkCache.getChunkNow`, never a loading Level for shapes.
  `LevelChunk.getBlockEntity(..., CHECK)` still promotes pending NBT in 26.2; use `getBlockEntities().get(pos)`
  to remain read-only. UUID cache TTL is `<20`, rechecks all read dependencies on hits, invalidates bbox+1 and
  shape dependencies; join/disconnect/respawn/dimension/chunk/level unload/server stop clear runtime entries.
  The existing exposure sample calls the provider before the unchanged policy; no new tick/payload/state.

- **Dark-ambient spawning (1.8):** one extra pass inside the single `END_SERVER_TICK` gated by
  `tickCount % 100 == 0`; per-level `SavedData` `white_fog:dark_spawn` stores only the per-chunk attempt state, so
  restart/offline gaps create no backlog. `Level.getLevelData().getGameTime()/100` is the bucket; the seed/RNG and
  the 8 candidate offsets are deterministic. The marker is a Fabric **entity** attachment (`persistent(Codec)`),
  chosen over a custom mixin because the API persists on any `Entity` via its `Entity#save/load` inject (verified
  by `javap`); runtime index is rebuilt from `ServerEntityEvents.ENTITY_LOAD`/`ENTITY_UNLOAD` (which in 26.2 hook
  `ServerLevel.EntityCallbacks.onTrackingStart/onTrackingEnd`). Candidate Y loop scans **floor** Y top-down and the
  mob is placed at `floorY+1`; block light (not sky) is the gate; the shelter detector is called read-only last.
  Caps are counted from the marker index + in-tick reservations only (never per player). Peaceful is skipped
  wholesale. Existing world/natural spawns, loot and combat are untouched.

- **Compact HUD / goals (1.9):** one `HudElementRegistry.addLast` root (`white_fog:root_hud`); every panel is a child
  widget with explicit `updateAnimations(dt)`/`renderBackground`/`renderIcons`/`renderText` phases and a guaranteed
  `pose().pushMatrix()/popMatrix()`. `DarknessSnapshotPayload` uses a hand-written `StreamCodec.of` because
  `StreamCodec.composite` caps at 12 components and the payload has 13 fields plus two nullable strings (source id
  null ⇒ `sourceRemainingTicks=-1`). Snapshot policy: throttle `>=5` ticks, heartbeat `100` ticks, immediate on
  join/respawn/dimension; the client `SnapshotRevisionGate` accepts `revision >= last` (equal = heartbeat) and resets
  on disconnect. Goal flags are monotonic (`DarkGoalService.markCompleted`, unknown ID rejected), active goal = first
  incomplete, and persisting in `PlayerSurvivalState` with per-goal boolean NBT keys (`goal_completed_<short>`,
  absent=false). The server source field is the existing `LightSourceService.nearest(eye,8)` mapped to a vanilla item
  id (never a guessed block id); `LightSourceService.isTieBetter` delegates to the pure `LightTieBreak`.
  Client-only checks verified by `javap`: `Minecraft.gui.hud.isHidden()` is the F1 flag (`Gui.handleKeybinds` →
  `Options.keyToggleGui` → `Hud.toggle()`), `Minecraft.gui.screen()` is the open screen, and chat is
  `Minecraft.gui.hud.getChat().isChatFocused()`. HUD text is localized (`hud.white_fog.*`, `goal.white_fog.*`); raw
  goal IDs are never shown. `postCount`/`finalState`/`finalRemainingTicks`/`finalFuelTicks`/`waveRemainingTicks` are
  persisted DTO defaults (0 / LOCKED / 72000 / 0 / 0) until future world providers set them; no fake structures.

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
- **First-person hand light (post-1.6, javap 26.2).** `GameRenderer#renderItemInHand` feeds exactly one packed light
  into `ItemInHandRenderer#submitHandsWithItems(float,PoseStack,SubmitNodeCollector,LocalPlayer,int)`. Our
  `ItemInHandRendererMixin` `@ModifyVariable(index=5, argsOnly=true)` raises it to `max(vanilla block, portable)`
  via `PortableLightClient.raisePackedLight`. Idempotent with the `EntityRenderer` hook; unlit/empty/corrupt
  unchanged; no stack/model swap, no fullbright.
- **HUD source display split from action (post-1.6).** `LightPanelFormat.hudSourceLine/hudFuelLine` are pure; the HUD
  shows a valid burning offhand as «Источник: в руке — …» when no block is nearby, but `handleLightPanelKey`
  still sends a C2S refuel only for a placed block.
- **Visual fade-in (post-1.6).** Only `DarknessVisualConfig.FADE_IN_PER_SECOND` changed 2.0 → 0.4/s; server
  thresholds/sample interval and `FADE_OUT_PER_SECOND` unchanged.
- **References (adapted, not copied):** Fabric API (`fabric-events-interaction-v0`, `fabric-menu-api-v1`,
  `fabric-networking-api-v1`, `fabric-lifecycle-events-v1`, `fabric-key-mapping-api-v1`, `fabric-rendering-v1`);
  `Patbox/polymer` (mining), `gnembon/fabric-carpet` (menu click), `Tschipp/CarryOn` (station removal), `Wynntils`/
  `cheatutils` (prediction), `LambdAurora/LambDynamicLights` 26.2 (light: `BrightnessGetterMixin`,
  `EntityRendererMixin`, `SimpleChunkRebuildScheduler`), `Sjouwer/gamma-utils` (fog), `Glitchfiend/SereneSeasons`
  (dark-spawn ticking-chunk iteration), `VazkiiMods/Botania` (`GaiaGuardianEntity` spawn sequence),
  `MinecraftForge` `ForgeEventFactory#checkSpawnPosition` (spawn-position validation), `AlexModGuy/AlexsMobs` /
  `bonsaistudi0s/Creeper-Overhaul` (dark-enough pattern). Check real jars.
