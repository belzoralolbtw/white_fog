# tests/eternal_darkness/exposure — sandbox этапа 1.5 («ВЕЧНАЯ НОЧЬ И ВОЗДЕЙСТВИЕ ТЬМЫ»)

Это **независимый sandbox** для доказательства логики вечной ночи и воздействия тьмы до/после
применения в `src/`. Не входит в основной Gradle build и не трогает `src/`.

## Что это НЕ доказывает

Это **чистая логика без Minecraft**. Тест НЕ доказывает runtime-поведение игры (world clock,
`GameRules.ADVANCE_TIME`, эффект Darkness, attribute modifier, `Level#getBrightness/canSeeSky`,
сеть, сохранение NBT). Для этого нужна сборка мода и запуск клиента/сервера архитектором.

## Что проверяется (89 проверок, 26 сценариев)

1. свет 4/5/8/9 — дельты exposure (−/0/+);
2. `canSeeSky` true/false и укрытие (`shelter` гасит бонус открытого неба);
3. `exempt` (creative/spectator) и мёртвый игрок не получают штрафов;
4. `victorySafe`: delta −2 и светлый отдых;
5. cap exposure 0/100;
6. два одинаковых DTO дают одинаковый результат (детерминизм);
7. Oracle: 0 за 600 тиков открытой тьмы → exposure 60; под крышей → 30;
8. Oracle: 100 в ярком свете: 500т → 50, 580т → 42, 600т → 0 (полный сброс);
9. light 5..8: safe timer сбрасывается, exposure не меняется;
10. Condition: порог истощения 90, восстановление в ярком, нет в промежуточном;
11. Oracle: 2000 тёмных damage samples снимают ровно 100 п.п. Condition, на 2000-м — deathRequired;
12. Condition cap 100000 при восстановлении;
13. пороги 49/50 (Darkness), 74/75 (скорость), 89/90 (урон), 99/100 (cap);
14. повтор server tick с тем же номером не начисляет второй sample;
15. sample chunks 19+1 → ровно один sample;
16. sample chunks 10+10 → ровно один sample;
17. serialization round-trip полей тьмы;
18. save на remainder 19 → load сохраняет и следующий тик даёт sample;
19. миграция: `condition 63.0 → darkness_condition_milli 63000` (и 0 → 0);
20. disconnect без offline catch-up;
21. отсутствие накопления modifier'ов (`white_fog:darkness_slow` ровно один);
22. creative/spectator приостанавливает шкалы, возврат в survival восстанавливает гейт;
23. незагруженная клетка глаза — sample пропускается без догоняющего расчёта;
24. Nether/End: block light без skylight-гейта (+1, а не +2);
25. два игрока имеют разные exposure;
26. синхронизация: heartbeat 100 и min 5 тиков, немедленный снимок на join.

## Утверждённые значения (синхронизированы с `src`)

порог яркого света `9`, промежуточный `5..8`, тёмный `0..4`; дельты `−2 / 0 / +1 (+1 открытое небо)`;
`SAMPLE_INTERVAL_TICKS = 20`; `SAFE_TICKS_MAX = 600`; `CONDITION_MILLI_MAX = 100000`,
урон/восстановление `50`/sample, порог урона `exposure>=90`; эффект Darkness при `>=50`, штраф
скорости при `>=75` (`multiplier 0.85`); heartbeat `100`, min интервал `5`; вечная ночь `18000`
(`24000` = сутки).

## Запуск (foreground, само-завершение)

Из корня проекта:

```
tests\eternal_darkness\exposure\run_eternal_darkness_selftest.bat
```

`.bat` компилирует только `tests/eternal_darkness/exposure/src` во временный
`tests/eternal_darkness/exposure/build` (в `.gitignore` через `build/`), запускает `SelfTest`
в foreground и пишет UTF-8 лог в `logs\eternal_darkness_selftest_<stamp>.txt` +
`status=SUCCESS|TIMEOUT|FAILURE` в `logs\eternal_darkness_selftest_<stamp>.result`.
Не затрагивает `build.gradle`, `src/`, ресурсы. Внутренний watchdog завершает JVM через 20 с
(exit 124 = TIMEOUT).
