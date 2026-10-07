package com.whitefog.tests.breaktimer;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded self-test жизненного цикла серверного таймера разрушения (этап 1.3).
 *
 * <p><b>Это НЕ доказательство runtime-поведения Minecraft.</b> Тест проверяет только
 * чистую логику lifecycle (START/STOP/ABORT, валидация цели, дедупликация commit,
 * политика инструментов/станций). Реальная проверка требует сборки и запуска игры.</p>
 *
 * <p>Завершается сам: внутренний watchdog жёстко убивает JVM через {@value #HARD_TIMEOUT_MS} мс
 * и оставляет код возврата 124 ({@code TIMEOUT}).</p>
 */
public final class SelfTest {

	/** Внутренний hard-timeout (мс). Тест должен завершиться за миллисекунды. */
	private static final long HARD_TIMEOUT_MS = 20_000L;
	private static final int EXIT_TIMEOUT = 124;

	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final ToolStack MOD_PICKAXE = ToolStack.of(RoadmapPolicy.MOD_PICKAXE_ID);
	private static final ToolStack VANILLA_PICKAXE = ToolStack.of("minecraft:iron_pickaxe");
	private static final ToolStack VANILLA_AXE = ToolStack.of("minecraft:iron_axe");
	private static final ToolStack SHEARS = ToolStack.of("minecraft:shears");

	private static int passed = 0;
	private static final List<String> failures = new ArrayList<>();

	private SelfTest() {
	}

	public static void main(String[] args) {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(HARD_TIMEOUT_MS);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("HARD_TIMEOUT after " + HARD_TIMEOUT_MS + " ms");
			System.out.flush();
			Runtime.getRuntime().halt(EXIT_TIMEOUT);
		}, "break-timer-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog break-timer sandbox self-test (logic only, NOT runtime) ===");
		long begin = System.nanoTime();

		softSoilHandServerCommit();
		prematureStopKeepsSessionThenTickCommits();
		abortNoCommit();
		toolChangeWithComponentsAborts();
		dimensionChangeAborts();
		chunkUnloadAborts();
		outOfRangeAborts();
		twoPlayersOneBlockOneCommit();
		exactlyOnceDedupeGuard();
		hotStationRefused();
		furnaceRequiresModPickaxe();
		handNoLeafDropToolDrops();
		vanillaPickaxeCannotMineStone();
		vanillaAxeCannotMineLog();
		handCannotMineHard();
		filledCauldronRefused();
		creativeBypassConfigurable();
		stopWithoutStart();
		doubleStopNoDoubleDrop();
		refusalMessageCooldown();
		filledStationDuringTimerAborts();
		filledStationStartRefusedAndCommitGuard();
		clientAllowSuppressesVanillaAndSendsSingleStart();
		clientDenySendsNoStartAndHintsWithCooldown();
		clientTargetOrToolChangeSendsAbortThenStart();
		clientUnclassifiedLeavesVanillaAndClosesSession();
		clientAllowGetsSwingDenyDoesNot();

		long ms = (System.nanoTime() - begin) / 1_000_000L;
		System.out.println("-------------------------------------------------------------");
		System.out.println("passed=" + passed + " failed=" + failures.size() + " timeMs=" + ms);
		for (String f : failures) {
			System.out.println("FAILED: " + f);
		}

		if (failures.isEmpty()) {
			System.out.println("SELFTEST status=SUCCESS");
			System.out.flush();
			Runtime.getRuntime().halt(0);
		} else {
			System.out.println("SELFTEST status=FAILURE");
			System.out.flush();
			Runtime.getRuntime().halt(1);
		}
	}

	// ------------------------------------------------------------------
	// Сценарии
	// ------------------------------------------------------------------

	private static void softSoilHandServerCommit() {
		section("1. земля рукой: медленный серверный таймер + ровно один commit/дроп");
		SimWorld w = new SimWorld();
		BlockKey dirt = key(0, 64, 0);
		w.put(dirt, BlockKind.SOFT_SOIL);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(0, 64, 0);

		check("START принят", svc.start(p, ToolStack.hand(), dirt, 0L) == BreakTimerService.Outcome.STARTED, "start");
		check("tick до срока — цель жива", svc.tick(p, ToolStack.hand(), dirt, 10L) == BreakTimerService.Outcome.TICK_OK, "tick");
		check("сервер дозрел и закоммитил без STOP", svc.tick(p, ToolStack.hand(), dirt, 60L) == BreakTimerService.Outcome.COMMITTED, "commit");
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
		check("дроп ровно 1", w.dropCount() == 1, "drops=" + w.dropCount());
	}

	private static void prematureStopKeepsSessionThenTickCommits() {
		section("2. преждевременный STOP: сессия СОХРАНЯЕТСЯ, commit только по серверному таймеру");
		SimWorld w = new SimWorld();
		BlockKey leaf = key(0, 64, 1);
		w.put(leaf, BlockKind.SOFT_LEAF);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(0, 64, 1);

		check("START", svc.start(p, ToolStack.hand(), leaf, 0L) == BreakTimerService.Outcome.STARTED, "start");
		check("STOP на 10 из 40 — PREMATURE", svc.stop(p, ToolStack.hand(), leaf, 10L) == BreakTimerService.Outcome.PREMATURE_STOP, "stop");
		check("сессия сохранена (server-authoritative)", svc.hasSession(p.uuid), "session");
		check("до срока изменения нет", w.changeCount() == 0, "changes=" + w.changeCount());
		check("tick на 39 — цель жива", svc.tick(p, ToolStack.hand(), leaf, 39L) == BreakTimerService.Outcome.TICK_OK, "tick1");
		check("tick на 40 — commit", svc.tick(p, ToolStack.hand(), leaf, 40L) == BreakTimerService.Outcome.COMMITTED, "tick2");
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
		check("листва рукой — 0 дропа", w.dropCount() == 0, "drops=" + w.dropCount());
	}

	private static void abortNoCommit() {
		section("3. ABORT — отмена без commit");
		SimWorld w = new SimWorld();
		BlockKey dirt = key(0, 64, 2);
		w.put(dirt, BlockKind.SOFT_SOIL);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(0, 64, 2);

		svc.start(p, ToolStack.hand(), dirt, 0L);
		check("ABORT", svc.abort(p, dirt, 3L) == BreakTimerService.Outcome.ABORTED, "abort");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
		check("сессия снята", !svc.hasSession(p.uuid), "session");
	}

	private static void toolChangeWithComponentsAborts() {
		section("4. смена инструмента, включая data-компоненты, прерывает сессию");
		SimWorld w = new SimWorld();
		BlockKey stone = key(1, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(1, 64, 0);

		ToolStack eff5 = MOD_PICKAXE.withComponent("minecraft:enchantments", "efficiency=5");
		ToolStack eff3 = MOD_PICKAXE.withComponent("minecraft:enchantments", "efficiency=3");

		check("START с кайлом (eff=5)", svc.start(p, eff5, stone, 0L) == BreakTimerService.Outcome.STARTED, "start");
		check("тот же id, другой компонент — TICK_ABORTED",
				svc.tick(p, eff3, stone, 5L) == BreakTimerService.Outcome.TICK_ABORTED, "tick");
		check("причина TOOL_CHANGED", svc.lastAbortReason() == BreakTimerService.AbortReason.TOOL_CHANGED,
				svc.lastAbortReason().toString());
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());

		check("повторный START", svc.start(p, eff5, stone, 10L) == BreakTimerService.Outcome.STARTED, "restart");
		check("тот же стек с теми же компонентами — цель жива",
				svc.tick(p, eff5, stone, 15L) == BreakTimerService.Outcome.TICK_OK, "tick");
	}

	private static void dimensionChangeAborts() {
		section("5. смена измерения прерывает сессию");
		SimWorld w = new SimWorld();
		BlockKey stone = key(2, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(2, 64, 0);

		svc.start(p, MOD_PICKAXE, stone, 0L);
		check("tick в Нижнем мире — TICK_ABORTED",
				svc.tick(p.inDimension(NETHER), MOD_PICKAXE, stone, 5L) == BreakTimerService.Outcome.TICK_ABORTED, "tick");
		check("причина DIMENSION_CHANGED",
				svc.lastAbortReason() == BreakTimerService.AbortReason.DIMENSION_CHANGED, svc.lastAbortReason().toString());
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
	}

	private static void chunkUnloadAborts() {
		section("6. выгрузка чанка прерывает сессию");
		SimWorld w = new SimWorld();
		BlockKey gravel = key(3, 64, 0);
		w.put(gravel, BlockKind.SOFT_SOIL);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(3, 64, 0);

		svc.start(p, ToolStack.hand(), gravel, 0L);
		w.unload(gravel);
		check("tick на выгруженном чанке — TICK_ABORTED",
				svc.tick(p, ToolStack.hand(), gravel, 5L) == BreakTimerService.Outcome.TICK_ABORTED, "tick");
		check("причина CHUNK_UNLOADED",
				svc.lastAbortReason() == BreakTimerService.AbortReason.CHUNK_UNLOADED, svc.lastAbortReason().toString());
	}

	private static void outOfRangeAborts() {
		section("7. уход из зоны досягаемости прерывает сессию");
		SimWorld w = new SimWorld();
		BlockKey sand = key(4, 64, 0);
		w.put(sand, BlockKind.SOFT_SOIL);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(4, 64, 0);

		svc.start(p, ToolStack.hand(), sand, 0L);
		PlayerRef far = p.at(100, 64, 100);
		check("tick далеко — TICK_ABORTED",
				svc.tick(far, ToolStack.hand(), sand, 5L) == BreakTimerService.Outcome.TICK_ABORTED, "tick");
		check("причина OUT_OF_RANGE",
				svc.lastAbortReason() == BreakTimerService.AbortReason.OUT_OF_RANGE, svc.lastAbortReason().toString());
	}

	private static void twoPlayersOneBlockOneCommit() {
		section("8. два игрока на один блок — блок меняется максимум один раз");
		SimWorld w = new SimWorld();
		BlockKey stone = key(5, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef a = playerA(5, 64, 0);
		PlayerRef b = playerB(5, 64, 0);

		check("A START", svc.start(a, MOD_PICKAXE, stone, 0L) == BreakTimerService.Outcome.STARTED, "a.start");
		check("B START", svc.start(b, MOD_PICKAXE, stone, 0L) == BreakTimerService.Outcome.STARTED, "b.start");
		check("A commit", svc.tick(a, MOD_PICKAXE, stone, 120L) == BreakTimerService.Outcome.COMMITTED, "a.commit");
		// B видит, что блок уже другой — его сессия прерывается, второго изменения/дропа нет.
		BreakTimerService.Outcome bOut = svc.stop(b, MOD_PICKAXE, stone, 120L);
		check("B не коммитит повторно", bOut == BreakTimerService.Outcome.TICK_ABORTED
				|| bOut == BreakTimerService.Outcome.SUPPRESSED, "b=" + bOut);
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
		check("дроп ровно 1", w.dropCount() == 1, "drops=" + w.dropCount());
	}

	private static void exactlyOnceDedupeGuard() {
		section("9. защита «ровно один раз» (white-box guard commitNow)");
		SimWorld w = new SimWorld();
		BlockKey stone = key(6, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);

		check("первый commit", svc.commitNow(stone, BlockKind.HARD_STONE, ToolKind.MOD_PICKAXE)
				== BreakTimerService.Outcome.COMMITTED, "commit1");
		check("повторный commit подавлен", svc.commitNow(stone, BlockKind.HARD_STONE, ToolKind.MOD_PICKAXE)
				== BreakTimerService.Outcome.SUPPRESSED, "commit2");
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
		check("дроп ровно 1", w.dropCount() == 1, "drops=" + w.dropCount());
	}

	private static void hotStationRefused() {
		section("10. горячая станция — отказ");
		SimWorld w = new SimWorld();
		BlockKey furnace = key(7, 64, 0);
		w.put(furnace, BlockKind.STATION_HOT);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(7, 64, 0);

		check("START по горячей печи — отказ",
				svc.start(p, MOD_PICKAXE, furnace, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("сессии нет", !svc.hasSession(p.uuid), "session");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
	}

	private static void furnaceRequiresModPickaxe() {
		section("11. печь (не горит): ванильная кирка нельзя, модовое кайло можно");
		SimWorld w = new SimWorld();
		BlockKey furnace = key(8, 64, 0);
		w.put(furnace, BlockKind.STATION_FURNACE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(8, 64, 0);

		check("ванильная кирка — отказ",
				svc.start(p, VANILLA_PICKAXE, furnace, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("модовое кайло — START", svc.start(p, MOD_PICKAXE, furnace, 0L) == BreakTimerService.Outcome.STARTED, "start2");
		check("commit", svc.tick(p, MOD_PICKAXE, furnace, 60L) == BreakTimerService.Outcome.COMMITTED, "commit");
		check("станция дала ровно 1 предмет", w.dropCount() == 1, "drops=" + w.dropCount());
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
	}

	private static void handNoLeafDropToolDrops() {
		section("12. листва: ровно 0 дропа при ручном разрушении (в т.ч. ножницами)");
		SimWorld w1 = new SimWorld();
		BlockKey leaf1 = key(9, 64, 0);
		w1.put(leaf1, BlockKind.SOFT_LEAF);
		BreakTimerService svc1 = svc(w1);
		PlayerRef p1 = playerA(9, 64, 0);
		svc1.start(p1, ToolStack.hand(), leaf1, 0L);
		svc1.tick(p1, ToolStack.hand(), leaf1, 40L);
		check("рукой: изменение есть", w1.changeCount() == 1, "changes=" + w1.changeCount());
		check("рукой: дропа нет", w1.dropCount() == 0, "drops=" + w1.dropCount());

		SimWorld w2 = new SimWorld();
		BlockKey leaf2 = key(9, 64, 1);
		w2.put(leaf2, BlockKind.SOFT_LEAF);
		BreakTimerService svc2 = svc(w2);
		PlayerRef p2 = playerA(9, 64, 1);
		svc2.start(p2, SHEARS, leaf2, 0L);
		svc2.tick(p2, SHEARS, leaf2, 40L);
		check("ножницами: изменение есть", w2.changeCount() == 1, "changes=" + w2.changeCount());
		check("ножницами: скрытого ванильного дропа нет", w2.dropCount() == 0, "drops=" + w2.dropCount());
	}

	private static void vanillaPickaxeCannotMineStone() {
		section("13. ванильная кирка не добывает камень; модовое кайло — да");
		SimWorld w = new SimWorld();
		BlockKey stone = key(10, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(10, 64, 0);

		check("ванильная кирка — отказ",
				svc.start(p, VANILLA_PICKAXE, stone, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("модовое кайло — START", svc.start(p, MOD_PICKAXE, stone, 0L) == BreakTimerService.Outcome.STARTED, "start2");
		check("commit", svc.tick(p, MOD_PICKAXE, stone, 120L) == BreakTimerService.Outcome.COMMITTED, "commit");
	}

	private static void vanillaAxeCannotMineLog() {
		section("14. ванильный топор не добывает бревно");
		SimWorld w = new SimWorld();
		BlockKey log = key(11, 64, 0);
		w.put(log, BlockKind.HARD_WOOD);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(11, 64, 0);

		check("ванильный топор — отказ",
				svc.start(p, VANILLA_AXE, log, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
	}

	private static void handCannotMineHard() {
		section("15. кулак не двигает твёрдый блок");
		SimWorld w = new SimWorld();
		BlockKey stone = key(12, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(12, 64, 0);

		check("рука — отказ", svc.start(p, ToolStack.hand(), stone, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("подсказка показана 1 раз", svc.stats().messages == 1, "messages=" + svc.stats().messages);
	}

	private static void filledCauldronRefused() {
		section("16. наполненный котёл — отказ до опустошения");
		SimWorld w = new SimWorld();
		BlockKey cauldron = key(13, 64, 0);
		w.put(cauldron, BlockKind.CAULDRON_FILLED);
		w.setFilled(cauldron, true);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(13, 64, 0);

		check("START — отказ", svc.start(p, MOD_PICKAXE, cauldron, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
	}

	private static void filledStationDuringTimerAborts() {
		section("21. станция стала наполненной во время таймера — abort (содержимое не теряется)");
		SimWorld w = new SimWorld();
		BlockKey furnace = key(18, 64, 0);
		w.put(furnace, BlockKind.STATION_FURNACE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(18, 64, 0);

		check("START по пустой печи", svc.start(p, MOD_PICKAXE, furnace, 0L) == BreakTimerService.Outcome.STARTED, "start");
		// Игрок положил предмет в печь за время таймера; BlockState при этом не менялся (LIT=false).
		w.setFilled(furnace, true);
		check("tick — TICK_ABORTED", svc.tick(p, MOD_PICKAXE, furnace, 40L) == BreakTimerService.Outcome.TICK_ABORTED, "tick");
		check("причина FILLED", svc.lastAbortReason() == BreakTimerService.AbortReason.FILLED, svc.lastAbortReason().toString());
		check("сессия снята", !svc.hasSession(p.uuid), "session");
		check("без изменения (содержимое цело)", w.changeCount() == 0, "changes=" + w.changeCount());
		check("без дропа", w.dropCount() == 0, "drops=" + w.dropCount());
	}

	private static void filledStationStartRefusedAndCommitGuard() {
		section("22. наполненная станция: отказ на START и последний defensive guard в commit");
		SimWorld w = new SimWorld();
		BlockKey furnace = key(19, 64, 0);
		w.put(furnace, BlockKind.STATION_FURNACE);
		w.setFilled(furnace, true);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(19, 64, 0);

		check("START по наполненной — отказ",
				svc.start(p, MOD_PICKAXE, furnace, 0L) == BreakTimerService.Outcome.START_REFUSED, "start");
		check("прямой commitNow наполненной станции подавлен",
				svc.commitNow(furnace, BlockKind.STATION_FURNACE, ToolKind.MOD_PICKAXE) == BreakTimerService.Outcome.SUPPRESSED, "commit");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
		check("без дропа", w.dropCount() == 0, "drops=" + w.dropCount());
	}

	private static void creativeBypassConfigurable() {
		section("17. creative: таймер 0, но категории/инструменты НЕ обходятся (утверждено этап 1.3)");
		SimWorld w = new SimWorld();
		BlockKey stone = key(14, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService instant = new BreakTimerService(w, new RoadmapPolicy(), true);
		PlayerRef creative = playerA(14, 64, 0).creative(true);

		check("creative + модовое кайло → мгновенный commit",
				instant.start(creative, MOD_PICKAXE, stone, 0L) == BreakTimerService.Outcome.COMMITTED, "instant");
		check("таймер не запускался", instant.stats().starts == 0, "starts=" + instant.stats().starts);

		SimWorld w2 = new SimWorld();
		BlockKey stone2 = key(14, 64, 1);
		w2.put(stone2, BlockKind.HARD_STONE);
		BreakTimerService instant2 = new BreakTimerService(w2, new RoadmapPolicy(), true);
		check("creative рукой по твёрдому — отказ (категории не обходятся)",
				instant2.start(creative, ToolStack.hand(), stone2, 0L) == BreakTimerService.Outcome.START_REFUSED, "strict");

		SimWorld w3 = new SimWorld();
		BlockKey stone3 = key(14, 64, 2);
		w3.put(stone3, BlockKind.HARD_STONE);
		BreakTimerService strict = new BreakTimerService(w3, new RoadmapPolicy(), false);
		check("creativeBypass=false → креатив ограничен как выживание",
				strict.start(creative, ToolStack.hand(), stone3, 0L) == BreakTimerService.Outcome.START_REFUSED, "strict2");
	}

	private static void stopWithoutStart() {
		section("18. STOP без START");
		SimWorld w = new SimWorld();
		BlockKey stone = key(15, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(15, 64, 0);

		check("NO_SESSION", svc.stop(p, MOD_PICKAXE, stone, 0L) == BreakTimerService.Outcome.NO_SESSION, "stop");
		check("без изменения", w.changeCount() == 0, "changes=" + w.changeCount());
	}

	private static void doubleStopNoDoubleDrop() {
		section("19. повторный STOP после commit — без второго дропа");
		SimWorld w = new SimWorld();
		BlockKey dirt = key(16, 64, 0);
		w.put(dirt, BlockKind.SOFT_SOIL);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(16, 64, 0);

		svc.start(p, ToolStack.hand(), dirt, 0L);
		check("commit", svc.stop(p, ToolStack.hand(), dirt, 60L) == BreakTimerService.Outcome.COMMITTED, "stop1");
		check("STOP повторить — NO_SESSION", svc.stop(p, ToolStack.hand(), dirt, 60L) == BreakTimerService.Outcome.NO_SESSION, "stop2");
		check("изменение ровно 1", w.changeCount() == 1, "changes=" + w.changeCount());
		check("дроп ровно 1", w.dropCount() == 1, "drops=" + w.dropCount());
	}

	private static void refusalMessageCooldown() {
		section("20. cooldown подсказки при повторных отказах");
		SimWorld w = new SimWorld();
		BlockKey stone = key(17, 64, 0);
		w.put(stone, BlockKind.HARD_STONE);
		BreakTimerService svc = svc(w);
		PlayerRef p = playerA(17, 64, 0);

		svc.start(p, ToolStack.hand(), stone, 0L);
		svc.start(p, ToolStack.hand(), stone, 5L);
		svc.start(p, ToolStack.hand(), stone, 25L);
		check("3 отказа", svc.stats().refusals == 3, "refusals=" + svc.stats().refusals);
		check("2 подсказки (cooldown 20 тиков)", svc.stats().messages == 2, "messages=" + svc.stats().messages);
	}

	// ------------------------------------------------------------------
	// Клиентский гейт предсказания (regression fix этапа 1.3)
	// ------------------------------------------------------------------

	private static void clientAllowSuppressesVanillaAndSendsSingleStart() {
		section("23. клиент ALLOW (земля рукой): ваниль подавлена, ровно 1 START, без STOP");
		ClientGate gate = new ClientGate(new RoadmapPolicy());
		BlockKey dirt = key(30, 64, 0);
		ToolStack hand = ToolStack.hand();

		check("start подавлен", gate.start(dirt, BlockKind.SOFT_SOIL, hand), "suppress");
		check("исходящий START ровно 1", gate.sends().equals(List.of("START@" + dirt)), gate.sends().toString());
		// Удержание ЛКМ: много continue-тиков, но НЕ должно быть новых пакетов и подсказок.
		for (int i = 0; i < 100; i++) {
			gate.continueTick(dirt, BlockKind.SOFT_SOIL, hand);
		}
		check("сессия активна (серверный таймер идёт)", gate.hasServerSession(), "session");
		check("за 100 continue-тиков пакетов по-прежнему 1", gate.sends().size() == 1, gate.sends().toString());
		check("без подсказки при ALLOW", gate.messages() == 0, "messages=" + gate.messages());
		check("ваниль подавлена на каждом тике", gate.vanillaSuppressed() == 101, "suppressed=" + gate.vanillaSuppressed());
		check("ALLOW: continueDestroyBlock вернул бы true (ванильный визуал удара)",
				gate.continueReturnsTrue(BlockKind.SOFT_SOIL, hand), "visual-ret");
		check("ALLOW: визуал отдан на всех 100 continue-тиках", gate.visualContinueTicks() == 100,
				"visualTicks=" + gate.visualContinueTicks());
		gate.stop();
		check("stop → ABORT", gate.sends().equals(List.of("START@" + dirt, "ABORT@" + dirt)), gate.sends().toString());
	}

	private static void clientDenySendsNoStartAndHintsWithCooldown() {
		section("24. клиент DENY: без START, локальная подсказка с cooldown 20 тиков");
		ClientGate gate = new ClientGate(new RoadmapPolicy());
		BlockKey stone = key(31, 64, 0);
		ToolStack hand = ToolStack.hand();

		check("start подавлен", gate.start(stone, BlockKind.HARD_STONE, hand), "suppress");
		check("START НЕ отправлен", gate.sends().isEmpty(), gate.sends().toString());
		check("подсказка показана 1 раз", gate.messages() == 1, "messages=" + gate.messages());
		// Спам каждый тик в пределах 20 тиков не должен дублировать подсказку.
		for (int i = 0; i < 10; i++) {
			gate.continueTick(stone, BlockKind.HARD_STONE, hand);
		}
		check("cooldown: подсказка всё ещё 1", gate.messages() == 1, "messages=" + gate.messages());
		// Переждав cooldown (>= 20 тиков), подсказка показывается снова.
		for (int i = 0; i < 20; i++) {
			gate.continueTick(stone, BlockKind.HARD_STONE, hand);
		}
		check("после cooldown подсказка снова", gate.messages() == 2, "messages=" + gate.messages());
		check("за всё время START не отправлялся", gate.sends().isEmpty(), gate.sends().toString());
		check("DENY: continueDestroyBlock вернул бы false (без визуала)",
				!gate.continueReturnsTrue(BlockKind.HARD_STONE, hand), "visual-ret");
		check("DENY: ноль визуальных continue-тиков", gate.visualContinueTicks() == 0,
				"visualTicks=" + gate.visualContinueTicks());

		ClientGate hotGate = new ClientGate(new RoadmapPolicy());
		BlockKey furnace = key(31, 64, 1);
		hotGate.start(furnace, BlockKind.STATION_HOT, MOD_PICKAXE);
		check("DENY_HOT: без START, подсказка", hotGate.sends().isEmpty() && hotGate.messages() == 1,
				"sends=" + hotGate.sends() + " messages=" + hotGate.messages());

		ClientGate filledGate = new ClientGate(new RoadmapPolicy());
		BlockKey cauldron = key(31, 64, 2);
		filledGate.start(cauldron, BlockKind.CAULDRON_FILLED, MOD_PICKAXE);
		check("DENY_FILLED: без START, подсказка", filledGate.sends().isEmpty() && filledGate.messages() == 1,
				"sends=" + filledGate.sends() + " messages=" + filledGate.messages());
	}

	private static void clientTargetOrToolChangeSendsAbortThenStart() {
		section("25. клиент: смена цели/инструмента → ABORT + START (серверная сессия пересоздаётся)");
		ClientGate targetGate = new ClientGate(new RoadmapPolicy());
		BlockKey a = key(32, 64, 0);
		BlockKey b = key(32, 64, 1);
		targetGate.start(a, BlockKind.SOFT_SOIL, ToolStack.hand());
		targetGate.continueTick(b, BlockKind.SOFT_SOIL, ToolStack.hand());
		check("смена цели: START A → ABORT A → START B",
				targetGate.sends().equals(List.of("START@" + a, "ABORT@" + a, "START@" + b)),
				targetGate.sends().toString());

		ClientGate toolGate = new ClientGate(new RoadmapPolicy());
		BlockKey soil = key(33, 64, 0);
		ToolStack shovel = ToolStack.of("minecraft:iron_shovel");
		toolGate.start(soil, BlockKind.SOFT_SOIL, ToolStack.hand());
		toolGate.continueTick(soil, BlockKind.SOFT_SOIL, shovel);
		check("смена инструмента (та же цель): START → ABORT → START",
				toolGate.sends().equals(List.of("START@" + soil, "ABORT@" + soil, "START@" + soil)),
				toolGate.sends().toString());
	}

	private static void clientUnclassifiedLeavesVanillaAndClosesSession() {
		section("26. клиент UNCLASSIFIED: ваниль не подавляется; наша сессия закрывается");
		ClientGate gate = new ClientGate(new RoadmapPolicy());
		BlockKey dirt = key(34, 64, 0);
		BlockKey rails = key(34, 64, 1); // null kind = UNCLASSIFIED

		check("UNCLASSIFIED start НЕ подавлен", !gate.start(rails, null, ToolStack.hand()), "not-suppressed");
		check("пакетов нет", gate.sends().isEmpty(), gate.sends().toString());

		gate.start(dirt, BlockKind.SOFT_SOIL, ToolStack.hand());
		gate.continueTick(rails, null, ToolStack.hand());
		check("перешли на UNCLASSIFIED: наша сессия закрыта ABORT'ом",
				gate.sends().equals(List.of("START@" + dirt, "ABORT@" + dirt)), gate.sends().toString());
		check("UNCLASSIFIED: мод не возвращает true (ваниль решает сама)",
				!gate.continueReturnsTrue(null, ToolStack.hand()), "visual-ret");
		check("UNCLASSIFIED: мод не считает визуальные тики", gate.visualContinueTicks() == 0,
				"visualTicks=" + gate.visualContinueTicks());
	}

	private static void clientAllowGetsSwingDenyDoesNot() {
		section("27. клиент visual (swing fix): ALLOW даёт визуал удара, DENY — нет, UNCLASSIFIED — ваниль");
		ToolStack hand = ToolStack.hand();

		// ALLOW: держим ЛКМ 40 тиков — визуал (модель swing+частицы) на каждом; mining-пакетов всё ещё 1.
		ClientGate allow = new ClientGate(new RoadmapPolicy());
		BlockKey dirt = key(35, 64, 0);
		allow.start(dirt, BlockKind.SOFT_SOIL, hand);
		for (int i = 0; i < 40; i++) {
			allow.continueTick(dirt, BlockKind.SOFT_SOIL, hand);
		}
		check("ALLOW: 40/40 continue-тиков с визуалом", allow.visualContinueTicks() == 40,
				"ticks=" + allow.visualContinueTicks());
		check("ALLOW: всё ещё ровно 1 START (визуал не шлёт mining-пакеты)", allow.sends().size() == 1,
				allow.sends().toString());
		check("ALLOW: возврат true", allow.continueReturnsTrue(BlockKind.SOFT_SOIL, hand), "ret");

		// DENY: держим ЛКМ 40 тиков — визуала нет, подсказка есть, START нет.
		ClientGate deny = new ClientGate(new RoadmapPolicy());
		BlockKey stone = key(35, 64, 1);
		deny.start(stone, BlockKind.HARD_STONE, hand);
		for (int i = 0; i < 40; i++) {
			deny.continueTick(stone, BlockKind.HARD_STONE, hand);
		}
		check("DENY: ноль визуальных тиков", deny.visualContinueTicks() == 0,
				"ticks=" + deny.visualContinueTicks());
		check("DENY: возврат false", !deny.continueReturnsTrue(BlockKind.HARD_STONE, hand), "ret");
		check("DENY: START не отправлялся", deny.sends().isEmpty(), deny.sends().toString());
		check("DENY: подсказка показана", deny.messages() >= 1, "messages=" + deny.messages());

		// UNCLASSIFIED: мод не вмешивается — ни визуального счётчика, ни пакетов.
		ClientGate vanilla = new ClientGate(new RoadmapPolicy());
		BlockKey rails = key(35, 64, 2);
		check("UNCLASSIFIED: start НЕ подавлен", !vanilla.start(rails, null, hand), "not-suppressed");
		for (int i = 0; i < 40; i++) {
			vanilla.continueTick(rails, null, hand);
		}
		check("UNCLASSIFIED: мод не считает визуал", vanilla.visualContinueTicks() == 0,
				"ticks=" + vanilla.visualContinueTicks());
		check("UNCLASSIFIED: пакетов нет", vanilla.sends().isEmpty(), vanilla.sends().toString());
	}

	// ------------------------------------------------------------------
	// Инфраструктура
	// ------------------------------------------------------------------

	private static BreakTimerService svc(SimWorld w) {
		return new BreakTimerService(w, new RoadmapPolicy(), false);
	}

	private static BlockKey key(int x, int y, int z) {
		return new BlockKey(OVERWORLD, x, y, z);
	}

	private static PlayerRef playerA(int x, int y, int z) {
		return new PlayerRef("player-a", OVERWORLD, x + 0.5, y, z + 0.5, 5.0, false);
	}

	private static PlayerRef playerB(int x, int y, int z) {
		return new PlayerRef("player-b", OVERWORLD, x + 0.6, y, z + 0.5, 5.0, false);
	}

	private static void section(String title) {
		System.out.println("== " + title + " ==");
	}

	private static void check(String name, boolean ok, String detail) {
		if (ok) {
			passed++;
			System.out.println("  [PASS] " + name);
		} else {
			failures.add(name + " :: " + detail);
			System.out.println("  [FAIL] " + name + " :: " + detail);
		}
	}
}
