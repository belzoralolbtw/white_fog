# Sandbox: portable dynamic light root cause (ticket 1.9)

Logic-only sandbox proving the root cause of "charged offhand torch gives no dynamic light /
the hand item looks invisible" and the replacement policy. It does **not** touch `build.gradle`
or `src/`; it compiles and runs only the files under `src/` here.

Run:

```bat
tests\portable_light_dynamic\run_portable_light_dynamic_selftest.bat
```

Expected: `passed=74 failed=0`, `SELFTEST status=SUCCESS` (log `logs\portable_light_dynamic_selftest_<stamp>.txt`,
result file with `status=SUCCESS`; internal watchdog → exit 124 = `TIMEOUT`).

What it checks (pure model, mirrors `PortableLightPolicy`):

- **root cause**: the old `level instanceof ClientLevel` gate returned 0 for a
  `RenderSectionRegion` (the getter passed during mesh baking); the new logic returns the real level.
- falloff: 1 level per block, clamp 0..15 (torch 14, soul torch 10).
- entity light: `max(vanilla, dynamic)` — the hand is lit only when the offhand is burning; an
  empty/unlit/corrupt stack keeps vanilla light and is **not** fullbright.
- section-rebuild radius: `ceil(emission / 16)` (1 section for emission ≤ 15, 0 for emission 0).

**Not runtime proof.** Real `ItemStack`, world, mesh and renderer are not exercised here.
