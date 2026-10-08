# White Fog (`white_fog`)

A Minecraft **26.2** **Fabric** mod that builds a survival foundation on a
**server-authoritative player state** and replaces several vanilla interactions
(crafting, block breaking) with explicit, server-verified rules.

## Status / roadmap

- **Upper-level roadmap stages 1.1–1.6 are complete** and were manually verified in-game by the user across the
  main mechanics.
- All later work (fading light sources, the Darkness pulse fix, dynamic light, HUD, the source menu and UI) shipped
  as **post-1.6 bugfix/stabilization, not as separate roadmap stages 1.7/1.8/1.9/1.10/1.11**.
- **Roadmap stage 1.7 «Закрытое укрытие и адаптер света» (shelter detector) is NOT started** — see
  `ROADMAP_STEPS.md`.

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

## Implemented (roadmap stages 1.1–1.6 + post-1.6 bugfixes)

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

## Not yet implemented

- **Roadmap stage 1.7 «Закрытое укрытие и адаптер света» (shelter detector)** is
  **not started**: there is no real `darkness/shelter/` detector and
  `LightExposureService.isSheltered(...)` still returns `false`, so the "open sky
  adds +1 exposure inside a roofed room" rule currently depends on `canSeeSky`
  alone. This is the single remaining ticket in `ROADMAP_STEPS.md`.
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
- `scripts\server_smoke.bat` / `scripts\client_smoke.bat` — bounded smoke runs
  (dedicated server / client) that terminate only their own process tree.

## License

This project is released under **CC0-1.0** — see [LICENSE](LICENSE).
