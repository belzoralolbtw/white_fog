# tests/item_gui_center — sandbox центрирования GUI-иконок `small_stone` и `flat_stone`

Это **независимый sandbox** для доказательства того, что вычисленный `gui.translation`
предметной модели ставит **центр bounding-box** модели точно в центр слота после
GUI rotation/scale/translation, — до/без изменения `src/`. Проверяются обе модели:
`white_fog:small_stone` (parent `white_fog:block/small_stone_0`) и `white_fog:flat_stone`
(parent `white_fog:block/flat_stone`).

## Что это НЕ доказывает

Это **матричная проверка реальными классами движка**, а не пиксельный рендер.
Тест НЕ рисует клиент и не проверяет визуальную картинку. Финальную визуальную
приёмку делает архитектор в клиенте.

## Почему это не «догадка»

Тест вызывает **настоящий** конвейер Minecraft 26.2 из `minecraft-clientonly-deobf`:

1. `net.minecraft.client.resources.model.cuboid.ItemTransform#apply(boolean, PoseStack$Pose)`
   (единицы и порядок операций: `translate(t) → rotate(rotationXYZ) → scale(s) → translate(-0.5)`);
2. `ItemTransform$Deserializer`: `translation = json * 0.0625`, clamp `[-5, 5]`;
3. `ItemDisplayContext.GUI.leftHand() == false` (javap: `true` только для `*_LEFT_HAND`),
   поэтому `apply` вызывается с `leftHand = false` (без инверсии `tx/ry/rz`).

Дополнительно тест сверяет матрицу из `apply` с ручной JOML-композицией
`Mat4.translate(t).rotate(rotationXYZ).scale(s).translate(-0.5)` — совпадение
подтверждает, что единицы/порядок поняты верно.

## Что проверяется

1. bounding-box модели берётся из реального block-JSON (`from`/`to` всех элементов)
   — `small_stone_0` для `small_stone`, `flat_stone` для `flat_stone`;
2. иконка с **текущим** `gui.translation` проецируется **вне** центра (воспроизведение дефекта);
3. вычисленный `translation = -f(0)` центрирует bbox точно в `(0,0,0)` (в единицах модели);
4. размер (extent) bbox сохраняется (translation не меняет размер), `gui.scale` не менялся
   (`small_stone` 1.25, `flat_stone` 0.625 — унаследованный от `minecraft:block/block`);
5. `ItemTransform.apply ==` ручная JOML-композиция;
6. фактический `gui.translation` в файле совпадает с вычисленным (до фикса — FAIL, после — SUCCESS):
   `small_stone` — `[0.000, 7.036, 4.062]`, `flat_stone` — `[0.000, 3.248, 1.875]`.

## Запуск (foreground, само-завершение)

Из корня проекта:

```
tests\item_gui_center\run_gui_icon_center_selftest.bat
```

`.bat` компилирует только `tests/item_gui_center/src` во временный `tests/item_gui_center/build`
(в `.gitignore` через `build/`), запускает `GuiIconCenterSelfTest` в foreground и пишет UTF-8 лог
в `logs\item_gui_center_selftest_<stamp>.txt` + `status=SUCCESS|TIMEOUT|FAILURE` в
`logs\item_gui_center_selftest_<stamp>.result`.

Классpath собирается из локального Gradle/Loom кэша (`minecraft-client.jar` 26.2, `joml 1.10.8`,
`datafixerupper 10.0.21`); патчи/секреты не используются.
