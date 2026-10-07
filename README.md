# White Fog (`white_fog`)

A Minecraft **26.2** **Fabric** mod that builds a survival foundation on a
**server-authoritative player state** and replaces several vanilla interactions
(crafting, block breaking) with explicit, server-verified rules.

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

## Implemented stages

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

## Not yet implemented

- The mod pickaxes `white_fog:bronze_pickaxe` / `white_fog:iron_pickaxe` /
  `white_fog:steel_pickaxe` are **not registered yet** (planned for stage 6.4).
  Until then mining hard blocks and hard stations with a mod pickaxe is
  unavailable; vanilla tools are refused for those categories.

## Tests

- `tests\break_timer\run_break_timer_selftest.bat` — logic-only sandbox for the
  break-timer lifecycle (not a runtime proof).
- `scripts\server_smoke.bat` / `scripts\client_smoke.bat` — bounded smoke runs
  (dedicated server / client) that terminate only their own process tree.

## License

This project is released under **CC0-1.0** — see [LICENSE](LICENSE).
