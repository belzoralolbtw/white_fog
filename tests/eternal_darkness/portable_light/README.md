# Sandbox: переносной свет факела в левой руке (новый этап, фаза 1)

Независимый foreground-self-test **чистой логики** переносного света в левой руке + меню источника + HUD.
НЕ входит в основной `build.gradle`, НЕ трогает `src/`, `README.md`, `AGENTS.md`, `ROADMAP_STEPS.md`.
Main-реализация этапа 1.7 уже применена (см. ниже); sandbox остаётся logic-only доказательством.

## Запуск

```bat
tests\eternal_darkness\portable_light\run_portable_light_selftest.bat
:: exit 0 только при status=SUCCESS; TIMEOUT -> 124; FAILURE -> 1
```

Пишет UTF-8 лог `logs\portable_light_selftest_<stamp>.txt` и machine-readable
`logs\portable_light_selftest_<stamp>.result` (`status=SUCCESS|FAILURE|TIMEOUT`).

Текущий результат: `passed=459 failed=0`, `SELFTEST status=SUCCESS`
(`logs/portable_light_selftest_20261008_200240.txt`).

> **Тикет поверх 1.7 (реализован в main).** Модели `FuelStateModel`, `SourceActionModel`,
> `PortableUiModel`, `NoPulseVisualModel` и `PortableLightLayout` формализуют НОВУЮ семантику:
> уголь = ровно 20% ёмкости, `Заправить` НИКОГДА не меняет `lit`, `(remaining, lit)` персистентны
> (place/drop round-trip), железо предмета тикается в инвентаре (main + offhand), HUD без сырых
> тиков и `свет 14`, дальний туман тьмы без пульсации. Старые модели (`PortableLightModel.tick/
> extinguish/relight/refuel`) сохранены без изменений ради прежних проверок.

## Что формализует

- **Offhand-предмет**: принимаются только факелы `minecraft:torch`/`wall_torch` → `TORCH`
  и `soul_torch`/`soul_wall_torch` → `SOUL_TORCH`; фонари/костры/прочее — не переносной свет.
- **Компонент** `white_fog:light_fuel`: `remaining>0` = переносной свет; нет компонента/0/негатив = нет
  света; больше ёмкости — clamp (torch 12000, soul torch 8000). Эмиссия torch 14, soul torch 10.
- **Порча стека**: заряженный компонент при `count>1` → light off, операции запрещены (без split/расхода).
- **Адаптер exposure**: вход `blockLight` заменяется на `max(vanilla, emission)` — это НЕ радиус и НЕ
  сложение; формула exposure (`DarknessConfig`: bright≥9 → −2, neutral 5..8 → 0, dark≤4 → +1/+2) НЕ меняется.
- **Countdown**: тикается только горящий (`lit`) факел с запасом; 1→0 сразу тушит; погашенный не тратит топливо.
- **Тушение/зажигание**: тушение сохраняет остаток и ставит `lit=false`; зажигание сохраняет остаток.
- **Заправка**: авто-заправка погашенного запрещена (`REFUSED_EXTINGUISHED`), явная заправка разрешена и
  зажигает; переполнение — `FULL` без частичного списания; ровно 1 предмет при `OK`; порча — отказ.
- **Два игрока независимы** (свои countdown/тушение/заправка).
- **Форматирование**: тики → «Nс / Nм Mс / Nч Mм», 0 → «нет топлива».
- **Подпись часов**: «Ночь · HH:MM» по мировому времени (0 тиков = 06:00, 18000 = 00:00), без
  ванильного вводящего в заблуждение «дня».
- **Кнопки**: `Заправить`/`Потушить`/`Зажечь` и правила их доступности.
- **Действия с плацед-источником** (`SourceActionModel`): тушение/зажигание сохраняют остаток,
  статусы `OK/ALREADY_LIT/ALREADY_UNLIT/NO_FUEL_TO_LIGHT/ACCEPTED/FULL/NO_FUEL/MANAGED_BY_POST`;
  `managedByPost` запрещает действия и кнопки.
- **Раскладка UI**: фиксированные границы title/status/fuel/time + 3 кнопки; на узких ширинах
  (60..190 px) панель не выходит за доступную ширину, строки/кнопки не переполняются и не пересекаются.
- **Тикет поверх 1.7** (`FuelStateModel`/`SourceActionModel`/`PortableUiModel`/`NoPulseVisualModel`):
  уголь = 20% ёмкости (torch 2400, soul 1600, lantern 4800, soul lantern 3200, campfire 3200;
  stick/log костра сохранены), `refuelPartial` не меняет `lit`, переполнение `FULL` без списания,
  `relight` — единственное `unlit -> lit` и без расхода; персистентность place/drop сохраняет
  `remaining+lit`; инвентарный countdown `1 -> 0` тушит; offhand-эмиссия только при
  `lit+remaining>0+count=1`; HUD-строки `В руке: Факел`/`Факел душ` и `Осталось: X мин Y сек` без
  сырых тиков/света; постоянные выходы визуального адаптера тьмы (offset `0.16`, fog ≥ 48, cap 0.55,
  `voidFactor` сохранён) против пульсирующего ванильного косинуса.

## Что НЕ доказывает

Это **logic-only**: не проверяются реальный `ItemStack`/item-компонент `white_fog:light_fuel`, инвентарь
левой руки, серверный tick/сеть, light engine, меню (клики) и HUD-рендер (пиксели). Runtime-доказательство —
сборка мода, smoke-логи и интерактивная приёмка архитектором.

## References (прочитано, адаптировать — не копировать)

- `LambdAurora/LambDynamicLights`, ветка **26.2**:
  - `HOW_DOES_IT_WORK.md` — метод динамического света через lightmap-координаты (в 26.2 — `LightCoordsUtil`).
  - `src/main/java/dev/lambdaurora/lambdynlights/mixin/BrightnessGetterMixin.java` — инъекция
    `@ModifyReturnValue` в `LightCoordsUtil.BrightnessGetter` (в 26.2 старый `WorldRenderer#getLightmapCoordinates`
    заменён на `LightCoordsUtil`).
  - `src/main/java/dev/lambdaurora/lambdynlights/mixin/lightsource/LivingEntityMixin.java` — свет от
    предметов в руках/экипировке.
  - `api/src/main/java/dev/lambdaurora/lambdynlights/api/item/ItemLightSource.java` — сопоставление
    предмета и его светимости (0..15).
- API MC 26.2 (javap, deobf jars): `LivingEntity#getOffhandItem()`, `getItemInHand(InteractionHand)` +
  `InteractionHand.OFF_HAND`; `Inventory.SLOT_OFFHAND`; `BlockAndLightGetter#getBrightness(LightLayer,BlockPos)`;
  `net.minecraft.util.LightCoordsUtil` / `LightCoordsUtil.BrightnessGetter` (клиентский динамический свет);
  `AbstractContainerScreen`, `AbstractContainerMenu#clickMenuButton`, `MenuType`.

## Реализованные main-файлы (этап 1.7)

- `src/main/java/com/whitefog/darkness/light/PortableLightPolicy.java` — чистая политика (модель sandbox).
- `src/main/java/com/whitefog/darkness/light/PortableLightService.java` — серверный адаптер exposure.
- `src/main/java/com/whitefog/darkness/light/LightPanelFormat.java` — форматирование duration/часов/подписей.
- `src/main/java/com/whitefog/darkness/light/SourceActionPolicy.java` — чистая политика действий панели.
- `src/main/java/com/whitefog/darkness/light/LightMenuLayout.java` — чистая bounded-раскладка меню
  (порт `PortableLightLayout`, реальное измерение шрифтом; используется `LightSourceScreen`).
- `src/main/java/com/whitefog/network/SourcePanelPayload.java` — S2C состояние панели.
- `src/main/java/com/whitefog/content/menu/LightSourceMenu.java` — меню источника (clickMenuButton).
- `src/client/java/com/whitefog/client/screen/LightSourceScreen.java` — экран источника
  (адаптивная панель через `LightMenuLayout` + 3 кнопки; длительность без сырых тиков).
- `src/client/java/com/whitefog/client/hud/LightWorkPanelHud.java` — HUD по `G` (bounded-раскладка, `Ночь · HH:MM`).
- `src/client/java/com/whitefog/client/portable/PortableLightClient.java` +
  `src/client/java/com/whitefog/client/mixin/PortableLightBrightnessGetterMixin.java` — клиентский
  динамический свет (LambDynamicLights 26.2, `LightCoordsUtil.BrightnessGetter`).
- Интеграция: `WhiteFog`/`WhiteFogPayloads`/`WhiteFogContent`/`LightSourceInteractions`/
  `LightSourceService` (common), `WhiteFogClient`/`LightClientNetworking`/`ClientLightState` (client).
