# Stage 1.8 sandbox — dark-ambient spawning (pure logic)

Logic-only acceptance sandbox for the pure classes
`com.whitefog.darkness.spawn.DarkSpawnPolicy` and `DarkMobState`. It compiles those production
sources read-only (no duplicated formulas) and runs `com.whitefog.tests.spawn.SelfTest`.

Run:

```bat
tests\eternal_darkness\spawn\run_dark_spawn_selftest.bat
```

The runner is a foreground watchdog with an internal budget; it writes a UTF-8 log and a
`.result` file under `logs\` (`dark_spawn_selftest_<stamp>.{txt,result}`). `exit 124` means
`status=TIMEOUT`. Probe the bounded failure paths with
`runner.ps1 -Mode selftest -Probe timeout` / `-Probe failure`.

Covered: 25-of-100 roll permits, exposure 74/75, distance 23.99/24/32/32.01, block light 4/5
(sky channel deliberately ignored), chunk cap 1/2 and dimension cap 11/12, all-8-invalid → no
spawn, first valid candidate, spider wide AABB vs a one-block corridor, `successfulSpawns % 4`
rotation, nearest/tie-break target selection, two players do not multiply the cap, duplicate-UUID
idempotency, unload cleanup / load restore, reservation reset per tick, deterministic seed/bucket
candidates/type, and persistent attempt round-trip.

This sandbox is **logic-only and NOT runtime/live-spawn proof**. Live acceptance (torch removes
spawns, open-door house has no immunity, marker survives save/load, vanilla loot, Peaceful) is
still pending.
