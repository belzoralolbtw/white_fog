package com.whitefog.tests.lightfix;

import java.util.List;

/**
 * Детерминированный self-test трёх исправлений этапа поверх 1.7:
 * <ol>
 *   <li>стабильность blend эффекта Darkness (нет «провала» фактора -> нет пульсации);</li>
 *   <li>запись источника по ФАКТИЧЕСКОЙ позиции и id поставленного блока;</li>
 *   <li>кнопки меню источника с горизонтальным отступом (текст не «впритык»).</li>
 * </ol>
 *
 * <p>Hard-timeout — watchdog-поток (20 c) => exit 124 = TIMEOUT. Это logic-only sandbox:
 * НЕ runtime-proof — реальные эффект/пакеты/мир/шрифт не проверяются.</p>
 */
public final class SelfTest {
	private static final long HARD_TIMEOUT_MS = 20_000L;

	private static int passed;
	private static int failed;
	private static final java.util.List<String> failures = new java.util.ArrayList<>();

	private SelfTest() {
	}

	public static void main(String[] args) {
		startWatchdog();

		System.out.println("=== White Fog darkness/light fix sandbox ===");
		System.out.println("darkness: duration=" + 60 + " refreshAt=" + 40 + " blendAdvance=" + BlendModel.BLEND_ADVANCE);
		System.out.println("menu: BUTTON_H_PAD=" + LayoutModel.BUTTON_H_PAD
				+ " PAD=" + LayoutModel.PAD + " GAP=" + LayoutModel.GAP);
		System.out.println();

		try {
			testBlendNoDipNewConfig();
			testBlendOldConfigDips();
			testBlendTailBounded();
			testPlacementBlockIdMismatch();
			testPlacementSupportPosition();
			testPlacementNormalPosition();
			testLayoutButtonsPadded();
			testLayoutOldButtonsTooNarrow();
			testLayoutBounded();
			testLayoutCompactNarrow();
		} catch (Throwable t) {
			fail("unexpected-exception: " + t);
			t.printStackTrace(System.out);
		}

		System.out.println();
		System.out.println("passed=" + passed + " failed=" + failed);
		for (String f : failures) {
			System.out.println("FAILED: " + f);
		}
		System.out.println("SELFTEST status=" + (failed == 0 ? "SUCCESS" : "FAILURE"));
		System.out.flush();
		System.exit(failed == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------
	// Darkness blend
	// ------------------------------------------------------------------

	/** Новая конфигурация (duration=60, refreshAt=40): фактор держится на 1, пульсации нет. */
	private static void testBlendNoDipNewConfig() {
		float min = minFactorWhileExposed(60, 40, 6000);
		checkClose("blend.new.noDip", 1.0f, min, 1e-6f);
		check("blend.new.refreshInterval", refreshInterval(60, 40) == 20);
	}

	/** Старая конфигурация (duration=40, refreshAt=20): фактор периодически проваливается. */
	private static void testBlendOldConfigDips() {
		float min = minFactorWhileExposed(40, 20, 6000);
		check("blend.old.dipsBelowOne", min < 1.0f - 0.02f);
	}

	/** Хвост при уходе exposure ниже порога ограничен и покрывается клиентским tail (5 c = 100 тиков). */
	private static void testBlendTailBounded() {
		int tail = 60 + BlendModel.BLEND_ADVANCE;
		check("blend.tailWithinClientWindow", tail <= 100);
	}

	/**
	 * Прогон: каждые 20 тиков один серверный sample (как {@code SAMPLE_INTERVAL_TICKS}); эффект
	 * обновляется до {@code duration}, когда остаток &le; {@code refreshAt}. Клиент тикает blend
	 * каждый тик. Возвращает минимум фактора после первого набора (первые 40 тиков пропускаются).
	 */
	private static float minFactorWhileExposed(int duration, int refreshAt, int ticks) {
		BlendModel blend = new BlendModel();
		int remaining = duration;
		blend.setImmediate(BlendModel.hasEffect(remaining));
		float min = 1.0f;
		for (int tick = 1; tick <= ticks; tick++) {
			if (tick % 20 == 0 && remaining <= refreshAt) {
				remaining = duration;
			}
			if (remaining > 0) {
				remaining--;
			}
			blend.tick(BlendModel.hasEffect(remaining), 22, 22);
			if (tick > 40) {
				min = Math.min(min, blend.factor());
			}
		}
		return min;
	}

	private static int refreshInterval(int duration, int refreshAt) {
		return duration - refreshAt;
	}

	// ------------------------------------------------------------------
	// Placement position / block id
	// ------------------------------------------------------------------

	/** (a) Расхождение id (стоячий vs настенный): старый commit теряет запись, новый — нет. */
	private static void testPlacementBlockIdMismatch() {
		PlacementModel.Pos pos = new PlacementModel.Pos(0, 1, 0);
		PlacementModel.World world = new PlacementModel.World();
		world.set(pos, "minecraft:wall_torch");

		PlacementModel oldStore = new PlacementModel();
		oldStore.commitOld(pos, "minecraft:torch"); // последним был adjust стоячего варианта
		oldStore.tickLevel(world);
		check("placement.blockId.oldRecordLost", oldStore.size() == 0);

		PlacementModel newStore = new PlacementModel();
		newStore.commitNew(world, pos, "TORCH");
		newStore.tickLevel(world);
		check("placement.blockId.newRecordSurvives", newStore.size() == 1);
		check("placement.blockId.newRecordAtPos", newStore.hasRecordAt(pos));
		check("placement.blockId.newRecordActualId",
				newStore.entries().get(0).expectedBlockId().equals("minecraft:wall_torch"));
	}

	/** (b) Запись по опорному блоку: старый commit теряет, новый переносит на реальный источник. */
	private static void testPlacementSupportPosition() {
		PlacementModel.Pos support = new PlacementModel.Pos(0, 0, 0);
		PlacementModel.Pos torch = new PlacementModel.Pos(0, 1, 0);
		PlacementModel.World world = new PlacementModel.World();
		world.set(support, "minecraft:stone");
		world.set(torch, "minecraft:torch");

		PlacementModel oldStore = new PlacementModel();
		oldStore.commitOld(support, "minecraft:torch");
		oldStore.tickLevel(world);
		check("placement.position.oldRecordLost", oldStore.size() == 0);

		PlacementModel newStore = new PlacementModel();
		newStore.commitNew(world, support, "TORCH");
		newStore.tickLevel(world);
		check("placement.position.newRecordSurvives", newStore.size() == 1);
		check("placement.position.newRecordAtTorch", newStore.hasRecordAt(torch));
	}

	/** (c) Обычный случай: запись строго по фактической позиции. */
	private static void testPlacementNormalPosition() {
		PlacementModel.Pos torch = new PlacementModel.Pos(2, 5, -3);
		PlacementModel.World world = new PlacementModel.World();
		world.set(torch, "minecraft:torch");
		PlacementModel store = new PlacementModel();
		store.commitNew(world, torch, "TORCH");
		store.tickLevel(world);
		check("placement.normal.survives", store.size() == 1);
		check("placement.normal.atPos", store.hasRecordAt(torch));
	}

	// ------------------------------------------------------------------
	// Menu layout
	// ------------------------------------------------------------------

	private static final java.util.function.ToIntFunction<String> MEASURE = LayoutModel::textWidth;

	private static LayoutModel.Layout layout(int width) {
		return LayoutModel.computeNew(width, MEASURE, "Факел · горит", "Осталось: 2 мин 0 сек",
				"Ночь · 00:00", true, true, false);
	}

	/** Новая раскладка: текст каждой кнопки помещается с отступами (при ширине не меньше MIN). */
	private static void testLayoutButtonsPadded() {
		boolean allFit = true;
		for (int width = LayoutModel.MIN_PANEL_WIDTH; width <= 320; width++) {
			LayoutModel.Layout l = layout(width);
			for (LayoutModel.Button b : l.buttons()) {
				if (!LayoutModel.labelFits(b, MEASURE, LayoutModel.BUTTON_H_PAD)) {
					allFit = false;
				}
			}
		}
		check("layout.new.labelsFitWithPadding", allFit);
	}

	/** Старая раскладка (без отступа): в широком режиме кнопка ровно по тексту — запаса нет. */
	private static void testLayoutOldButtonsTooNarrow() {
		LayoutModel.Layout old = LayoutModel.computeOld(360, MEASURE, "Факел · горит",
				"Осталось: 2 мин 0 сек", "Ночь · 00:00", true, true, false);
		boolean wideHasNoPadding = !old.compact();
		boolean anyTooNarrow = false;
		for (LayoutModel.Button b : old.buttons()) {
			if (!LayoutModel.labelFits(b, MEASURE, LayoutModel.BUTTON_H_PAD)) {
				anyTooNarrow = true;
			}
		}
		check("layout.old.wideMode", wideHasNoPadding);
		check("layout.old.someButtonTooNarrow", anyTooNarrow);
	}

	/** Новая раскладка ограничена: панель в доступной ширине, прямоугольники внутри, без наложений. */
	private static void testLayoutBounded() {
		boolean panelBounded = true;
		boolean rectsInside = true;
		boolean noOverlap = true;
		boolean positiveHeight = true;
		for (int width = LayoutModel.PAD; width <= 400; width++) {
			LayoutModel.Layout l = layout(width);
			int safe = Math.max(1, width);
			if (l.panelWidth() > safe || l.panelWidth() < 1) {
				panelBounded = false;
			}
			if (width >= LayoutModel.MIN_PANEL_WIDTH) {
				for (LayoutModel.Button b : l.buttons()) {
					if (b.rect().x() < 0 || b.rect().right() > l.panelWidth()
							|| b.rect().bottom() > l.panelHeight()) {
						rectsInside = false;
					}
				}
			} else {
				// Узкое окно: панель = доступная ширина, кнопки обрезаются по ней (без горизонтального выхода).
				for (LayoutModel.Button b : l.buttons()) {
					if (b.rect().right() > l.panelWidth()) {
						rectsInside = false;
					}
				}
			}
			if (l.panelHeight() <= 0) {
				positiveHeight = false;
			}
			if (!l.compact()) {
				List<LayoutModel.Button> bs = l.buttons();
				for (int i = 0; i + 1 < bs.size(); i++) {
					if (bs.get(i).rect().right() > bs.get(i + 1).rect().x()) {
						noOverlap = false;
					}
				}
			}
		}
		check("layout.new.panelBounded", panelBounded);
		check("layout.new.rectsInside", rectsInside);
		check("layout.new.noOverlap", noOverlap);
		check("layout.new.positiveHeight", positiveHeight);
	}

	/** Узкое окно: компактный режим, кнопки вертикальные, весь текст помещается. */
	private static void testLayoutCompactNarrow() {
		LayoutModel.Layout l = layout(70);
		check("layout.compact.flag", l.compact());
		check("layout.compact.threeButtons", l.buttons().size() == 3);
		boolean allFit = true;
		for (LayoutModel.Button b : l.buttons()) {
			if (!LayoutModel.labelFits(b, MEASURE, LayoutModel.BUTTON_H_PAD)) {
				allFit = false;
			}
		}
		check("layout.compact.labelsFit", allFit);
	}

	// ------------------------------------------------------------------
	// Инфраструктура
	// ------------------------------------------------------------------

	private static void startWatchdog() {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(HARD_TIMEOUT_MS);
			} catch (InterruptedException ignored) {
				return;
			}
			System.out.println("SELFTEST status=TIMEOUT");
			System.out.flush();
			Runtime.getRuntime().halt(124);
		}, "selftest-watchdog");
		t.setDaemon(true);
		t.start();
	}

	private static void check(String name, boolean condition) {
		if (condition) {
			passed++;
		} else {
			fail(name);
		}
	}

	private static void checkClose(String name, float expected, float actual, float tol) {
		if (Math.abs(expected - actual) <= tol) {
			passed++;
		} else {
			fail(name + " expected=" + expected + " actual=" + actual + " tol=" + tol);
		}
	}

	private static void fail(String name) {
		failed++;
		failures.add(name);
	}
}
