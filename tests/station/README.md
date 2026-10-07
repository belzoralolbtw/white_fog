# tests/station — sandbox этапа 1.4 («ПЛОСКИЙ КАМЕНЬ И КАМУШКИ»)

Это **независимый sandbox** для доказательства логики серверно-авторитетной станции
`white_fog:flat_stone` и подбираемого блока `white_fog:small_stone` до применения в `src/`.

## Что это НЕ доказывает

Это **чистая логика без Minecraft**. Тест НЕ доказывает runtime-поведение игры
(block entity, миксины, сеть, реальные `BlockState`/`ItemStack`, piston/explosion движки).
Для этого нужна сборка мода и запуск клиента/сервера архитектором.

## Что проверяется

1. 100 циклов установить/снять — количество предметов постоянно (blockChanges = 200);
2. два игрока снимают одну станцию — ровно один результат;
3. полный инвентарь — ровно один pending-drop с `pickupDelay = 10`;
4. неподходящее место (нет опоры / жидкость / занято / нет прав) не списывает предмет;
5. два клика по одному камушку в одном тике — ровно один предмет;
6. cooldown 2 тика не даёт подобрать два разных камушка в одном тике;
7. pending-drop переживает unload/load чанка (без потери и дубля);
8. recovery: движение отменяет задачу, cobblestone не теряется (двойной запас не списывается);
9. recovery: 100 тиков без движения → −2 cobblestone, +1 поставленный flat_stone;
10. recovery без второго cobblestone / не на твёрдой земле не запускается;
11. поршень не двигает flat_stone; small_stone разрушается ровно с 1 дропом;
12. взрыв даёт ровно один block item, повторный вызов — без второго;
13. save/load active job сохраняет владельца, escrow, output, mode, progress, revision;
14. занятая станция не снимается и отвечает точным текстом «Сначала забери материалы и результат»;
15. vanilla `minecraft:stone_slab` больше не открывает станцию;
16. revision guard: повторное снятие не даёт второго результата;
17. вариант модели камушка детерминирован и не влияет на loot;
18. установка small_stone по тем же правилам (unsupported/liquid);
19. смена измерения отменяет recovery.

## Утверждённые значения (синхронизированы с `src`)

`RECOVERY_TICKS = 100`, `RECOVERY_COST = 2` (cobblestone), `SMALL_STONE_PICKUP_COOLDOWN_TICKS = 2`,
`PENDING_PICKUP_DELAY_TICKS = 10`, `SCHEMA_VERSION = 1`; id: `white_fog:flat_stone`,
`white_fog:small_stone`, `minecraft:cobblestone`; текст отказа — «Сначала забери материалы и результат».

> **Атомарность recovery:** `cobblestone` НЕ списывается на старте — только при успешном
> завершении (после повторной серверной проверки входов). Поэтому отмена/движение/урон
> не требуют отдельного «возврата»: потерь нет по построению.

> **Pending при полном инвентаре:** остаток выбрасывается обычным `ItemEntity` с
> `pickupDelay = 10`; выгруженный чанк сохраняет сущность на диск, поэтому unload/load
> не теряет и не дублирует предмет.

## Запуск (foreground, само-завершение)

Из корня проекта:

```
tests\station\run_station_selftest.bat
```

`.bat` компилирует только `tests/station/src` во временный `tests/station/build`
(в `.gitignore` через `build/`), запускает `SelfTest` в foreground и пишет UTF-8 лог
в `logs\station_selftest_<stamp>.txt` + `status=SUCCESS|TIMEOUT|FAILURE` в
`logs\station_selftest_<stamp>.result`. Не затрагивает `build.gradle`, `src/`, ресурсы.
