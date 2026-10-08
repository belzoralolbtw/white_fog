# Sandbox: гаснущие источники света (этап 1.6)

Независимый foreground-self-test **чистой логики** механики источников света.
НЕ входит в основной `build.gradle`, НЕ трогает `src/`.

## Запуск

```bat
tests\eternal_darkness\light\run_light_selftest.bat
:: exit 0 только при status=SUCCESS; TIMEOUT -> 124; FAILURE -> 1
```

Пишет UTF-8 лог `logs\light_selftest_<stamp>.txt` и machine-readable
`logs\light_selftest_<stamp>.result` (`status=SUCCESS|FAILURE|TIMEOUT`).

## Что покрывает

Ёмкости/бонусы (`12000/8000/24000/16000/16000`), нормализация компонента (0/1/max/негатив/null),
переполнение (torch остаток 80 + coal 12000 → отказ без списания), near-empty (torch 1 → 0 за job → 12000),
костёр два бревна = 16000, палочка 2000, уголь 16000, пауза unlit, защита от повторного серверного тика,
countdown lantern 24000→23900 за 100 тиков, unload без догоняющего расхода, ItemEntity не тикается,
1→0 немедленно гасит, замена источника не наследует остаток (UUID), charged `count>1` отклоняется
(split/merge), `managedByPost` запрещает refuel (inspect доступен), генерационные бонусы, флаг
инициализированного чанка. Дополнительно (hotfix nearest/unlit discoverability): `LightModel.nearest`
(аналог серверного, **lit НЕ фильтруется**) и сценарии — пустой/погасший источник (`remaining=0`)
обнаружим, unlit с запасом обнаружим, сохранены правила loaded/block id/дистанция/LOS/tie-break,
после обнаружения работают обычный refuel (`remaining=0` + топливо) и lightOnly («Зажечь»,
`remaining>0` + пустая рука).

## Что НЕ доказывает

Это **logic-only**. Не проверяются: реальные `BlockState`/`BlockEntity`/`SavedData`, применение миксинов
(свойство `white_fog_lit`, `getLightEmission`, частицы, `popResource`), item-компонент `white_fog:light_fuel`,
сеть (`white_fog:light_refuel`/`light_source_snapshot`), light engine, клиентские ресурсы и Work Panel.

Runtime-доказательство: `gradlew.bat build --no-daemon --console=plain`, `scripts\server_smoke.bat`
(строка `WHITEFOG_LIGHT_SELFTEST ... status=SUCCESS`), `scripts\client_smoke.bat` и интерактивная приёмка.
