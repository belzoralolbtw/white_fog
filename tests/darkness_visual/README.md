# Sandbox: визуальный адаптер модовой тьмы (этап 1.5 — визуальный hotfix)

Независимый foreground-self-test **чистой логики** клиентского визуального адаптера модовой тьмы.
НЕ входит в основной `build.gradle` и НЕ трогает `src/`. Логика **уже интегрирована** в client
source set (`com.whitefog.client.darkness.DarknessVisualGate`/`DarknessVisualConfig` + миксины
`LightmapRenderStateExtractorMixin`/`DarknessFogEnvironmentMixin`); этот sandbox остаётся
эталоном значений и проверяет bounded-огибающую и гейты без Minecraft.

## Запуск

```bat
tests\darkness_visual\run_darkness_visual_selftest.bat
:: exit 0 только при status=SUCCESS; TIMEOUT -> 124; FAILURE -> 1
```

Пишет UTF-8 лог `logs\darkness_visual_selftest_<stamp>.txt` и machine-readable
`logs\darkness_visual_selftest_<stamp>.result` (`status=SUCCESS|FAILURE|TIMEOUT`).

Bounded runtime probe (свой PID-три, внутренний timeout 180 c): доказывает **инициализацию
клиента и применение обоих миксинов** тьмы (`WHITEFOG_DARKNESS_VISUAL_SELFTEST ... status=SUCCESS`),
НЕ пиксели:

```bat
tests\darkness_visual\run_client_visual_probe.bat
:: exit 0 только при status=SUCCESS
```

## Что моделируется

Клиентский адаптер (следующая фаза, только client source set), который отличает **модовую** тьму
по последнему серверному snapshot `lightExposure >= 50` и убирает пульсацию + near-black, не меняя
серверную механику:

- **Огибающая** (`VisualEnvelope`, `MildnessModel`): frame-delta, ограниченный шаг `rate*dt`,
  кламп `[0,1]`, монотонность при постоянной цели, отсутствие перехлёста.
- **Гейты**: только живой survival/adventure, snapshot свежий (≤ 7 c, heartbeat сервера 5 c),
  `exposure >= 50`; disconnect/creative/spectator/смерть → спад.
- **Gate tail** (`ownedTail`, `MildnessModel`): после ухода exposure ниже порога уже наложенный
  Darkness живёт до 40 тиков (~2 c), поэтому `active()` ещё `OWNED_TAIL_SECONDS = 5 c` держится,
  пока у игрока есть эффект и модовая активность была недавно — ванильная пульсация не
  возвращается на остаток эффекта. Чужой Darkness вне окна (нет снимка/после disconnect) не
  трогается: `ownedTail=false`, `active=false`.
- **Активность** (`MildnessModel#active`): подавление включается СРАЗУ при `targetActive`
  (даже при огибающей 0 — иначе fade-in оставил бы чёрные импульсы косинуса).
- **Выходы** (`DarknessVisualPolicy`): пол `brightness` (≤ 0.35), постоянное вычитание lightmap
  (≤ 0.16 против vanilla-пика 0.45), дистанция тумана (≥ 48 блоков), cap затемнения тумана (≤ 0.55)
  с сохранением `voidFactor`; `e = 0` даёт ровно vanilla no-op.

## Что покрывает (проверки)

- fade-in/fade-out: монотонность, отсутствие отрицательных/положительных ложных шагов, `[0,1]`;
  скорость fade-in — `FADE_IN_PER_SECOND = 0.4/s` (замедлена по тикету: полная сила ≈ 2.5 c вместо
  0.5 c; fade-out не менялся), поэтому ожидания в тестах считаются из константы, а не хардкодятся;
- частотная независимость: 20 cps vs 60 fps за одно и то же реальное время (середина перехода =
  `rate * 0.25 c`); отсутствие скачка больше `rate*dt`;
- bounded cap: огибающая и все выходы упираются в умеренные пределы, offset заметно ниже vanilla 0.45;
- устаревание snapshot: активно при свежем, спадает после окна; disconnect; creative/spectator/смерть;
- порог exposure: 49 — неактивно, 50 — активно;
- **gate tail**: immediate `active()` при пороге; удержание активности после ухода exposure при
  живом своём эффекте; истечение окна и плавный спад; чужой Darkness (нет снимка/после disconnect)
  не активирует адаптер;
- **нет пульсации**: наше слагаемое имеет нулевой разброс (max−min=0), vanilla-эталон
  (`calculateDarknessScale`, период 80 тиков) — положительный разброс > 0.1;
- выходы: монотонны по `e`, непрерывны, в границах; `e=0`/`blend=0` — no-op;
- детерминизм: одна и та же последовательность кадров даёт тот же результат.

## Что НЕ доказывает

Это **logic-only**. Не проверяются: миксины `LightmapRenderStateExtractor.extract` /
`DarknessFogEnvironment.setupFog` / `getModifiedDarkness`, реальные `LightmapRenderState`/`FogData`,
шейдер `lightmap.fsh`, light engine, сеть. Пиксельная видимость (`light 0` остаётся тёмным без
ambient-света — честное ограничение) за архитектором.

Runtime-доказательство — `gradlew.bat build --no-daemon --console=plain`,
`scripts\client_smoke.bat` и `tests\darkness_visual\run_client_visual_probe.bat`
(`WHITEFOG_DARKNESS_VISUAL_SELFTEST ... status=SUCCESS` = миксины применены). Пиксельная видимость
(различимость окружения, отсутствие моргания) — только интерактивная приёмка архитектора: probe
и sandbox её НЕ заменяют.

## Байткод-evidence (26.2)

Сохранён в `logs\`:
- `_evidence_LightmapRenderStateExtractor.txt` — `extract`/`calculateDarknessScale`, пульсация косинусом;
- `_evidence_MobEffectInstance_BlendState.txt` — `getFactor` (lerp, без пульсации — пульс в lightmap);
- `_evidence_DarknessFogEnvironment.txt` — `setupFog`/`getModifiedDarkness`;
- `_evidence_FogRenderer_full.txt` — `computeFogColor` (`modifiesDarkness`, `getModifiedDarkness`,
  `square(1 - factor)`);
- `_evidence_Lightmap.txt` — UBO `LightmapInfo` (`DarknessScale`, `BrightnessFactor`);
- `_evidence_shaders/lightmap.fsh` — `color - DarknessScale` + `mix(color, notGamma(color), BrightnessFactor)`.

## Reference (adapted, not copied)

- `Sjouwer/gamma-utils` (Fabric, branch `26.3-Fabric`), файл
  `src/main/java/io/github/sjouwer/gammautils/mixin/MixinLightmapRenderStateExtractor.java` —
  подтверждает точку инъекции в `LightmapRenderStateExtractor.extract` (там — `Math.max`/`hasEffect`).
  URL: https://github.com/Sjouwer/gamma-utils/blob/26.3-Fabric/src/main/java/io/github/sjouwer/gammautils/mixin/MixinLightmapRenderStateExtractor.java
- `yingfing/ClearEffects` (Fabric 1.21.11), `src/main/java/com/example/cleareffects/mixin/GameRendererMixin.java` —
  идея отключения визуальных эффектов Darkness/Blindness (в 26.2 путь другой: не `getShader`, а lightmap/fog).
  URL: https://github.com/yingfing/ClearEffects/blob/main/src/main/java/com/example/cleareffects/mixin/GameRendererMixin.java
