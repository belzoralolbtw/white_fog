# AGENTS.md — White Fog (`white_fog`)

## Status
- **Этап 1.1 «Фундамент мода и серверное состояние игрока» — реализован.** Сборка `BUILD SUCCESSFUL`, dedicated-server smoke — `status=SUCCESS`.
- **Этап 1.2 «Запрет ванильного крафта» — реализован.** Сборка `BUILD SUCCESSFUL`; dedicated-server smoke — `status=SUCCESS`, self-тест `WHITEFOG_CRAFTING_SELFTEST ... craftingRecipes=0 craftingTableRecipe=false status=SUCCESS`.
- **Этап 1.3 «Разрушение блоков и станции» — РЕАЛИЗОВАН + review-фиксы.** Сборка `BUILD SUCCESSFUL`; dedicated-server smoke — `status=SUCCESS`, лог содержит `White Fog: block-break rules registered (stage 1.3, server-authoritative timer)`. Sandbox `tests/break_timer` обновлён под утверждённые значения и проходит (`passed=82 failed=0`, `SELFTEST status=SUCCESS`). Runtime-нюанс client prediction учтён (см. ниже); интерактивная приёмка ещё не проведена.
- **Этап 1.3 — HOTFIX клиентского предсказания (запрошен явно).** Клиент теперь не начинает/не продолжает разрушение блока, который сервер отклонит по тем же правилам (`BlockBreakRules`+`BlockBreakPolicy`): `MultiPlayerGameMode#startDestroyBlock`/`continueDestroyBlock` отменяются на `HEAD` (`setReturnValue(false)`) → нет ложного прогресса, трещин, локального удаления и исходящих `START_DESTROY_BLOCK`. `UNCLASSIFIED` остаётся ванильным. Сборка `BUILD SUCCESSFUL` (`logs/build_20261007_215605.txt`); bounded client smoke `scripts\client_smoke.bat` — `status=SUCCESS`, лог `logs/client_smoke_20261007_215637.txt` содержит `White Fog: client initializer ready (stage 1.3 hotfix), MultiPlayerGameModeMixin applied=true`; server smoke — `status=SUCCESS` (`logs/server_smoke_20261007_215719.txt`); sandbox — `passed=82 failed=0` (`logs/break_timer_selftest_20261007_215755.txt`). См. отдельный раздел «Этап 1.3 — hotfix».
- **Этап 1.3 — REGRESSION FIX клиентского предсказания (запрошен явно по жалобе пользователя).** Первый hotfix отменял ваниль только для `DENY_*`, поэтому (а) для `ALLOW` земля ломалась ванильно быстро → сервер откатывал → цикл до серверного commit; (б) при `DENY_*` подавленный `START` не доходил до сервера, и серверная подсказка «Слишком крепко — нужен инструмент» не показывалась. Теперь ванильное предсказание подавляется для ВСЕХ контролируемых категорий (`classify != UNCLASSIFIED`); для `ALLOW` клиент вручную шлёт ровно один `ServerboundPlayerActionPacket(START_DESTROY_BLOCK, …, sequence)` через ванильный `startPrediction` (корректная sequence) и `ABORT` при отпускании/смене цели/инструмента; для `DENY_*` `START` не шлётся, а подсказка показывается ЛОКАЛЬНО в action bar (`LocalPlayer#sendOverlayMessage`) с cooldown 20 тиков на причину. `UNCLASSIFIED` — ваниль. Сборка `BUILD SUCCESSFUL` (`logs/build_20261007_221853.txt`); client smoke `scripts\client_smoke.bat` — `status=SUCCESS`, маркер `MultiPlayerGameModeMixin applied=true` (`logs/client_smoke_20261007_221911.txt`); server smoke — `status=SUCCESS` (`logs/server_smoke_20261007_221939.txt`); sandbox — `passed=102 failed=0` (`logs/break_timer_selftest_20261007_221905.txt`). См. раздел «Этап 1.3 — regression fix».
- **Этап 1.3 — SWING FIX визуального отклика ALLOW (запрошен явно).** После regression fix'а при зажатой ЛКМ по разрешённым блокам не было анимации удара: `Minecraft#continueAttack` делает `addBreakingBlockEffect`+`player.swing` только если `continueDestroyBlock` вернул `true`, а миксин гасил метод. Теперь для контролируемого `ALLOW` `continueDestroyBlock` (`@Inject at HEAD, cancellable`) возвращает `true`, поэтому вызывающий выполняет ванильный визуал (частицы + swing, ванильная cadence). Тело метода всё равно пропущено: ванильного прогресса/локального удаления/пакетов `START`/`STOP` нет, серверный таймер не затронут. `DENY_*` возвращает `false` (без свинга, поведение сохранено), `UNCLASSIFIED` — ваниль. Сборка `BUILD SUCCESSFUL` (`logs/build_20261007_223929.txt`); client smoke `scripts\client_smoke.bat` — `status=SUCCESS`, `MultiPlayerGameModeMixin applied=true` (`logs/client_smoke_20261007_223941.txt`); server smoke — `status=SUCCESS` (`logs/server_smoke_20261007_223512.txt`); sandbox — `passed=118 failed=0` (`logs/break_timer_selftest_20261007_223354.txt`). См. раздел «Этап 1.3 — swing fix».
- **Интерактивная приёмка (пользователь, вручную) — ПОДТВЕРЖДЕНА для этапов 1.1/1.2/1.3, включая hotfix/regression/swing fix.** Пользователь подтвердил, что версия работает «идеально»; версия `0.1.0` **закрепляется** для первичной публикации (PUBLIC GitHub). Подтверждение относится к поведению, описанному в разделах ниже. Проверки, которые явно НЕ проводились (смерть/`copyOnDeath`, повторный вход после смерти, второй игрок, смена измерения, выгрузка/загрузка чанка) — **не заявляются** и остаются в TODO.
- **Цель:** Minecraft **26.2**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, Java **25+** (собрано JDK 26.0.2.1), Gradle **9.7.1**, Loom **1.18.3**.
- Stonecutter/Forge/NeoForge/прочие версии MC **не используются**; строки `mappings` в сборке нет (26.2 деобфусцирован).
- Mod ID: `white_fog`; group `com.whitefog`; version `0.1.0`; env `*` (запускается и как dedicated server, и как клиент).
- Репозиторий локальный (git init, коммитов нет). По явному запросу пользователя выполнена подготовка **документации/гигиены** для первичной публикации на GitHub (PUBLIC): обновлены `README.md`, `.gitignore`, `AGENTS.md`, удалён stray-файл `fabric.mod.json` в корне; `.github/workflows/build.yml` проверен. **staging/commit/создание репозитория/push выполняет архитектор** — здесь они не делались, GitHub-репозиторий ещё не создан.

## Structure
```
src/main/java/com/whitefog/            # COMMON — не импортирует net.minecraft.client.*
  WhiteFog.java                        # common ModInitializer: порядок init (payloads -> attachment -> server -> crafting lock)
  WhiteFogConfig.java                  # tunables/границы/дефолты, комментарии на русском
  WhiteFogAttachments.java             # AttachmentType<PlayerSurvivalState> (persistent + copyOnDeath)
  crafting/CraftingLock.java           # этап 1.2: запрет крафта — предикаты слотов, фильтр рецептов, блок use верстака, self-тест
  breaking/BlockBreakRules.java        # этап 1.3: классификация блоков (Category) и инструментов (ToolKind), hot/filled-проверки
  breaking/BlockBreakPolicy.java       # этап 1.3: решение ALLOW/DENY_* и длительности в тиках (значения из WhiteFogConfig)
  breaking/BreakSession.java           # этап 1.3: запись сессии (UUID, измерение, pos, копия инструмента, startState, startTick, requiredTicks)
  breaking/BreakTimerService.java      # этап 1.3: START/STOP/ABORT, серверный tick, валидация, commit/дедуп, refuse+cooldown, dev-лог
  breaking/StationRemoval.java         # этап 1.3: безопасное снятие станции (ровно 1 предмет, без выброса содержимого)
  network/PlayerStateSyncPayload.java  # S2C record + TYPE + STREAM_CODEC
  network/WhiteFogPayloads.java        # PayloadTypeRegistry.clientboundPlay().register(...) ОДИН раз
  state/PlayerSurvivalState.java       # серверное состояние, NBT Codec, ревизия, describe()
  state/ClothingSlot.java              # слоты мокроты одежды
  server/WhiteFogServer.java           # ОДИН END_SERVER_TICK + join/respawn/dim-change + dev-команда
  server/SurvivalTicker.java           # логика тика (normalize + интервалы процессов)
  server/PlayerStateSyncService.java   # отправка снимка (изменение/период/canSend)
  server/command/WhiteFogDebugCommand.java  # /whitefog debug [player] (только dev)
  mixin/AbstractContainerMenuMixin.java       # этап 1.2: отмена clicked по сетке/результату InventoryMenu/CraftingMenu (client+server)
  mixin/RecipeManagerMixin.java               # этап 1.2: @ModifyVariable apply(RecipeMap) — удаление всех CRAFTING-рецептов
  mixin/ServerGamePacketListenerImplMixin.java # этап 1.2: блок placeRecipe и creative set-slot в слоты крафта
  mixin/ServerPlayerGameModeMixin.java         # этап 1.3: @Inject handleBlockBreakAction HEAD → BreakTimerService (START/STOP/ABORT)
src/client/java/com/whitefog/client/   # CLIENT — только client API
  WhiteFogClient.java                  # ClientModInitializer: receiver + HUD + disconnect clear + dev self-check миксина
  ClientPlayerState.java               # клиентский кэш снимка (не источник истины)
  client/network/WhiteFogClientNetworking.java  # ClientPlayNetworking.registerGlobalReceiver
  client/hud/WhiteFogHud.java          # заглушка HudElement (пока ничего не рисует)
  client/mixin/MultiPlayerGameModeMixin.java # этап 1.3 swing fix: подавление ванильного тела для ВСЕХ контролируемых; ALLOW → ручной START/ABORT + возврат true (ванильный визуал удара), DENY_* → false + локальная подсказка (client-only)
src/client/resources/white_fog.client.mixins.json # client-only миксин-конфиг (package com.whitefog.client.mixin, "client": [...])
src/main/resources/fabric.mod.json     # entrypoints main/client, mixins (common + client env), depends minecraft ~26.2
src/main/resources/white_fog.mixins.json  # этап 1.2: common-миксины (com.whitefog.mixin), compatibilityLevel JAVA_25
src/main/resources/assets/white_fog/icon.png
scripts/build.bat                      # сборка с UTF-8 логом в logs/
scripts/server_smoke.bat|.ps1          # bounded runServer smoke (hard-timeout, только свой PID-три, non-zero exit при не-SUCCESS)
scripts/client_smoke.bat|.ps1          # этап 1.3 hotfix: bounded runClient smoke (hard-timeout 180 c, только свой PID-три)
README.md                              # краткое описание проекта (публикация)
.gitignore                             # игнор logs/build/.gradle/run/secrets/local configs/temp
.github/workflows/build.yml            # CI Fabric-шаблона: gradlew build, без секретов
ROADMAP_STEPS.md                       # ТЗ (не редактировать)
tests/break_timer/                     # ЭТАП 1.3: независимый sandbox (НЕ в основном build, НЕ трогает src/)
  src/com/whitefog/tests/breaktimer/   # чистая логика lifecycle таймера разрушения (без Minecraft) + ClientGate (клиентский гейт)
  run_break_timer_selftest.bat         # javac+java foreground, hard-timeout, UTF-8 лог + .result в logs/
  README.md                            # что доказывает и что НЕ доказывает (не runtime-proof)
```
`common` не содержит ссылок на `net.minecraft.client.*` — проверено по скомпилированным классам
`build/classes/java/main` (см. «Build & run»).

## Done
### Этап 1.1 (полностью)
- **Серверное состояние игрока** — Fabric Data Attachment API v1:
  `AttachmentRegistry.create(id, b -> b.initializer(PlayerSurvivalState::createDefault).persistent(CODEC).copyOnDeath())`.
  Хранится **по одному вложению на игрока** (не в static-полях), в playerdata → переживает save/load,
  unload/load чанка, disconnect/reconnect.
- **Смерть / смена измерения / копирование игрока** — `copyOnDeath()` переносит состояние на нового игрока;
  дополнительно `ServerPlayerEvents.AFTER_RESPAWN` и `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`
  сбрасывают служебную синхронизацию и шлют свежий снимок. Второй компонент не создаётся.
- **Поля** (`PlayerSurvivalState`, точность/границы в `WhiteFogConfig`):
  `weightKg` (0.1 кг, шаг 0.1), `fatigue` 0–100, `body` 0–100, `perceivedTemperatureC`, `calories` 0–3000,
  `water` 0–100, `condition` 0–100, `clothingWetness` по слотам (`ClothingSlot`: head/chest/legs/feet),
  `dysentery{remainingTicks,lastProcessedAtTick}`, `sleep{active,startedAt,lastProcessedAt}`,
  `work{active,startedAt,lastProcessedAt}`, `activeGoal{id,startedAt}`.
  Дефолты: fatigue=100, body=100, calories=3000, water=100, condition=100, вес=0, дизентерия=0 (нет),
  sleep/work = неактивны, goal = "".
- **NBT-сериализация** — вручную через `CompoundTag` (`toNbt`/`fromNbt`), Codec = `CompoundTag.CODEC.xmap(...)`.
- **Один защищённый серверный тик** — единственный `ServerTickEvents.END_SERVER_TICK` (флаг `registered`),
  обход игроков с try/catch на игрока и целиком. Обработка длительных процессов идёт по фактически
  прошедшему интервалу (`now - lastProcessedAtTick`), что исключает «перескок» таймеров.
- **Сеть** — только S2C `white_fog:player_state_sync` (NBT-снимок). Тип зарегистрирован **ровно один раз**
  в common initializer (`WhiteFogPayloads`) до клиентского получателя. Перед отправкой `ServerPlayNetworking.canSend(...)`.
- **Клиент** — `ClientModInitializer`, получатель пакета, кэш `ClientPlayerState`, **заглушка HUD**
  (`HudElementRegistry.addLast` + `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`), очистка кэша на
  disconnect. Клиентских классов нет в common; dedicated server стартует без HUD.
- **Dev-команда** `/whitefog debug [player]` — регистрируется только при `FabricLoader.isDevelopmentEnvironment()`,
  печатает состояние **без изменения** (использует `peek`, не создаёт вложение). Требует прав `LEVEL_GAMEMASTERS`.
- **Конфигурация** — `WhiteFogConfig` с русскими комментариями (дефолты, границы, шаг веса, интервал синхронизации,
  константы времени 20/24000).

### Этап 1.2 (полностью)
- **Инвентарная сетка 2x2 (`minecraft:inventory`) недоступна.** `AbstractContainerMenuMixin`
  (`@Inject` в `AbstractContainerMenu#clicked` на `HEAD`, `cancellable`) отменяет любой клик по слотам
  входа/результата `InventoryMenu`. Слоты вычисляются не по «магическим» индексам, а через
  `AbstractCraftingMenu#getResultSlot()` и `getInputGridSlots()` (для `InventoryMenu` это слоты 0 и 1–4).
  Покрыты обычный клик, shift-click (`QUICK_MOVE`), drag (`QUICK_CRAFT`), `PICKUP_ALL` и повторная
  отправка click-пакетов. Миксин общий — отменяет и клиентское предсказание (нет рассинхрона).
- **Сообщение** `Крафтить на бегу нельзя` (`CraftingLock.MESSAGE_CANNOT_CRAFT`, `Component.literal`) —
  показывается сервером в action bar и для 2x2, и для верстака.
- **Обычный верстак 3x3 не открывается и не разрушается.** `BlockEvents.USE_WITHOUT_ITEM` (Fabric API,
  без лишнего миксина) для `Blocks.CRAFTING_TABLE` возвращает `InteractionResult.FAIL` — ванильный
  `CraftingTableBlock#useWithoutItem` (открытие vanilla 3x3 + recipe book) не выполняется, блок цел.
  Дополнительно любые клики по сетке/результату `CraftingMenu` отменяются тем же миксином (на случай
  открытия меню иным путём: спектатор/команда/мод).
- **Рецепты крафта удалены из recipe manager.** `RecipeManagerMixin` (`priority = 1500`,
  `@ModifyVariable(argsOnly = true, at = HEAD)` на `RecipeManager#apply(RecipeMap, ResourceManager, ProfilerFiller)`)
  подменяет набор рецептов, выкидывая все `RecipeType.CRAFTING`, включая `minecraft:crafting_table`.
  Работает при старте и при каждом `/reload`; книга рецептов пуста, `getRecipeFor(CRAFTING,...)` = пусто.
  Слот результата всегда пуст.
- **Креатив/книга рецептов.** `ServerGamePacketListenerImplMixin` отменяет `handleSetCreativeModeSlot`
  для слотов сетки/результата `inventoryMenu` (нельзя «положить» предметы в 2x2) и `handlePlaceRecipe`
  для любого `AbstractCraftingMenu` (нельзя выложить рецепт из книги).
- **Проверки сервера.** `CraftingLock.shouldBlockClick` проверяет тип меню (`instanceof` + `MenuType.CRAFTING`),
  владельца/активное меню (`player.containerMenu == menu`), индекс слота и (для верстака) дистанцию
  (`CraftingMenu#stillValid`). Отказ не удаляет и не дублирует предметы; при закрытии меню ванильный
  `AbstractContainerMenu#removed` сам возвращает содержимое transient-сетки (см. «Decisions»).
- **Self-тест.** `CraftingLock.logRecipeSelfTest` по `SERVER_STARTED`/`END_DATA_PACK_RELOAD` пишет строку
  `WHITEFOG_CRAFTING_SELFTEST phase=... craftingRecipes=<n> craftingTableRecipe=<bool> status=...`.
  Прогон smoke: `craftingRecipes=0 craftingTableRecipe=false status=SUCCESS`; доказывающие строки
  «Loaded 465 recipes» (было 1585) и «removed 1120 vanilla crafting recipe(s)».
- **Совместимость.** Stage 1.1 (persistent state/network/HUD) не затронут; common по-прежнему без
  `net.minecraft.client.*` (проверено по `build/classes/java/main`).

## Этап 1.3 (полностью)

- **Перехват.** `mixin/ServerPlayerGameModeMixin` — `@Inject(method = "handleBlockBreakAction", at = HEAD, cancellable)`
  (javap-проверено: `ServerPlayerGameMode#handleBlockBreakAction(BlockPos, ServerboundPlayerActionPacket$Action,
  Direction, int, int)`). Через `@Shadow` читаются `player` и `level`. Действие отдаётся
  `BreakTimerService.handleAction(...)`; возврат `true` → `ci.cancel()` (ванильный прогресс/дроп подавлены).
  Регистрация — в `white_fog.mixins.json`.
- **Тик.** Отдельного миксина на `ServerPlayerGameMode#tick` нет: единый `END_SERVER_TICK` мода
  (`WhiteFogServer.onEndServerTick`) вызывает `BreakTimerService.tickAll(server)` после обхода игроков.
- **Сессии.** `BreakSession` (record): UUID, `ResourceKey<Level> dimension`, immutable `BlockPos`, копия
  `ItemStack` инструмента, `BlockState startState`, `Category`, `ToolKind`, `startTick`, `requiredTicks`.
  Хранятся в сервисной `Map<UUID, BreakSession>` (не в static-полях сущностей). `START/STOP/ABORT`
  обрабатываются по правилам; **commit — только по серверному таймеру**.
- **Валидация** (`BreakTimerService.validate`, вызывается на каждом tick и STOP, а также на
  повторном START): игрок жив (`isAlive`/`isRemoved`), чанк загружен (`Level#isLoaded`), не вне
  границ мира (`Level#isOutsideBuildHeight`), измерение (`player.level().dimension()`), права
  (см. ниже), инструмент через `ItemStack.isSameItemSameComponents` (учитывает компоненты),
  тот же `BlockState`, блок не воздух и не неразрушимый, не горячо, **не наполнено**
  (`BlockBreakRules.isFilled` — динамически, не по старой категории), дистанция
  `Player#isWithinBlockInteractionRange(pos, 1.0)` (значение из `WhiteFogConfig.BREAK_INTERACTION_RANGE_MARGIN`).
  Отмена — при смерти/disconnect/смене измерения/блока/инструмента/дистанции/наполненности/hot/permission.
- **Разрешения (дублируют ванильные гейты, т.к. миксин отменяет ванильный `handleBlockBreakAction`).**
  `BreakTimerService.hasBreakPermission` проверяет на сервере: не спектатор, `Level#mayInteract`,
  `MinecraftServer#isUnderSpawnProtection` (spawn-protection), `Player#blockActionRestricted`
  (adventure/без прав). Неразрушимость — `BlockState#getDestroySpeed(level, pos) < 0` (bedrock/portal/…);
  такие блоки классифицируются как `UNCLASSIFIED` (`GameMasterBlock` — тоже), т.е. остаются ванильными.
  Дистанция (`isWithinBlockInteractionRange`) и «слишком высоко» (`pos.y > level.getMaxY()`) проверяются в
  `handleAction` ДО чтения `BlockState`. Эти же гейты повторяются в `commit`.
- **Классификация (`BlockBreakRules`).**
  * `Category`: `SOFT_PLANT`, `SOFT_LEAF`, `SOFT_SOIL`, `SOFT_ICE`, `SOFT_SNOW`, `LANTERN`,
    `STATION_WOOD` (crafting_table/loom), `STATION_HARD` (furnace/blast_furnace/smoker без LIT и пустые,
    cauldron пустой, anvil), `STATION_HOT`, `STATION_FILLED`, `HARD`, `UNCLASSIFIED`.
  * `ToolKind`: `HAND`, `VANILLA_PICKAXE/AXE/SHOVEL` (по `ItemTags.PICKAXES/AXES/SHOVELS` через
    `typeHolder().is(...)`), `OTHER`, `MOD_PICKAXE_BRONZE/IRON/STEEL` (точные id
    `white_fog:bronze_pickaxe`/`iron_pickaxe`/`steel_pickaxe` из `WhiteFogConfig`).
  * Твёрдые: `BlockTags.LOGS`, `BlockTags.PLANKS`, `BlockTags.IMPERMEABLE` (стекло) **или**
    `BlockState#requiresCorrectToolForDrops()` (камень/руды/кирпич/металл/тяжёлые механизмы).
    Декоративные блоки без требования инструмента (рельсы, кнопки, двери и т.п.) остаются
    `UNCLASSIFIED` → ванильное поведение.
  * Горячее: `AbstractFurnaceBlock#LIT`, `CampfireBlock#isLitCampfire`. Наполненное: `AbstractCauldronBlock#isFull`,
    непустой `Container` у печи (`BlockBreakRules.isFilled` — динамически в момент вызова). Модовые
    `heat/fuel` states появятся позже (этапы 6.x) — hook-точка `isHot`.
  * Неразрушимые (`getDestroySpeed < 0`, bedrock/portal/…) и `GameMasterBlock` (barrier и т.п.)
    возвращают `UNCLASSIFIED` → остаются ванильными (нельзя сломать через мод).
- **Политика (`BlockBreakPolicy`, значения из `WhiteFogConfig`).** Мягкое рукой 40; земля/песок/гравий/глина
  рукой 60 и лопатой 10; лёд 80; снег 40; фонарь 40; станция 40 (дерево: рука/топор; твёрдые: только модовое
  кайло); твёрдое модовым кайлом 120 (bronze) / 80 (iron) / 40 (steel). `SOFT_PLANT`/`SOFT_LEAF` — **0 дропа**
  (в т.ч. ножницами/топором; дроп с листвы — отдельная ПКМ-механика этапа 3.1). Сообщение отказа
  `Слишком крепко — нужен инструмент` — action bar, cooldown 20 тиков на игрока и причину
  (`RefuseReason` `NO_TOOL/HOT/FILLED`).
- **Commit (ровно один раз).** `BreakTimerService.commit` — единственная точка изменения блока: ПЕРЕчитывает
  фактическое состояние и повторно проверяет права (`hasBreakPermission`), границы, неразрушимость, горячее,
  наполненность, дистанцию и политику инструмента. Повторный пакет/второй игрок/второй обработчик видят
  `state.isAir()` и второй предмет не выдают. Обычные блоки: `playerWillDestroy` + `Block.getDrops(...)` +
  `Level#destroyBlock(pos, false, player, 512)` + `Block.popResource` (ванильный лут без гейта
  `hasCorrectToolForDrops`, поэтому модовое кайло не теряет дроп). Станции: `StationRemoval.commit` —
  последний defensive guard `BlockBreakRules.isFilled` ДО `removeBlockEntity`, затем `getCloneItemStack` до
  удаления, `removeBlockEntity`, `destroyBlock(..., false, ...)`, ровно один предмет. Содержимое не
  выбрасывается и не дублируется.
- **Fail-closed.** При внутренней ошибке в `handleAction` мод НЕ возвращает управление ванили (`return false`
  — это был бы bypass): `failClosed(...)` очищает сессию, откатывает блок и отменяет действие.
- **Creative.** Не обходит категории/дистанцию/права/горячее/наполненное и запрет ванильных tools. Таймер = 0
  (немедленный `commit` после тех же проверок, которые `commit` перечитывает заново), durability не расходуется.
- **Клиентское предсказание 26.2 (важно).** По javap `MultiPlayerGameMode` клиент оптимистично меняет блок
  и шлёт `STOP_DESTROY_BLOCK` при СВОЁМ (ванильном) прогрессе; `ServerGamePacketListenerImpl#handlePlayerAction`
  затем безусловно `ackBlockChangesUpTo(seq)`. Поэтому **преждевременный `STOP` НЕ отменяет сессию** (иначе
  медленное разрушение невозможно) — отмена по `ABORT`/валидации. При отказе мод шлёт
  `ClientboundBlockUpdatePacket(pos, state)` и `inventoryMenu.sendAllDataToRemote()` (снимает предсказание
  блока и «ghost durability» — серверный урон инструмента не начисляется).
- **Dev-диагностика.** В dev-среде пишутся строки `White Fog [break-refuse] ...` и `White Fog [break-abort] ...`.
- **Регистрация.** `WhiteFog.onInitialize` → `BreakTimerService.register()` (только лог, идемпотентно);
  `WhiteFogServer.register()` → `ServerPlayConnectionEvents.DISCONNECT` (очистка сессии/cooldown);
  `onPlayerRespawn`/`onPlayerChangeLevel` → `BreakTimerService.clearSession(...)`.
- **Сборка/проверки (после review-фиксов).** `gradlew.bat build --no-daemon --console=plain` — BUILD SUCCESSFUL
  (`logs/build_20261007_213604.txt`); smoke `scripts\server_smoke.bat` — `status=SUCCESS`
  (`logs/server_smoke_20261007_213616.txt`, строка `White Fog: block-break rules registered (stage 1.3, server-authoritative timer)`);
  sandbox `tests\break_timer\run_break_timer_selftest.bat` — `passed=82 failed=0`, `SELFTEST status=SUCCESS`
  (`logs/break_timer_selftest_20261007_214031.txt`).
- **Известные ограничения.** (1) Модовые кайла (`white_fog:*_pickaxe`) не зарегистрированы — добыча твёрдых
  блоков и твёрдых станций модовым кайлом появится после этапа 6.4; правила компилируются и заработают сразу
  после регистрации предметов. (2) Длительные процессы урона/голода/температуры — вне 1.3. (3) При
  преждевременном `STOP` клиент может не прислать `ABORT` (см. предсказание) — сессия доводится серверным
  тиком; это осознанное поведение 26.2. (4) Урона durability у разрешённых ванильных инструментов при
  модовом commit нет (вызов `mineBlock` не выполняется) — вне рамок 1.3. (5) Runtime-проверка миксина в
  headless smoke не гарантируется (нет игрока) — нужна интерактивная приёмка.

## Этап 1.3 — hotfix клиентского предсказания (полностью)

Запрошен явно. Закрывает runtime-нюанс: серверный таймер уже отменяет прогресс/дроп, но **клиент**
до этого успевал вести собственное предсказание (трещины, ложное удаление, исходящие `START`).

- **Файлы (client-only, common не тронут):**
  * `src/client/java/com/whitefog/client/mixin/MultiPlayerGameModeMixin.java` — новые `@Inject` в
    `startDestroyBlock(BlockPos, Direction)Z` и `continueDestroyBlock(BlockPos, Direction)Z`
    (`at = HEAD`, `cancellable = true`). Если общее решение не `ALLOW` — `cir.setReturnValue(false)`:
    ванильный метод не выполняется целиком → нет прогресса/звука/частиц, нет
    `destroyBlockProgress`/`addBreakingBlockEffect`, нет свинга, нет исходящих `START_DESTROY_BLOCK`.
  * `src/client/resources/white_fog.client.mixins.json` — конфиг `package com.whitefog.client.mixin`,
    секция `"client": ["MultiPlayerGameModeMixin"]`, `compatibilityLevel JAVA_25`.
  * `src/main/resources/fabric.mod.json` — конфиг добавлен в `"mixins"` как объект
    `{ "config": "white_fog.client.mixins.json", "environment": "client" }` (формат шаблона
    Fabric для MC 26.2, локальный шаблон проекта).
  * `src/main/java/com/whitefog/breaking/BlockBreakRules.java` — **безопасное обобщение**
    `classify(ServerLevel,…)`/`isFilled(ServerLevel,…)` → `classify(Level,…)`/`isFilled(Level,…)`:
    одна и та же классификация на клиенте и сервере, серверная семантика не изменилась
    (`ServerLevel extends Level`), ссылок на `net.minecraft.client.*` в common нет.
  * `src/client/java/com/whitefog/client/WhiteFogClient.java` — dev-only bounded self-check
    (`FabricLoader.isDevelopmentEnvironment()`): принудительно загружает целевой класс и пишет
    `White Fog: client initializer ready (stage 1.3 hotfix), MultiPlayerGameModeMixin applied={}`.
    Нужен потому, что `MultiPlayerGameMode` создаётся только при входе в мир (`ClientPacketListener`),
    и headless-запуск до главного меню его сам не загружает.
- **Правила.** Решение ровно то же, что на сервере: `BlockBreakRules.classify(level, pos, state)` +
  `BlockBreakRules.classifyTool(player.getMainHandItem())` + `BlockBreakPolicy.evaluateStart(...)`.
  `UNCLASSIFIED` → `evaluateStart` возвращает `ALLOW` → миксин **не** вмешивается (обычная ваниль).
  Null-safe: нет `minecraft`/`player`/`level`/`pos` или чанк не загружен → не вмешиваемся.
- **API evidence (javap по реальным 26.2 clientonly + common deobf jar):**
  `net.minecraft.client.multiplayer.MultiPlayerGameMode`:
  `public boolean startDestroyBlock(BlockPos, Direction)`, `public boolean continueDestroyBlock(BlockPos, Direction)`,
  private-поле `private final Minecraft minecraft` (для `@Shadow @Final`), приватные
  `destroyProgress/destroyTicks/destroyDelay/isDestroying/destroyBlockPos`. `Minecraft`:
  `public LocalPlayer player`, `public ClientLevel level`, `public MultiPlayerGameMode gameMode`.
  `Minecraft#continueAttack` вызывает `continueDestroyBlock(...)` и лишь при `true` делает
  `addBreakingBlockEffect` + `swing`; `Minecraft#startAttack` вызывает `startDestroyBlock(...)` и
  при `true` делает `swing` — поэтому `setReturnValue(false)` глушит и трещины, и свинг.
  `ClientLevel` входит в `Level`; `BlockState#getDestroySpeed(BlockGetter, BlockPos)` и
  `Level#getBlockEntity/isLoaded` доступны на клиенте.
- **References (прочитано, адаптировано — не скопировано):**
  * `Wynntils/Wynntils` — `common/src/main/java/com/wynntils/mc/mixin/MultiPlayerGameModeMixin.java`
    (точный паттерн `@Inject(... at = HEAD, cancellable = true)` → `cir.setReturnValue(false); cir.cancel();`
    для обоих методов).
  * `Zergatul/cheatutils` (`26.2`) — `common/java/com/zergatul/cheatutils/mixins/common/MixinMultiPlayerGameMode.java`
    (`@Inject(at = HEAD, method = "startDestroyBlock"/"continueDestroyBlock")`, `@Shadow @Final private Minecraft minecraft`).
  * `3arthqu4ke/phobot` — `mixins/network/MixinMultiPlayerGameMode.java` (та же пара инъекций, `setReturnValue(false)`).
- **Проверки (эта сессия).**
  * `scripts\build.bat` → `BUILD SUCCESSFUL`, `status=SUCCESS` (`logs/build_20261007_215605.txt`).
    В `build/libs/white_fog-0.1.0.jar` присутствуют `com/whitefog/client/mixin/MultiPlayerGameModeMixin.class`
    и `white_fog.client.mixins.json`.
  * `scripts\client_smoke.bat` → `status=SUCCESS` (`logs/client_smoke_20261007_215637.txt`,
    `logs/client_smoke_20261007_215637.result`); доказательство — строка
    `[..] (white_fog) White Fog: client initializer ready (stage 1.3 hotfix), MultiPlayerGameModeMixin applied=true`
    (client init + миксин применён). Окно не остаётся, завершено только своё PID-дерево.
  * `scripts\server_smoke.bat` → `status=SUCCESS` (`logs/server_smoke_20261007_215719.txt`).
  * `tests\break_timer\run_break_timer_selftest.bat` → `passed=82 failed=0`, `SELFTEST status=SUCCESS`
    (`logs/break_timer_selftest_20261007_215755.txt`).
- **Ограничения/последствия.**
  1. **Action-bar подсказки при клиентском отказе не было → ИСПРАВЛЕНО regression fix'ом** (см. раздел
     «Этап 1.3 — regression fix»): теперь клиент показывает её локально, т.к. `START` для `DENY_*`
     не отправляется и сервер о запросе не знает. Если сервер сам начал сессию (блок был разрешён и стал
     запрещён — смена инструмента/наполнение печи), сервер всё равно шлёт `ClientboundBlockUpdatePacket`.
  2. Клиентская классификация опирается на синхронизированное состояние: для **печи с предметами,
     но без `LIT`** содержимое на клиенте не синхронизировано, поэтому клиент может увидеть
     `STATION_HARD` вместо `STATION_FILLED`. Для руки/ванильных инструментов решение всё равно
     `DENY_*` (совпадает); расхождение теоретически возможно только с модовым кайлом (появится на
     этапе 6.4), сервер в любом случае авторитетен.
  3. Headless client smoke доказывает **инициализацию и применение миксина**, но НЕ геймплей —
     интерактивная приёмка архитектора остаётся.

## Этап 1.3 — regression fix клиентского предсказания (полностью)

Запрошен явно по жалобе пользователя. Закрывает два дефекта первого hotfix'а (см. раздел
«Этап 1.3 — hotfix»): быстрый ванильный разлом разрешённых блоков с откатом-циклом и отсутствие
подсказки «Слишком крепко — нужен инструмент» при запрещённом блоке.

### Причина регрессии
1. Первый hotfix отменял ванильное предсказание только при `decision != ALLOW`. Для `ALLOW`
   (например земля рукой) ванильный `continueDestroyBlock` продолжал вести локальный прогресс,
   локально удалял блок и слал `STOP_DESTROY_BLOCK`. Серверный таймер коммитил лишь позже, сервер
   откатывал блок (`ClientboundBlockUpdatePacket`) — цикл «сломал локально → откатилось» повторялся.
2. Для `DENY_*` клиент подавлял `START` полностью, поэтому `START_DESTROY_BLOCK` не доходил до
   сервера, `BreakTimerService.onStart` не вызывался и сервер не показывал action-bar подсказку.

### Решение
- **`src/client/java/com/whitefog/client/mixin/MultiPlayerGameModeMixin.java` переписан.** На
  `startDestroyBlock` и `continueDestroyBlock` (`@Inject at HEAD, cancellable = true`) ваниль
  подавляется для ВСЕХ контролируемых категорий (`BlockBreakRules.classify(level,pos,state) != UNCLASSIFIED`),
  т.е. и для `DENY_*`, и для `ALLOW`. `UNCLASSIFIED` не трогается.
- **`ALLOW` — ручной клиент→сервер `START` без ванильного prediction.** Тень ванильного приватного
  `startPrediction(ClientLevel, PredictiveAction)` (паттерн Fabric API, javap-проверен) и отправка
  ровно одного `new ServerboundPlayerActionPacket(START_DESTROY_BLOCK, pos, dir, sequence)`; локальное
  состояние блока при этом НЕ меняется, поэтому нет sweep'ов/трещин/свинга/удаления. Сессия
  запоминается (`pos` + направление + копия инструмента). Серверный таймер доводит разрушение сам,
  прогресс-трещины приходят пакетами `ClientboundBlockDestructionPacket`.
  - При смене цели или инструмента клиент шлёт `ABORT` старой сессии и `START` новой (сервер в
    `BreakTimerService.onStart` при невалидной существующей сессии новую НЕ создаёт — поэтому нужен ABORT).
  - При отпускании ЛКМ/потере фокуса `stopDestroyBlock` (наш `@Inject at HEAD`) шлёт `ABORT` вручную:
    ванильный `stopDestroyBlock` шлёт его только если `isDestroying`, а для контролируемых блоков
    ванильное состояние не создаётся.
- **`DENY_NO_TOOL`/`DENY_HOT`/`DENY_FILLED` — локальная подсказка.** `START` не отправляется,
  сообщение `BreakTimerService.MESSAGE_TOO_HARD` («Слишком крепко — нужен инструмент», единый
  источник, серверный текст не менялся) показывается через `LocalPlayer#sendOverlayMessage(Component)`
  (javap-проверено: вызывает `ChatListener.handleOverlay` = action bar) с cooldown
  `WhiteFogConfig.BREAK_REFUSE_MESSAGE_COOLDOWN_TICKS` (20) тиков **на причину** (ключ —
  `BlockBreakPolicy.Decision`, `Entity#tickCount` как клиентские тики). C2S-пакет только ради текста
  не отправляется.
- **`src/client/java/com/whitefog/client/WhiteFogClient.java`** — dev-only self-check теперь ищет
  ВСЕ три новых обработчика (`whiteFog$onStartDestroyBlock`, `whiteFog$onContinueDestroyBlock`,
  `whiteFog$onStopDestroyBlock`); маркер `MultiPlayerGameModeMixin applied={}` сохранён для smoke.
- **`tests/break_timer`** — добавлен чистый `ClientGate` (модель клиентского гейта) и сценарии 23–26
  (`passed=102 failed=0`). Это по-прежнему logic-only, не runtime-proof.

### API evidence (javap по 26.2 deobf jar)
- `MultiPlayerGameMode`: `startDestroyBlock(BlockPos,Direction)Z`, `continueDestroyBlock(BlockPos,Direction)Z`,
  `stopDestroyBlock()V`, private `startPrediction(ClientLevel, PredictiveAction)`, private-final
  `Minecraft minecraft`, `ClientPacketListener connection`.
- `ServerboundPlayerActionPacket(Action, BlockPos, Direction, int)`; 3-арг. конструктор = sequence 0
  (так ваниль шлёт `ABORT` из `stopDestroyBlock`). `ServerGamePacketListenerImpl#handlePlayerAction`
  для START/STOP/ABORT вызывает `handleBlockBreakAction(...)` и затем безусловно `ackBlockChangesUpTo(seq)`.
- `LocalPlayer#sendOverlayMessage(Component)` (action bar), `Entity#tickCount` (public int).

### References (прочитано, адаптировано — не скопировано)
- `FabricMC/fabric-api` (`26.2`, `fabric-events-interaction-v0`) — `.../client/MultiPlayerGameModeMixin.java`:
  `@Shadow @Final private ClientPacketListener connection` + `@Shadow protected abstract void startPrediction(ClientLevel, PredictiveAction)`
  и ручная отправка `ServerboundPlayerActionPacket(START_DESTROY_BLOCK, pos, direction, id)`.
- `MeteorDevelopment/meteor-client` — тот же `startPrediction(level, sequence -> new ServerboundPlayerActionPacket(...))`.
- `Zergatul/cheatutils` (`26.2`) — `modules/hacks/AreaMine.java`, `BedrockBreaker.java` (работа с prediction/sequence).
- `Wynntils/Wynntils` — `@Inject(startDestroyBlock/continueDestroyBlock at HEAD, cancellable) → setReturnValue(false)`.

### Проверки (эта сессия)
- `scripts\build.bat` → `BUILD SUCCESSFUL` (`logs/build_20261007_221853.txt`); в jar —
  `com/whitefog/client/mixin/MultiPlayerGameModeMixin.class` с тремя обработчиками.
- `scripts\client_smoke.bat` → `status=SUCCESS` (`logs/client_smoke_20261007_221911.txt`), строка
  `White Fog: client initializer ready (stage 1.3 regression fix), MultiPlayerGameModeMixin applied=true`.
- `scripts\server_smoke.bat` → `status=SUCCESS` (`logs/server_smoke_20261007_221939.txt`).
- `tests\break_timer\run_break_timer_selftest.bat` → `passed=102 failed=0`, `SELFTEST status=SUCCESS`
  (`logs/break_timer_selftest_20261007_221905.txt`).
- Разделение клиент/сервер: `common` по-прежнему без `net/minecraft/client.*` (проверено по компилятам).

### Известные ограничения
1. Headless client smoke доказывает инициализацию и применение миксина, но НЕ геймплей — нужна
   интерактивная приёмка (земля/листва рукой медленно с серверными трещинами; камень/бревно/печь
   с подсказкой; отпускание ЛКМ до срока отменяет; смена инструмента/цели пересоздаёт сессию).
2. Для наполненной печи без `LIT` содержимое на клиенте не синхронизировано: клиент может видеть
   `STATION_HARD`, сервер — `STATION_FILLED`. Оба решения — `DENY_*` с одной и той же подсказкой;
   расхождение важно только для модового кайла (этап 6.4). Сервер всегда авторитетен.
3. Клиент не шлёт `STOP`; сессию завершает серверный таймер, а `ABORT` — только на отпускание/смену.
   Если клиент по какой-то причине не пришлёт `ABORT` (жёсткий выход), сессию снимет серверная
   валидация/`DISCONNECT`/`clearSession` — сервер не остаётся с «вечной» сессией.

## Этап 1.3 — swing fix визуального отклика ALLOW (полностью)

Запрошен явно после подтверждения пользователем: подсказка `DENY` работает, серверное медленное
разрушение `ALLOW` идёт без rollback, но при зажатой ЛКМ по разрешённым блокам **нет анимации удара**.

### Причина
- В 26.2 `Minecraft#continueAttack(boolean)` вызывает `MultiPlayerGameMode#continueDestroyBlock(pos, dir)` и
  выполняет `ClientLevel#addBreakingBlockEffect(pos, dir)` + `LocalPlayer#swing(MAIN_HAND)` **только если
  возврат `true`** (при `false` — немедленный `return`). Regression fix гасил `continueDestroyBlock`
  (`setReturnValue(false)`), поэтому удержание ЛКМ по `ALLOW` не давало ни свинга, ни частиц.
- `Minecraft#startAttack()` значение `startDestroyBlock` **сбрасывает через `pop`** и в хвосте безусловно
  делает `player.swing(MAIN_HAND)` — поэтому первый клик анимировался, а удержание — нет.

### Решение (минимальное, без возврата ванильного тела)
- **`src/client/java/com/whitefog/client/mixin/MultiPlayerGameModeMixin.java`** — на `continueDestroyBlock`
  (`@Inject at HEAD, cancellable`) для контролируемого `ALLOW` теперь `cir.setReturnValue(true)`; для
  `DENY_*` — `false`. Тело метода по-прежнему не выполняется (cancel), поэтому ванильного прогресса,
  локального удаления и пакетов `START`/`STOP` нет. `startDestroyBlock` остаётся `false` (значение
  игнорируется вызывающим) — ванильное тело не запускается, визуал даёт ветка `continueDestroyBlock`.
- **Что именно активирует `true` (javap-доказано):** ровно `ClientLevel#addBreakingBlockEffect(pos, dir)`
  (только `TerrainParticle`, блок не меняется) и `LocalPlayer#swing(MAIN_HAND)` (локальная анимация +
  ванильный `ServerboundSwingPacket`; сервер `ServerGamePacketListenerImpl#handleAnimate` →
  `resetLastActionTime()` + `swing(hand)`, без геймплейных эффектов). Это стандартная ванильная cadence
  майнинга, а не `START`/`STOP` и не модовые пакеты.
- Сервер и таймер (`BreakTimerService`) **не менялись**. Визуальный crack от сервера
  (`ClientboundBlockDestructionPacket` → `ClientPacketListener#handleBlockDestruction` →
  `ClientLevel#destroyBlockProgress`) от нашего миксина не зависит и не подавлен.

### API evidence (javap, clientonly/common deobf 26.2)
- `Minecraft#continueAttack(boolean)`: блок → `gameMode.continueDestroyBlock(pos, dir)`; по результату
  `ifeq`; при `true` → `level.addBreakingBlockEffect(pos, dir)` + `player.swing(MAIN_HAND)`.
- `Minecraft#startAttack()`: `gameMode.startDestroyBlock(...)` затем `pop` (значение отброшено);
  хвост — безусловный `player.swing(MAIN_HAND)`. `Minecraft#handleKeybinds()`: `startAttack()` на
  `keyAttack.consumeClick()`, `continueAttack(...)` каждый тик при зажатой ЛКМ.
- `ClientLevel#addBreakingBlockEffect(BlockPos, Direction)` — создаёт `TerrainParticle` + `ParticleEngine.add`;
  других эффектов нет.
- `LocalPlayer#swing(InteractionHand)` = `super.swing(hand)` + `connection.send(ServerboundSwingPacket)`.
- `ServerGamePacketListenerImpl#handleAnimate(ServerboundSwingPacket)` = `resetLastActionTime()` + `player.swing(hand)`.
- `ClientPacketListener#handleBlockDestruction` → `ClientLevel#destroyBlockProgress` (карта `destroyingBlocks`).
- Единственные вызовы `startDestroyBlock`/`continueDestroyBlock` в ванильном клиенте — из `Minecraft`
  (проверено байтовым поиском по всем `*.class` clientonly jar). Fabric API `fabric-events-interaction-v0`
  инъектится по `@At(value="INVOKE", target="LocalPlayer;getAbilities()")` **внутри тела**
  `startDestroyBlock`/`continueDestroyBlock` + `destroyBlock`, поэтому наш HEAD-cancel их не запускает
  (так было и до swing fix'а).

### Sandbox / проверки (эта сессия)
- **`tests/break_timer`**: `ClientGate` расширен (`continueReturnsTrue(...)`, `visualContinueTicks()`),
  добавлен сценарий 27 «ALLOW 40/40 визуальных continue-тиков, DENY — 0/0, UNCLASSIFIED — ваниль»,
  сценарии 23/24/26 дополнены. `tests\break_timer\run_break_timer_selftest.bat` → `passed=118 failed=0`,
  `SELFTEST status=SUCCESS` (`logs/break_timer_selftest_20261007_223354.txt`, `.result` = `status=SUCCESS`).
  Это logic-only, **НЕ runtime-proof**.
- `scripts\build.bat` → `BUILD SUCCESSFUL`, `status=SUCCESS` (`logs/build_20261007_223929.txt`); в jar —
  `com/whitefog/client/mixin/MultiPlayerGameModeMixin.class`; javap компилята: хендлер
  `continueDestroyBlock` для `ALLOW` делает `Boolean.valueOf(true)`/`setReturnValue`, для `DENY_*` — `false`.
- `scripts\client_smoke.bat` → `status=SUCCESS` (`logs/client_smoke_20261007_223941.txt`,
  `.result` = `status=SUCCESS`), строка `White Fog: client initializer ready (stage 1.3 regression fix),
  MultiPlayerGameModeMixin applied=true`.
- `scripts\server_smoke.bat` → `status=SUCCESS` (`logs/server_smoke_20261007_223512.txt`),
  `WHITEFOG_CRAFTING_SELFTEST ... status=SUCCESS`, `White Fog: block-break rules registered ...`.

### Известные ограничения / последствия swing fix'а
1. Headless client smoke доказывает только инициализацию клиента и применение миксина, НЕ геймплей —
   нужна интерактивная приёмка (анимация удара при удержании ЛКМ по земле/листве/станции, частицы,
   ванильная cadence).
2. `true` для `ALLOW` приводит к ванильному `ServerboundSwingPacket` каждый тик, пока ЛКМ зажата — это
   ровно ванильное поведение майнинга (сервер рассылает `ClientboundAnimatePacket` другим игрокам и
   обновляет `lastActionTime`), а не модовые mining-пакеты. Альтернатива «локальный свинг без пакета»
   (`LivingEntity.swing(hand,false)`) сознательно не выбрана: дублирует логику вызывающего и скрывает
   свинг от других игроков.
3. Первый клик по `DENY_*` всё равно даёт одиночный ванильный свинг: его делает хвост `Minecraft#startAttack`
   безусловно, мод его не подавляет (вне рамок задачи; удержание — без свинга). `UNCLASSIFIED` — ваниль.

## Этап 1.3 — референсы, API evidence и sandbox (детали реализации)

**Реализация уже применена в `src/` (см. раздел «Этап 1.3 (полностью)» выше).** Ниже — исходное
исследование (API evidence, референсы, sandbox), сохранённое как hand-off.

### Как должно работать (по ROADMAP_STEPS.md, без изменений src/)
- Перехват **трёх** действий серверного разрушения в `ServerPlayerGameMode`:
  `START_DESTROY_BLOCK` / `STOP_DESTROY_BLOCK` / `ABORT_DESTROY_BLOCK`.
- Сервер держит на игрока сессию `{pos, dimension, itemStack(+components), startTick, requiredTicks, startState}`;
  **клиентский progress bar не является доказательством завершения** — коммит делает сервер, когда
  прошёл `requiredTicks` (per-tick) или когда пришёл `STOP` уже после срока.
- Валидация сессии на каждом tick/stop: чанк загружен, измерение совпадает, **стек+компоненты не сменились**,
  блок тот же, игрок в зоне `isWithinBlockInteractionRange`, станция не стала горячей → иначе abort.
- Коммит ровно один: повторная проверка «блок уже воздух / уже закоммичен» → без второго изменения и дропа.
- Политика: мягкие блоки рукой (медленно), твёрдые — только модовым кайлом; ванильные кирка/топор
  не берут камень/брёвна; деревянные станции рукой/топором; печь/наковальня/котёл — только кайлом;
  горячая станция — отказ; листва/трава рукой без дропа; станции дают ровно 1 предмет.

### API evidence (javap по реальным 26.2 deobf / Fabric API jar)
Deobf: `%USERPROFILE%\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common-deobf\26.2\minecraft-common-deobf-26.2.jar`.
Fabric API: `...\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.161.0+26.2\...` (внутри — `fabric-events-interaction-v0-5.2.8+515ac5339e`).
- `net.minecraft.server.level.ServerPlayerGameMode`:
  `public void handleBlockBreakAction(BlockPos, ServerboundPlayerActionPacket$Action, Direction, int, int)`;
  `public boolean destroyBlock(BlockPos)`; `public void destroyAndAck(BlockPos, int, String)`;
  private-поля для `@Shadow`: `destroyProgressStart:int`, `destroyPos:BlockPos`, `isDestroyingBlock:boolean`, `gameTicks:int`.
- `ServerboundPlayerActionPacket$Action` = `START_DESTROY_BLOCK` / `ABORT_DESTROY_BLOCK` / `STOP_DESTROY_BLOCK` (+ DROP_*, RELEASE_USE_ITEM, SWAP_ITEM_WITH_OFFHAND, STAB).
- `ServerPlayer`: `public final ServerPlayerGameMode gameMode`; `level()`, `getMainHandItem()`, `blockPosition()`,
  `sendSystemMessage(Component, boolean)`; `Player#isWithinBlockInteractionRange(BlockPos, double)`, `blockActionRestricted(Level, BlockPos, GameType)`, `hasCorrectToolForDrops(BlockState)`.
- `BlockBehaviour$BlockStateBase`: `getDestroySpeed`, `getDestroyProgress`, `requiresCorrectToolForDrops`, `hasBlockEntity`, `isAir`, `getCloneItemStack`, `getDrops`; унаследованный от `TypedInstance` `is(TagKey<Block>)`.
- `net.minecraft.core.TypedInstance`: default `is(TagKey<T>)` (поэтому `state.is(BlockTags.LEAVES)` валиден).
  **Важно:** у `ItemStack` в 26.2 **нет** `is(TagKey)` — только `is(Predicate<Holder<Item>>)`; тег предмета проверяется как
  `stack.typeHolder().is(ItemTags.PICKAXES)` (`ItemStack#typeHolder():Holder<Item>`, `Holder#is(TagKey)`). Не выдумывать старый `stack.is(ItemTags.X)`.
- Станции: `AbstractFurnaceBlock.LIT:BooleanProperty`, `CampfireBlock.isLitCampfire(BlockState)`,
  `AbstractCauldronBlock.isFull(BlockState)`, `LayeredCauldronBlock.LEVEL`; `Blocks.CRAFTING_TABLE/LOOM/FURNACE/CAULDRON/WATER_CAULDRON/ANVIL/LANTERN`.
- Содержимое: `net.minecraft.world.Container` (`getContainerSize/getItem/removeItem/isEmpty`),
  `Containers.dropContents(Level, BlockPos, Container)`; дроп: `Block.getDrops(...)`, `Block.dropResources(...)`, `Block.popResource(Level, BlockPos, ItemStack)`.
- Мир: `Level#setBlock/removeBlock/destroyBlock/removeBlockEntity/getBlockEntity/isInWorldBounds/mayInteract`;
  сеть: `ClientboundBlockDestructionPacket(int,int?..)` → ctor `(int id, BlockPos, int progress)`, `ClientboundBlockUpdatePacket(BlockGetter, BlockPos)`.
- Fabric hooks: `PlayerBlockBreakEvents.BEFORE` срабатывает **внутри `destroyBlock`** (после прогресса),
  `AttackBlockCallback.EVENT` — на `START` (mixin `fabric-events-interaction-v0` `ServerPlayerGameModeMixin`, 26.3).

### Reference sources (прочитано, адаптировать — не копировать)
- **Patbox/polymer `dev/26.2`** — `polymer-core/src/main/java/eu/pb4/polymer/core/mixin/block/ServerPlayerGameModeMixin.java`:
  серверный mining (`@Inject handleBlockBreakAction HEAD`, `incrementDestroyProgress`, `destroyAndAck(pos, seq, "destroyed")`,
  `ClientboundBlockDestructionPacket(-1,pos,k)`, `ClientboundBlockUpdatePacket`). Ключевой 26.2-образец.
- **FabricMC/fabric-api** — `fabric-events-interaction-v0/.../mixin/event/interaction/ServerPlayerGameModeMixin.java` (branch 26.3):
  точные точки `PlayerBlockBreakEvents.BEFORE/AFTER/CANCELED` и `AttackBlockCallback`; проверка `isWithinBlockInteractionRange(pos, 1.0)`.
- **gnembon/fabric-carpet** — `src/main/java/carpet/mixins/ServerGamePacketListenerImpl_interactionUpdatesMixin.java`:
  `handlePlayerAction` → `ServerPlayerGameMode#handleBlockBreakAction(...)` (порядок вызова).
- **Tschipp/CarryOn `26.2`** — `Common/.../carry/PickupHandler.java`: снятие блока со станции — проверка дистанции
  (`BLOCK_INTERACTION_RANGE`), сохранение BlockEntity (`saveWithId`), `removeBlockEntity` + `removeBlock` (без дублей),
  reject double-блоков (`DoorBlock.HALF`), `getDestroySpeed==-1` (неломаемое), SPECTATOR/ADVENTURE.
- **Dwinovo/minecraft-numen `1.21.1`** — `numen/common/.../act/BlockDigger.java`: полный lifecycle
  begin(`START`)/tick(`getDestroyProgress`+crack)/finish(`STOP`→сервер ломает)/interrupted(`ABORT`), breaker id −1.
- Концепции «рукой нельзя твёрдое»: **alcatrazEscapee/No Tree Punching** (Forge, 1.12–1.16) — приём/идея, код не переносился.

### Sandbox: файлы / команда / raw logs
- Файлы: `tests/break_timer/src/com/whitefog/tests/breaktimer/*.java` (чистая логика, без Minecraft:
  `BreakTimerService`, `RoadmapPolicy`, `SimWorld`, `SelfTest` и модели), `tests/break_timer/README.md`,
  `tests/break_timer/run_break_timer_selftest.bat`. Основной `build.gradle` и `src/` не менялись;
  вывод компиляции идёт в `tests/break_timer/build` (в `.gitignore` через `build/`).
- Команда (foreground, само-завершение; внутренний watchdog 20 c, exit 124 = TIMEOUT):
  ```bat
  tests\break_timer\run_break_timer_selftest.bat
  ```
- Raw logs (эта сессия): `logs/break_timer_selftest_20261007_204110.txt` → `passed=67 failed=0 ... SELFTEST status=SUCCESS`;
  `logs/break_timer_selftest_20261007_204110.result` → `status=SUCCESS`.
- Покрыто 20 сценариев: START/STOP/ABORT, преждевременный STOP, смена инструмента (в т.ч. компоненты),
  измерение/чанк/дистанция, два игрока на 1 блок (1 commit/1 дроп), white-box guard «ровно один раз»,
  горячая станция, печь только кайлом, листва рукой без дропа, ванильные кирка/топор запрещены, кулак по твёрдому,
  наполненный котёл, creative-флаг, STOP без START, повторный STOP, cooldown подсказки.
- **Это НЕ runtime-proof**: миксины, пакеты и реальные `BlockState`/`ItemStack` sandbox не проверяет.

### Gaps / вопросы к пользователю (РЕШЕНО архитектором — см. «Этап 1.3 (полностью)»)
> Все пункты ниже закрыты утверждёнными правилами. Список оставлен как история исследования.
1. **Модовый кайло:** он уже существует/планируется? Если да — точный item id, материал/прочность/рецепт; если нет — этап 1.3
   должен использовать ванильный инструмент в роли «кайла», и какой именно? (Сейчас в sandbox плейсхолдер `white_fog:kaido_placeholder`.)
2. **Точные длительности** (тики/секунды): рукой по траве/листве/грунту; кайлом по твёрдым блокам и станциям.
3. **Инструменты по твёрдым категориям:** только кайло — или для части (доски/стекло/кирпич/металл) разрешены ванильные кирка/топор?
4. **Листва/трава:** рукой дроп 0; а каким инструментом и что дропает (ножницы/топор/лопата)?
5. **Наполненные станции** (котёл с водой/лавой, печь с содержимым): отказ до опустошения **или** выдать блок + высыпать содержимое?
   (Риск дублирования.)
6. **«Горячая» станция:** достаточно ли blockstate LIT (печь/костёр), нужен ли отдельный кулдаун «остывания» и какой?
7. **Cooldown и текст** подсказки: показывать ли «Слишком крепко — нужен инструмент» и при запрете ванильного инструмента; точный cooldown тиков.
8. **Creative rules:** разрешать ли креативу мгновенно ломать всё (bypass правил и таймера)? Сейчас — флаг.
9. **Дальность аборта:** использовать ванильный `isWithinBlockInteractionRange(pos, 1.0)` или своё значение?
10. **Теги для классификации:** подтвердить leaf=`#minecraft:leaves`, logs=`#minecraft:logs`, stone/ore — какие теги (`MINEABLE_WITH_PICKAXE` / `#c:ores`?),
    и что считать «травой» (`short_grass`/`tall_grass` — растение, а `grass_block` — грунт).

### Предлагаемый перечень src-файлов для будущего применения (после ответов и разрешения)
- `src/main/java/com/whitefog/breaking/BlockBreakRules.java` — классификация блоков/инструментов (теги 26.2).
- `src/main/java/com/whitefog/breaking/BlockBreakPolicy.java` — длительности/cooldown/дропы/станции (значения из `WhiteFogConfig`).
- `src/main/java/com/whitefog/breaking/BreakSession.java` — запись сессии.
- `src/main/java/com/whitefog/breaking/BreakTimerService.java` — per-player сессии, tick-валидация, commit/дедуп/диагностика (dev).
- `src/main/java/com/whitefog/breaking/StationRemoval.java` — ровно 1 предмет блока, содержимое, hot/full-проверки.
- `src/main/java/com/whitefog/mixin/ServerPlayerGameModeMixin.java` — `@Inject handleBlockBreakAction HEAD, cancellable`:
  START/STOP/ABORT → `BreakTimerService`; `AttackBlockCallback` для совместимости; информирование клиента пакетами.
- `src/main/java/com/whitefog/WhiteFogConfig.java` — новые tunables (длительности, cooldown, id кайла) с русскими комментариями.
- `WhiteFog.java` / `WhiteFogServer.java` — регистрация `BlockBreakRules.register()` (без новых payload-типов).
- Проверки: `gradlew.bat build --no-daemon --console=plain` + `scripts\server_smoke.bat`, затем интерактивная приёмка.

## TODO
- **Интерактивная приёмка этапов 1.1/1.2/1.3 — ПОДТВЕРЖДЕНА пользователем** (версия работает «идеально»,
  версия `0.1.0` закреплена). Явно НЕ заявляются как проверенные (в подтверждение не входили и отдельно не
  проверялись): смерть/`copyOnDeath`, повторный вход после смерти, смена измерения, выгрузка/загрузка чанка,
  второй игрок, spawn-protection/adventure. Headless-smoke проверяет только старт dedicated server +
  инициализацию мода.
- **Интерактивная приёмка крафта (архитектор, вручную):** в инвентаре положить предмет в 2x2 — результата нет,
  показано «Крафтить на бегу нельзя»; собрать верстак стандартным рецептом — результата нет; клик по найденному
  верстаку не открывает vanilla 3x3 и не даёт книгу рецептов; проверить shift-click/drag/креатив и закрытие меню
  без потери предметов.
- **Этап 1.3 — применён в `src/`** (правила и значения утверждены архитектором: длительности, id модовых
  кайл, creative-семантика, hot/filled, cooldown; затем review-фиксы: права/границы/unbreakable при
  START+commit, динамическая наполненность, fail-closed, sandbox-сценарий преждевременного STOP).
  Sandbox обновлён под эти значения (позже дополнен клиентским гейтом и swing fix'ом: `passed=118 failed=0`).
- **Этап 1.3 — интерактивная приёмка ПОДТВЕРЖДЕНА пользователем:** земля/листва рукой (медленно, листва без
  дропа), камень/бревно кулаком и ванильной киркой/топором (отказ + подсказка, без durability), верстак рукой,
  loom топором, furnace рукой (отказ), горящая станция (отказ), наполненный котёл (отказ) — работают по
  описанию. НЕ заявляются как проверенные: досягаемость через стену/вне дистанции, spawn-protection/приключение,
  наполнение печи ВО ВРЕМЯ таймера (содержимое не теряется), смена измерения/выгрузка чанка/два игрока на
  1 блок (блок меняется максимум 1 раз). Добыча модовым кайлом (`white_fog:*_pickaxe`) недоступна до этапа 6.4.
- **Этап 1.3 regression fix — интерактивная приёмка клиентского предсказания ПОДТВЕРЖДЕНА пользователем:**
  (1) при зажатой ЛКМ по **разрешённому** блоку (земля/листва/станция рукой) блок теперь НЕ ломается ванильно
  быстро: прогресс-трещины приходят от сервера, локального удаления нет, цикл «сломало→откатило» исчез;
  отпускание ЛКМ до срока реально отменяет (ABORT); смена инструмента/цели пересоздаёт сессию; (2) при зажатой
  ЛКМ по **запрещённому** блоку (камень/бревно кулаком и ванильной киркой, furnace рукой, горячая станция,
  наполненный котёл) показывается локальная action-bar подсказка «Слишком крепко — нужен инструмент» с
  cooldown (не чаще раза в 20 тиков), трещин/свинга/локального удаления нет; (3) `UNCLASSIFIED` (рельсы,
  кнопки, двери) ломается как в ванили. Headless client smoke доказывает только инициализацию клиента +
  применение миксина (`applied=true`), не геймплей.
- **Этап 1.3 swing fix — интерактивная приёмка анимации ALLOW ПОДТВЕРЖДЕНА пользователем:**
  при зажатой ЛКМ по разрешённому блоку (земля/листва/станция рукой) видна ванильная анимация удара
  рукой и частицы; первый клик анимируется; при `DENY_*` (камень/бревно/furnace/горячая станция)
  удержание НЕ даёт свинга, подсказка «Слишком крепко — нужен инструмент» показана; `UNCLASSIFIED`
  ломается ванильно. Серверные трещины (`ClientboundBlockDestructionPacket`) видны во время серверного
  таймера. Headless smoke доказывает только init + применение миксина.
- Этап 1.x+: фактический расход/восстановление (`fatigue/calories/water/condition`), термоощущение,
  мокрота одежды, дизентерия, сон/работа как механики; реальный HUD по §10 AGENTS.md.
- Возможные C2S-пакеты действий (регистрировать типы в common init до ресиверов).
- Git: по готовности — первый коммит; GitHub-репо лишь после явного подтверждения пользователя.

## Build & run
Все команды — из корня `D:\Minecraft\modhard`. Сборка без демона (`--no-daemon` гарантирует возврат управления).
```bat
:: сборка + UTF-8 лог в logs/build_<stamp>.txt
scripts\build.bat
:: либо напрямую:
gradlew.bat build --no-daemon --console=plain
```
Головастая проверка клиента (запускает процесс, требует ручной остановки; не запускать «голой» командой):
```bat
gradlew.bat runClient --no-daemon --console=plain
```
Bounded dedicated-server smoke (внутренний hard-timeout 360 c). Завершает **только собственное
подтверждённое дерево** по сохранённому PID запуска (`taskkill /PID <rootPid> /T /F`); никаких
sweep-обходов чужих `java`/`cmd` по маске командной строки — игру пользователя или другую сборку
убить нельзя. Очистка и запись результата выполняются в `try/finally` (процесс не остаётся висеть
даже при исключении). Пишет `logs/server_smoke_<stamp>.result` со `status=SUCCESS|TIMEOUT|FAILURE|EXITED`
и возвращает **ненулевой exit code для любого статуса кроме `SUCCESS`**:
```bat
scripts\server_smoke.bat
:: exit 0 только при status=SUCCESS; при TIMEOUT/FAILURE/EXITED — exit 1
```
Bounded client smoke (hotfix/regression fix этапа 1.3; внутренний hard-timeout 180 c; то же безопасное
завершение только своего PID-дерева). Запускает `gradlew runClient --no-daemon --console=plain`, ждёт в
логе dev-маркер client init + результат bounded self-check миксина и убивает процесс (игру не оставляет).
Пишет `logs/client_smoke_<stamp>.result` со `status=SUCCESS|TIMEOUT|FAILURE`:
```bat
scripts\client_smoke.bat
:: exit 0 только при status=SUCCESS
```
Проверка доказательства клиентского миксина в свежем client-smoke-логе (ожидается
`White Fog: client initializer ready (stage 1.3 regression fix), MultiPlayerGameModeMixin applied=true`;
self-check требует наличия ВСЕХ трёх обработчиков `whiteFog$onStartDestroyBlock`/`onContinueDestroyBlock`/`onStopDestroyBlock`):
```powershell
Select-String -Path logs\client_smoke_*.txt -Pattern 'client initializer ready|MultiPlayerGameModeMixin' | Select-Object -Last 3
```
> **Важно:** client smoke доказывает только инициализацию клиента и применение миксина (класс
> `MultiPlayerGameMode` принудительно загружается dev-only self-check'ом, т.к. в ваниле он создаётся
> лишь при входе в мир). Это **НЕ** доказательство геймплея — нужна интерактивная приёмка.
Sandbox self-теста этапа 1.3 (чистая логика, НЕ runtime; foreground, внутренний watchdog 20 c,
пишет UTF-8 лог + `.result` в `logs\`; `exit 124` = TIMEOUT):
```bat
tests\break_timer\run_break_timer_selftest.bat
```
Проверка результата self-теста крафта в свежем smoke-логе (должны быть строки
`removed 1120 vanilla crafting recipe(s)`, `Loaded 465 recipes` и
`WHITEFOG_CRAFTING_SELFTEST ... craftingRecipes=0 craftingTableRecipe=false status=SUCCESS`):
```powershell
Select-String -Path logs\server_smoke_*.txt -Pattern 'WHITEFOG_CRAFTING_SELFTEST|removed .* crafting|Loaded .* recipes' | Select-Object -Last 5
```
Проверка инициализации правил разрушения этапа 1.3 в свежем smoke-логе:
```powershell
Select-String -Path logs\server_smoke_*.txt -Pattern 'block-break rules registered' | Select-Object -Last 2
# ожидается: White Fog: block-break rules registered (stage 1.3, server-authoritative timer)
```
Проверка отсутствия клиентских зависимостей в common (после сборки):
```powershell
Get-ChildItem -Recurse build\classes\java\main -Filter *.class |
  ForEach-Object { Select-String -Path $_.FullName -Pattern 'net/minecraft/client' -SimpleMatch -Encoding Default -ErrorAction SilentlyContinue }
# пусто => common чист
```

## Публикация (гигиена первичной публикации)

Подготовлено для первичного публичного push на GitHub (по явному запросу пользователя). **staging/commit/
создание репозитория/push выполняет архитектор** — здесь они НЕ выполнялись.

- **Удалён stray-файл `fabric.mod.json` в корне.** Это был дескриптор самого Fabric API (id `fabric-api`,
  авторы FabricMC, `jars` с вложенными модулями), случайно попавший в корень (распаковка jar/Fabric API).
  Действующий дескриптор мода — `src/main/resources/fabric.mod.json` (id `white_fog`). На корневой файл не
  ссылался ни `build.gradle` (`processResources.filesMatching` работает только по source set resources),
  ни какой-либо код — доказанный случайный дубликат/шаблон, не часть проекта.
- **`.github/workflows/build.yml` — оставлен.** Стандартный CI Fabric-шаблона: `ubuntu-24.04`, JDK 25
  (microsoft), `./gradlew build`, `upload-artifact build/libs/`; секретов/личных данных не содержит, безопасен
  для публичного репозитория (запускается на push/PR).
- **`.gitignore`** дополнен секциями secrets/credentials, local/personal configs и scratch/reports/temp;
  `logs/`, `build/`, `.gradle/`, `run/` игнорировались и ранее. `tests/break_timer/build/` покрывается `build/`.
- **Личные данные:** в этом файле убран абсолютный путь вида `C:\Users\<user>\...` (заменён на нейтральное
  «локальный шаблон проекта»).
- **Аудит intended publishing paths (содержимое секретов не выводилось):**
  * `logs/`, `build/`, `run/`, `.gradle/`, `tests/break_timer/build/` — сгенерированные/локальные, игнорируются.
  * `gradle/wrapper/gradle-wrapper.jar` + `gradle-wrapper.properties` — легитимный Gradle Wrapper, нужен в репо
    (не артефакт мода); URL указывает на `services.gradle.org`, секретов нет.
  * `scripts/*.ps1|.bat` — пути относительно `$PSScriptRoot`/`%~dp0`, без секретов и личных путей.
  * `.env`, credentials, приватные ключи, личные конфиги — **не обнаружены**; добавлены в `.gitignore` на будущее.
  * Секретов (API-ключи/токены/пароли/приватные ключи) в intended-файлах **не найдено**.

## Decisions
- **Persistent state = Fabric Data Attachment API v1** (`fabric-data-attachment-api-v1` 2.2.19+515ac5339e), а не
  static-поля и не самодельный миксин: официально поддерживает persistence в NBT и `copyOnDeath`.
- **Sync — собственный S2C payload с NBT-снимком**, а не встроенный `syncWith`: явный контроль частоты,
  переиспользование `toNbt/fromNbt`, наглядная демонстрация правильной регистрации типа пакета.
- **Состояние хранится как mutable-объект** с `revision`; вложение перезаписывается редко, синхронизация —
  по ревизии/периоду. Один и тот же экземпляр переносится при `copyOnDeath` (старый игрок уже отброшен Fabric).
- **Debug-команда только в dev** и read-only (`peek`), чтобы не создавать состояние при простом просмотре.
- **`WhiteFogConfig` — Java-конфиг** с русскими комментариями (этап 1.1): менять значения в одном месте;
  при необходимости позже вынесем в JSON-файл.
- **Запрет крафта — серверные предикаты + минимум миксинов (этап 1.2).** Один общий
  `AbstractContainerMenuMixin` отменяет `clicked` для слотов сетки/результата `InventoryMenu` и
  `CraftingMenu` (покрывает все packet-пути, включая клиентское предсказание). Для верстака не
  подменяем GUI и не разрушаем блок: `BlockEvents.USE_WITHOUT_ITEM` возвращает `FAIL`, а ванильный
  `removed()` сам возвращает transient-сетку при закрытии — предметы не теряются и не дублируются.
- **Рецепты удаляются в `RecipeManager#apply`, а не через кастомный рецепт-менеджер.** `@ModifyVariable`
  на аргументе `RecipeMap` гарантирует, что и `getRecipeFor`, и построение дисплеев книги рецептов
  (`finalizeRecipeLoading`) видят набор без `RecipeType.CRAFTING`. `priority = 1500` — чтобы фильтр
  применился раньше Fabric recipe-sync.
- **Побочный эффект:** удаление всех `RecipeType.CRAFTING` отключает и блок Crafter (он использует тот же
  тип рецептов), и инвентарный 2x2 — это соответствует цели «запрет ванильного крафта».
- **Этап 1.3 — один серверный таймер вместо ванильного прогресса.** Перехват `handleBlockBreakAction` на
  `HEAD` с `cancellable`: для контролируемых блоков ванильный прогресс и лут подавлены, коммит делает только
  серверный тик. Это единственный способ сделать «медленное» разрушение независимым от клиентского
  progress bar и от подмены скорости инструмента.
- **Преждевременный `STOP` не отменяет сессию** (см. раздел «Этап 1.3 (полностью)»): в 26.2 клиент шлёт
  `STOP` при своём оптимистичном завершении, а `handlePlayerAction` безусловно подтверждает последовательность.
  Отмена — по `ABORT` и по валидации (блок/инструмент/дистанция/измерение/высота).
- **Твёрдые блоки = `requiresCorrectToolForDrops()` + `#logs`/`#planks` + стекло (`#impermeable`).** Это
  точнее, чем весь `#mineable/pickaxe`: рельсы/кнопки/двери/таблички остаются `UNCLASSIFIED` (ваниль), а
  камень/руды/кирпич/металл/тяжёлые механизмы требуют модовое кайло. Листва/растения — 0 дропа независимо
  от инструмента (скрытый ванильный дроп ножницами/топором запрещён).
- **Станции: отдельная безопасная выдача.** `StationRemoval.commit` берёт `getCloneItemStack` ДО удаления,
  `removeBlockEntity` + `destroyBlock(..., false, ...)` — содержимое не выбрасывается и не дублируется;
  наполненные станции/ёмкости (вода в котле, предметы в печи) и горячие (LIT/костёр) отклоняются до commit.
- **Review-фиксы (после личного ревью архитектора).**
  * Миксин отменяет ванильный `handleBlockBreakAction`, поэтому все его гейты продублированы на сервере:
    «too far» (`isWithinBlockInteractionRange`), «too high» (`pos.y > level.getMaxY()`), загрузка/границы
    (`isLoaded`/`isOutsideBuildHeight`), `mayInteract`, spawn-protection (`isUnderSpawnProtection`),
    `blockActionRestricted` — при START, каждом tick/STOP и повторно в `commit`.
  * Прежний `catch → return false` был fail-open (bypass правил): теперь `failClosed` очищает сессию и
    гасит ванильное действие.
  * Наполненность перепроверяется динамически (не по категории из START), т.к. содержимое печи добавляется
    без смены `BlockState`; `StationRemoval.commit` — последний defensive guard до `removeBlockEntity`.
  * Неразрушимые (`getDestroySpeed < 0`) и `GameMasterBlock` классифицируются как `UNCLASSIFIED`.
- **Hotfix клиентского предсказания (этап 1.3, запрошен явно).** Единственная клиентская точка
  вмешательства — `MultiPlayerGameMode#startDestroyBlock`/`continueDestroyBlock` (`HEAD`, `cancellable`),
  потому что именно они ведут клиентский прогресс и шлют `START_DESTROY_BLOCK`; отмена на `HEAD` не
  даёт ни трещин (`destroyBlockProgress`/`addBreakingBlockEffect`), ни свинга, ни пакетов, ни ложного
  локального удаления. Решение принимает **тот же** общий код (`BlockBreakRules`+`BlockBreakPolicy`),
  для чего `classify`/`isFilled` обобщены с `ServerLevel` до `Level` (без client-ссылок в common).
  `UNCLASSIFIED` → `ALLOW` → ванильное поведение. (Первая версия отменяла только `DENY_*`; недостатки
  закрыты regression fix'ом — см. следующий пункт.)
- **Regression fix клиентского предсказания (этап 1.3).** Подавляем ваниль для ВСЕХ контролируемых
  категорий, а для `ALLOW` вручную шлём серверу ровно один `START` через тень ванильного приватного
  `startPrediction(ClientLevel, PredictiveAction)` (паттерн Fabric API) и `ABORT` при отпускании/смене
  цели/инструмента; для `DENY_*` `START` не шлём, а подсказку показываем локально
  (`LocalPlayer#sendOverlayMessage`) с cooldown 20 тиков на причину. Так серверный таймер стартует и
  доводит разрушение сам, а клиент не ведёт ложный прогресс и не шлёт `STOP`. Текст подсказки — общий
  `BreakTimerService.MESSAGE_TOO_HARD` (единый источник, серверный текст не менялся).
- **Swing fix визуального отклика ALLOW (этап 1.3, запрошен явно).** Минимальная правка: на
  `continueDestroyBlock` (HEAD, cancellable) для `ALLOW` возвращать `true` — вызывающий
  `Minecraft#continueAttack` сам выполняет ванильный визуал (`addBreakingBlockEffect` + `player.swing`,
  ванильная cadence), при этом тело метода пропущено, так что ванильного таймера/прогресса/
  `START`/`STOP` НЕТ. Возврат `true` не даёт побочных геймплейных эффектов (javap: `startAttack`
  значение `startDestroyBlock` игнорирует через `pop`; `continueAttack` использует результат
  `continueDestroyBlock` только для визуала). Альтернативу «ручной локальный свинг» не выбрали:
  она дублирует логику вызывающего и скрывает свинг от других игроков. `DENY_*` остаётся `false`
  (без свинга), `UNCLASSIFIED` — ваниль.

### Reference sources (адаптировано, не скопировано)
- Fabric Docs (официальный пример attachment):
  `fabric-docs/reference/26.1.2/src/main/java/com/example/docs/attachment/ExampleModAttachments.java` —
  шаблон `AttachmentRegistry.create(id, b -> b.initializer(...).persistent(...).copyOnDeath())`.
- Fabric API `fabric-data-attachment-api-v1`: `net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl#transfer`
  (семантика `copyOnDeath` при респавне/конвертации/возврате из End/смене измерения).
- Мод `MoriyaShiine/enchancement` (`common/.../Enchancement.java`) — регистрация S2C-типов в общем инициализаторе;
  мод `Snownee/Jade` — клиентские получатели `ClientPlayNetworking.registerGlobalReceiver`.
- Fabric HUD API: `fabric-rendering-v1` (`HudElementRegistry`/`HudElement`, `GuiGraphicsExtractor`).
- **Этап 1.2:**
  - `Zergatul/cheatutils` — `common/java/com/zergatul/cheatutils/mixins/common/MixinAbstractContainerMenu.java`
    (26.2): паттерн `@Inject(method = "clicked", at = @At("HEAD"))` для перехвата кликов меню.
  - `gnembon/fabric-carpet` — `src/main/java/carpet/mixins/AbstractCraftingMenu_scarpetMixin.java`:
    перехват `AbstractCraftingMenu#handlePlacement` с `RecipeBookMenu.PostPlaceAction.NOTHING` (базис для
    запрета выкладывания рецепта).
  - `FabricMC/fabric-api` — `fabric-recipe-api-v1/.../mixin/recipe/RecipeManagerMixin.java` + `RecipeManagerAccessor.java`:
    точка `apply(RecipeMap, ResourceManager, ProfilerFiller)` и внутреннее поле `RecipeMap recipes`
    (использованы как ориентир для `@ModifyVariable`).
- **Этап 1.3:**
  - `Patbox/polymer` (`dev/26.2`) — `polymer-core/.../mixin/block/ServerPlayerGameModeMixin.java`:
    серверный mining — `@Inject handleBlockBreakAction HEAD`, `incrementDestroyProgress TAIL`,
    `destroyAndAck(pos, seq, "destroyed")`, `ClientboundBlockDestructionPacket(-1,pos,k)`, `ClientboundBlockUpdatePacket`.
  - `FabricMC/fabric-api` — `fabric-events-interaction-v0/.../mixin/event/interaction/ServerPlayerGameModeMixin.java`:
    `PlayerBlockBreakEvents.BEFORE/AFTER/CANCELED` внутри `destroyBlock`, `AttackBlockCallback` на START,
    `isWithinBlockInteractionRange(pos, 1.0)`.
  - `gnembon/fabric-carpet` — `src/main/java/carpet/mixins/ServerGamePacketListenerImpl_interactionUpdatesMixin.java`
    (`handlePlayerAction` → `handleBlockBreakAction`).
  - `Tschipp/CarryOn` (`26.2`) — `Common/.../carry/PickupHandler.java`: снятие станции без дублей —
    `removeBlockEntity` + `removeBlock`, сохранение BlockEntity `saveWithId`, дистанция `BLOCK_INTERACTION_RANGE`.
  - `Dwinovo/minecraft-numen` (`1.21.1`) — `numen/common/.../act/BlockDigger.java`: lifecycle begin/tick/finish/interrupted.
  - **Hotfix (клиентская отмена):** `Wynntils/Wynntils` — `common/src/main/java/com/wynntils/mc/mixin/MultiPlayerGameModeMixin.java`
    (точный паттерн `startDestroyBlock`/`continueDestroyBlock` `@At("HEAD")` + `setReturnValue(false)`);
    `Zergatul/cheatutils` (`26.2`) — `common/java/com/zergatul/cheatutils/mixins/common/MixinMultiPlayerGameMode.java`
    (те же инъекции на 26.2, `@Shadow @Final private Minecraft minecraft`); `3arthqu4ke/phobot` — `mixins/network/MixinMultiPlayerGameMode.java`.
  - **Swing fix (визуальный отклик ALLOW):** `Fabricators-of-Create/Porting-Lib` (`1.21.1`) —
    `modules/base/.../mixin/client/MinecraftMixin.java`: `@WrapOperation(method="continueAttack", at=INVOKE continueDestroyBlock)`
    с явным комментарием «true -> … do swing and crack; false -> … do NOT swing or crack» (наш критерий);
    `Wurst-Imperium/Wurst7` — `hacks/AutoMineHack.java`, `NukerLegitHack.java`:
    `if (im.continueDestroyBlock(pos, side)) { MC.level.addBreakingBlockEffect(pos, side); MC.player.swing(MAIN_HAND); }`
    (точный образец пары «частицы + свинг»); `KiltMC/Kilt` — `MultiPlayerGameModeInject.java`:
    `@Inject(continueDestroyBlock, HEAD, cancellable)` → `cir.setReturnValue(true)`; `Aspw-w/Krs` —
    `MinecraftClientMixin.java` (`swingHandWithoutPacket`) — рассмотренная и отклонённая альтернатива
    «локальный свинг без пакета».

### Проверка API (javap по реальным 26.2 deobf / Fabric API jar)
`AttachmentRegistry`/`AttachmentType`/`AttachmentTarget`; `PayloadTypeRegistry.clientboundPlay`/`serverboundPlay`;
`ServerPlayNetworking`/`ClientPlayNetworking`; `ServerTickEvents.END_SERVER_TICK`; `ServerLifecycleEvents`;
`ServerPlayConnectionEvents.JOIN`; `ServerPlayerEvents.AFTER_RESPAWN`; `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`;
`CommandRegistrationCallback` + `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` (в 26.2 `hasPermission(int)` больше нет —
используется `PermissionSet`); `HudElementRegistry.addLast` + `HudElement.extractRenderState`; `FabricLoader.isDevelopmentEnvironment`.

**Этап 1.2 (javap-проверено):** в 26.2 `ClickType` переименован в `net.minecraft.world.inventory.ContainerInput`;
`AbstractContainerMenu#clicked(int, int, ContainerInput, Player)`; `InventoryMenu`/`CraftingMenu` наследуют
`AbstractCraftingMenu#getResultSlot()`/`getInputGridSlots()`; у `InventoryMenu` **нет** `MenuType` (конструктор
передаёт `null`), у `CraftingMenu` — `MenuType.CRAFTING`; `CraftingMenu#stillValid(Player)`;
`RecipeManager#apply(RecipeMap, ResourceManager, ProfilerFiller)` + `getRecipeManager()`/`getRecipes()`/`byKey(ResourceKey)`
(`ResourceKey#identifier()`, `Registries.RECIPE`); `ServerGamePacketListenerImpl#handlePlaceRecipe`/`handleSetCreativeModeSlot`
(поле `public ServerPlayer player`); `Fabric BlockEvents.USE_WITHOUT_ITEM` (инъекция в `BlockStateBase#useWithoutItem`);
`ServerLifecycleEvents.SERVER_STARTED`/`END_DATA_PACK_RELOAD`; `ServerPlayer#sendSystemMessage(Component, boolean)` (2-й арг —
action bar); `RecipeMap.create(Iterable<RecipeHolder<?>>)`/`values()`; `RecipeHolder#value()`/`Recipe#getType()`.

**Этап 1.3 (javap-проверено):** `ServerPlayerGameMode#handleBlockBreakAction(BlockPos, ServerboundPlayerActionPacket$Action, Direction, int, int)`
и `destroyBlock(BlockPos)`; `Action.{START,STOP,ABORT}_DESTROY_BLOCK`; private-поля `destroyProgressStart`/`destroyPos`/`isDestroyingBlock`/`gameTicks`;
`ServerPlayer.gameMode` (public final), `Player#isWithinBlockInteractionRange(BlockPos, double)`, `Player#blockActionRestricted`, `Player#hasCorrectToolForDrops`;
`BlockBehaviour$BlockStateBase#getDestroySpeed/getDestroyProgress/requiresCorrectToolForDrops/hasBlockEntity/isAir/getCloneItemStack/getDrops`;
`net.minecraft.core.TypedInstance#is(TagKey)` (блоки) — но у `ItemStack` `is(TagKey)` **нет**, только `typeHolder().is(TagKey)` и `is(Predicate<Holder<Item>>)`;
`AbstractFurnaceBlock.LIT`, `CampfireBlock.isLitCampfire`, `AbstractCauldronBlock.isFull`, `LayeredCauldronBlock.LEVEL`;
`Blocks.{CRAFTING_TABLE,LOOM,FURNACE,CAULDRON,WATER_CAULDRON,ANVIL,LANTERN}`; `ItemTags.{PICKAXES,AXES,SHOVELS}`; `BlockTags.{LOGS,PLANKS,LEAVES,ICE,IMPERMEABLE,FLOWERS}`;
`Container`/`Containers.dropContents`; `Level#{setBlock,removeBlock,destroyBlock,removeBlockEntity,getBlockEntity,isLoaded,mayInteract}`;
`Level#destroyBlock(BlockPos, boolean, Entity, int)` (recursionLeft; ваниль передаёт 512), `Level#isLoaded`,
`Level#isOutsideBuildHeight(BlockPos)`, `Level#getMaxY()` (из `LevelHeightAccessor`),
`MinecraftServer#isUnderSpawnProtection(ServerLevel, BlockPos, Player)`,
`GameMasterBlock` (интерфейс-маркер), `Level#getLevelData().getGameTime()`
(в 26.2 `getGameTime()` живёт в `LevelData`, не в `Level`); `Block#{getDrops(..., ItemInstance), popResource, playerWillDestroy}`;
`BlockBehaviour$BlockStateBase#requiresCorrectToolForDrops()` и `getCloneItemStack(LevelReader, BlockPos, boolean)`;
`BuiltInRegistries.ITEM.getKey(Item)` (exact id модовых кайл); `ItemStack#isSameItemSameComponents`;
`ClientboundBlockDestructionPacket(int, BlockPos, int)`/`ClientboundBlockUpdatePacket(BlockPos, BlockState)`/`ClientboundBlockChangedAckPacket(int)`;
`ServerPlayConnectionEvents.DISCONNECT` = `(ServerGamePacketListenerImpl, MinecraftServer)`.
**Клиент (clientonly deobf):** `MultiPlayerGameMode` (`destroyProgress`, `isDestroying`, `startPrediction`,
`startDestroyBlock(BlockPos, Direction)Z`, `continueDestroyBlock(BlockPos, Direction)Z`, private-поле
`private final Minecraft minecraft`), `Minecraft` (`public LocalPlayer player`, `public ClientLevel level`,
`public MultiPlayerGameMode gameMode`; `continueAttack` вызывает `continueDestroyBlock(...)` и лишь при
`true` делает `addBreakingBlockEffect`+`swing`, а `startAttack` при `true` — `swing`);
`ServerboundPlayerActionPacket#getSequence`; `ServerGamePacketListenerImpl#handlePlayerAction` вызывает
`handleBlockBreakAction(...)` и затем **безусловно** `ackBlockChangesUpTo(seq)` — основание для правила
«преждевременный STOP не отменяет сессию».
