# Stage 1.9 sandbox — compact light/goal HUD (pure logic)

Logic-only acceptance sandbox for the pure production classes
`com.whitefog.darkness.goal.DarkGoalService`, `com.whitefog.darkness.SnapshotRevisionGate`,
`com.whitefog.darkness.light.LightTieBreak`, `com.whitefog.darkness.light.PortableLightPolicy`,
`com.whitefog.darkness.light.LightConfig`, the client pure
`com.whitefog.client.hud.DarkHudLayout`, `com.whitefog.client.hud.HudSourceDisplay`,
`com.whitefog.client.hud.DarkWarningPolicy` and `com.whitefog.client.hud.DarknessBar`. It compiles
those production sources read-only (no duplicated formulas) and runs
`com.whitefog.tests.hud.SelfTest`.

Run:

```bat
tests\eternal_darkness\hud\run_hud_selftest.bat
```

The runner is a foreground watchdog with an internal budget; it writes a UTF-8 log and a
`.result` file under `logs\` (`hud_selftest_<stamp>.{txt,result}`). `exit 124` means
`status=TIMEOUT`. Probe the bounded failure paths with
`runner.ps1 -Mode selftest -Probe timeout` / `-Probe failure`.

Covered: snapshot revision order 3,2,3,4 (equal revision accepted as heartbeat, lower rejected);
reconnect-after-clear accepts a new revision 1; nearest tie-break by x/y/z; fuel
`ceil(ticks/20)` 1/19/20/21 → 1/1/1/2; goal ordering + idempotency (a later goal may be pre-marked
but the active goal stays the first incomplete); allowed/unknown goal IDs and monotonic flags;
NBT key derivation; panel geometry (`HEIGHT/PAD/ICON/TEXT_GAP/BAR_HEIGHT/ROW_GAP/MAX_TEXT_WIDTH`,
scale 0.5, margin 4, goal cap 120 real px); goal wrap to two lines with ellipsis; narrow-screen
hiding; fixed zones with no goal/status overlap; warning centering; `clampDt` 0..0.05; `approach`
easing; palette/alpha helpers.

Regression coverage for the two reported HUD bugs and the unified style:

- **Display mode (user request).** The compact stage-1.9 status (`Свет/Тьма/Укрытие/Источник`) is
  disabled via the pure policy `DarkHudLayout.shouldShowCompactStageHud()` /
  `compactStageHudVisible(...)` — production widgets stay constructed but never render. The `G` Work
  Panel is shown permanently in normal gameplay through `DarkHudLayout.workPanelAlwaysVisible()` /
  `workPanelVisible(gameHidden)`, and its visibility has no key argument (it cannot depend on the `G`
  key). The `G` key itself stays a separate quick action: a source scan proves `WhiteFogHud.java` does
  not reference `lightPanelKey`, the single `HudElementRegistry.addLast` root remains, and
  `WhiteFogClient.java` still uses `consumeClick()` + `LightRefuelPayload`. The gameplay hide
  conditions (no player / death / spectator / F1 / open `Screen`) remain in the root.
- **Darkness bar inside the Work Panel (user request).** `DarknessBar` normalizes/clamps the server
  `exposure` 0..100 to 0..1 (`DarkHudLayout.exposureNormalized`) and eases it with the shared
  `approach`/frame-dt; geometry is `darknessBarRow() == 1`, `darknessBarLogicalY()`,
  `darknessBarTrackWidth()` with `WORK_PANEL_ROWS == 7`. The bar stays inside its row, inside the
  panel and above the hotbar zone at 180/720/1080.
- **Goal back in the top-right (user request).** `DarkHudLayout.stageGoalVisible(gameHidden, hasData,
  width, height)` shows the `GoalWidget` only with a gameplay player + server snapshot and at
  ≥320x180; the panel is right-aligned with a 4 px margin and the goal/work-panel zones do not overlap.
- **Warnings as action-bar messages (user request).** `DarkWarningPolicy.reasonFor` maps
  `0/74 -> NONE`, `75/89 -> FIND_LIGHT`, `90/100 -> DRAIN`; a reason change starts a 40-tick cooldown,
  after which the reason is emitted exactly once (no spam, never `NONE`, frame-rate independent). A
  source scan proves the root no longer renders/updates `WarningWidget` (no warning rectangle,
  `warningX`/`warningY` unused) and instead calls `warningPolicy.update` + `LocalPlayer.sendOverlayMessage`.
- **Offhand source (`HudSourceDisplay` + production `PortableLightPolicy`).** A valid burning
  offhand torch / soul torch with no nearby placed source resolves to `Origin.OFFHAND`, keeps the
  kind ordinal and exposes the fuel as `ceil(remaining/20)` (12000→600s, 19→1s, 20→1s, 21→2s);
  empty/unlit/corrupt (`count>1`)/absent offhand does not claim a source; a nearby placed source
  wins deterministically; `lantern`/`campfire` are not portable lights.
- **Goal boundary contract.** The old `PAD + ICON + TEXT_GAP` text X is proven to overrun the
  panel logical width; the fixed `goalTextX() == PAD` plus `goalPanelLogicalWidth()` keep
  `textX + width <= panel` for max-width and long localized (Cyrillic) strings at 320x180,
  1280x720 and 1920x1080, with the panel capped at 120 real px and respecting the 4 px margin.
  Long goals wrap to at most two ellipsized lines obeying the available width.
- **Shared style.** `WORK_PANEL_ROWS == 7`, `workPanelX()/workPanelTextMaxLogical()/workPanelTop()/
  workPanelHeight()/workPanelWidth()` reuse the status geometry, and
  `statusVisibleWithWorkPanel(true) == false` documents that the compact status hides while the
  `G` work panel is visible (no overlap). One palette (`COLOR_BACKGROUND/ACCENT/DANGER/TEXT`) and
  one `PAD/ROW_GAP/HEIGHT/SCALE` convention are shared by status/goal/warning/work panel.
- **Localization.** `ru_ru`/`en_us` both contain `hud.white_fog.source.held`,
  `hud.white_fog.source.kind.torch`, `hud.white_fog.source.kind.soul_torch`,
  `hud.white_fog.source.none`, `hud.white_fog.fuel`, `hud.white_fog.dark`,
  `hud.white_fog.warning.find_light`, `hud.white_fog.warning.drain` and all five `goal.white_fog.*`
  keys; `ru_ru` is Cyrillic and neither file exposes raw `minecraft:` item ids.

This sandbox is **logic-only and NOT pixel/runtime proof**. Live GUI acceptance (real Cyrillic
widths, pose reset, no overlap in-game, chat/F1/menu/dead/spectator, multi-scale 1..4 and
1280x720 / 1920x1080 / 320x180 across 20/60/144 FPS) is still pending.
