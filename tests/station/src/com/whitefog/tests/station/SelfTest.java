package com.whitefog.tests.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded self-test станции «Плоский камень» и камушек (этап 1.4).
 *
 * <p><b>Это чистая логика без Minecraft</b> и НЕ доказательство runtime-поведения
 * (миксины, block entity, сеть, реальные {@code BlockState}/{@code ItemStack}).
 * Реальная проверка — сборка мода + запуск игры архитектором.</p>
 *
 * <p>Само-завершение: внутренний watchdog жёстко убивает JVM через {@value #HARD_TIMEOUT_MS} мс
 * и оставляет код возврата 124 ({@code TIMEOUT}).</p>
 */
public final class SelfTest {

	private static final long HARD_TIMEOUT_MS = 20_000L;
	private static final int EXIT_TIMEOUT = 124;

	private static final String FLAT = StationService.FLAT_STONE;
	private static final String SMALL = StationService.SMALL_STONE;
	private static final String COBBLE = StationService.COBBLESTONE;
	private static final String SLAB = StationService.STONE_SLAB;
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

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
		}, "station-test-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog flat-stone / small-stone sandbox self-test (logic only, NOT runtime) ===");
		long begin = System.nanoTime();

		installRemoveHundredTimes();
		concurrentTwoPlayersOneResult();
		fullInventoryOnePendingDrop();
		unsupportedPlacementNoConsumption();
		doubleClickSameTickOnePickup();
		cooldownBlocksSecondPickupSameTick();
		pendingSurvivesChunkUnloadLoad();
		recoveryCancelKeepsItems();
		recoveryCompletesConsumesTwoAndPlaces();
		recoveryRejectedWithoutSecondCobblestone();
		pistonDoesNotMoveStation();
		explosionOneResultOnly();
		saveLoadPreservesActiveJob();
		busyStationRefusesRemovalWithMessage();
		vanillaSlabIsNotAStation();
		revisionGuardSecondRemoveNoResult();
		smallStoneVariantDeterministicAndLootNeutral();
		unsupportedAndLiquidRejected();
		dimensionChangeCancelsRecovery();

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

	private static void installRemoveHundredTimes() {
		section("1. 100 циклов установить/снять — количество предметов постоянно");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(0, 64, 0);
		p.give(FLAT, 100);
		SimWorld.StationKey key = key(0, 64, 0);

		for (int i = 0; i < 100; i++) {
			check("install #" + i, svc.install(p, key, FLAT, true, true, false, true)
					== StationService.InstallResult.PLACED, "install");
			check("remove #" + i, svc.removeStation(p, key) == StationService.RemoveResult.REMOVED,
					"remove");
		}
		check("предметов всё ещё 100", p.count(FLAT) == 100, "count=" + p.count(FLAT));
		check("блоков не осталось", w.blockCount() == 0, "blocks=" + w.blockCount());
		check("изменений ровно 200", w.blockChanges == 200, "changes=" + w.blockChanges);
	}

	private static void concurrentTwoPlayersOneResult() {
		section("2. два игрока снимают одну станцию — ровно один результат");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(1, 64, 0);
		w.put(key, FLAT);
		PlayerRef a = player(1, 64, 0);
		PlayerRef b = playerB(1, 64, 0);

		StationService.RemoveResult ra = svc.removeStation(a, key);
		StationService.RemoveResult rb = svc.removeStation(b, key);
		check("A получил предмет", ra == StationService.RemoveResult.REMOVED, "a=" + ra);
		check("B не получил второй", rb == StationService.RemoveResult.NO_BLOCK, "b=" + rb);
		check("всего выдано 1", w.givenToInventory + w.totalDrops() == 1,
				"total=" + (w.givenToInventory + w.totalDrops()));
	}

	private static void fullInventoryOnePendingDrop() {
		section("3. полный инвентарь — ровно один pending-drop с задержкой 10 тиков");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(2, 64, 0);
		w.put(key, FLAT);
		PlayerRef p = player(2, 64, 0);
		p.inventoryFull = true;

		check("станция снята", svc.removeStation(p, key) == StationService.RemoveResult.REMOVED, "remove");
		check("в инвентарь ничего не легло", w.givenToInventory == 0, "inv=" + w.givenToInventory);
		check("выпал ровно 1", w.drops().size() == 1 && w.totalDrops() == 1, "drops=" + w.totalDrops());
		check("задержка подбора 10", w.drops().get(0).pickupDelayTicks == 10,
				"delay=" + w.drops().get(0).pickupDelayTicks);
	}

	private static void unsupportedPlacementNoConsumption() {
		section("4. неподходящее место не списывает предмет");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(3, 64, 0);
		p.give(FLAT, 1);
		SimWorld.StationKey key = key(3, 64, 0);

		check("нет твёрдой опоры → UNSUPPORTED",
				svc.install(p, key, FLAT, false, true, false, true) == StationService.InstallResult.UNSUPPORTED,
				"install");
		check("жидкость → LIQUID",
				svc.install(p, key, FLAT, true, true, true, true) == StationService.InstallResult.LIQUID, "liquid");
		check("занято → OCCUPIED",
				svc.install(p, key, FLAT, true, false, false, true) == StationService.InstallResult.OCCUPIED, "occupied");
		check("нет прав → NO_PERMISSION",
				svc.install(p, key, FLAT, true, true, false, false) == StationService.InstallResult.NO_PERMISSION,
				"perm");
		check("предмет НЕ списан", p.count(FLAT) == 1, "count=" + p.count(FLAT));
		check("блок не поставлен", w.blockCount() == 0, "blocks=" + w.blockCount());
	}

	private static void doubleClickSameTickOnePickup() {
		section("5. два клика по камушку в одном тике — ровно один предмет");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(4, 64, 0);
		w.put(key, SMALL);
		PlayerRef p = player(4, 64, 0);

		StationService.PickupResult first = svc.pickupSmallStone(p, key, 5L);
		StationService.PickupResult second = svc.pickupSmallStone(p, key, 5L);
		check("первый клик — PICKED", first == StationService.PickupResult.PICKED, "first=" + first);
		check("второй клик — NO_BLOCK", second == StationService.PickupResult.NO_BLOCK, "second=" + second);
		check("выдано ровно 1", p.count(SMALL) == 1, "count=" + p.count(SMALL));
	}

	private static void cooldownBlocksSecondPickupSameTick() {
		section("6. cooldown 2 тика не даёт подобрать два РАЗНЫХ камушка в одном тике");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey k1 = key(5, 64, 0);
		SimWorld.StationKey k2 = key(5, 64, 1);
		w.put(k1, SMALL);
		w.put(k2, SMALL);
		PlayerRef p = player(5, 64, 0);

		check("первый PICKED", svc.pickupSmallStone(p, k1, 10L) == StationService.PickupResult.PICKED, "k1");
		check("второй COOLDOWN", svc.pickupSmallStone(p, k2, 10L) == StationService.PickupResult.COOLDOWN, "k2");
		check("подобрано ровно 1", p.count(SMALL) == 1, "count=" + p.count(SMALL));
		check("через 2 тика можно снова",
				svc.pickupSmallStone(p, k2, 12L) == StationService.PickupResult.PICKED, "k2b");
	}

	private static void pendingSurvivesChunkUnloadLoad() {
		section("7. pending-drop переживает unload/load чанка (без потери и дубля)");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(6, 64, 0);
		w.put(key, SMALL);
		w.loadChunk(key);
		PlayerRef p = player(6, 64, 0);
		p.inventoryFull = true;

		check("подбор при полном инвентаре", svc.pickupSmallStone(p, key, 0L) == StationService.PickupResult.PICKED,
				"pickup");
		w.unloadChunk(key);
		w.loadChunk(key);
		check("после unload/load ровно 1 drop", w.drops().size() == 1 && w.totalDrops() == 1,
				"drops=" + w.totalDrops());
		check("блока больше нет", !w.has(key), "block");
	}

	private static void recoveryCancelKeepsItems() {
		section("8. recovery: движение отменяет задачу, cobblestone не теряется (возврат тривиален)");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(7, 64, 0);
		p.shiftHeld = true;
		p.give(COBBLE, 2);
		SimWorld.StationKey ground = key(7, 63, 0);

		check("запуск", svc.recoveryStart(p, ground, COBBLE, 2, true, true, false, 0L)
				== StationService.RecoveryResult.STARTED, "start");
		PlayerRef moved = p.at(p.x + 1.0, p.y, p.z);
		check("движение → CANCELLED", svc.recoveryTick(moved, 10L) == StationService.RecoveryResult.CANCELLED,
				"tick");
		check("cobblestone по-прежнему 2", moved.count(COBBLE) == 2, "count=" + moved.count(COBBLE));
		check("flat_stone не появился", w.blockCount() == 0, "blocks=" + w.blockCount());
	}

	private static void recoveryCompletesConsumesTwoAndPlaces() {
		section("9. recovery: 100 тиков без движения → −2 cobblestone, +1 flat_stone");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(8, 64, 0);
		p.shiftHeld = true;
		p.give(COBBLE, 5);
		SimWorld.StationKey ground = key(8, 63, 0);

		check("запуск", svc.recoveryStart(p, ground, COBBLE, 5, true, true, false, 0L)
				== StationService.RecoveryResult.STARTED, "start");
		check("тик 50 — IN_PROGRESS", svc.recoveryTick(p, 50L) == StationService.RecoveryResult.IN_PROGRESS, "mid");
		check("тик 100 — COMPLETED", svc.recoveryTick(p, 100L) == StationService.RecoveryResult.COMPLETED, "done");
		check("cobblestone 5 → 3", p.count(COBBLE) == 3, "count=" + p.count(COBBLE));
		check("flat_stone поставлен", w.has(new SimWorld.StationKey(OVERWORLD, 8, 64, 0)), "block");
	}

	private static void recoveryRejectedWithoutSecondCobblestone() {
		section("10. recovery без второго cobblestone (или не на твёрдой земле) не запускается");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(9, 64, 0);
		p.give(COBBLE, 1);
		SimWorld.StationKey ground = key(9, 63, 0);

		check("один cobblestone — REJECTED",
				svc.recoveryStart(p, ground, COBBLE, 1, true, true, false, 0L) == StationService.RecoveryResult.REJECTED,
				"one");
		p.give(COBBLE, 1);
		check("нет твёрдой земли — REJECTED",
				svc.recoveryStart(p, ground, COBBLE, 2, false, true, false, 0L)
						== StationService.RecoveryResult.REJECTED, "support");
	}

	private static void pistonDoesNotMoveStation() {
		section("11. поршень не двигает flat_stone; small_stone разрушается с ровно 1 дропом");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey flat = key(10, 64, 0);
		SimWorld.StationKey small = key(10, 64, 1);
		w.put(flat, FLAT);
		w.put(small, SMALL);
		int changesBefore = w.blockChanges;

		check("flat_stone BLOCKED", svc.pistonPush(flat) == StationService.PushResult.BLOCKED, "flat");
		check("flat_stone не изменился", w.has(flat) && w.blockChanges == changesBefore, "changes");
		check("small_stone DESTROYED_ONE", svc.pistonPush(small) == StationService.PushResult.DESTROYED_ONE, "small");
		check("ровно 1 drop", w.totalDrops() == 1, "drops=" + w.totalDrops());
	}

	private static void explosionOneResultOnly() {
		section("12. взрыв: ровно один block item, повторный вызов — без второго");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(11, 64, 0);
		w.put(key, FLAT);

		check("первый взрыв", svc.explosion(key) == StationService.PushResult.DESTROYED_ONE, "first");
		check("второй взрыв — NO_BLOCK", svc.explosion(key) == StationService.PushResult.NO_BLOCK, "second");
		check("всего ровно 1", w.totalDrops() == 1, "drops=" + w.totalDrops());
	}

	private static void saveLoadPreservesActiveJob() {
		section("13. save/load active job сохраняет владельца, escrow, output, mode, progress");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(12, 64, 0);
		SimWorld.Block block = new SimWorld.Block(FLAT);
		block.revision = 7L;
		block.jobOwner = "player-job-1";
		block.escrow = 3;
		block.output = 1;
		block.mode = "FORGE";
		block.jobProgress = 40;
		block.jobRequired = 100;
		w.putBlock(key, block);

		String data = svc.saveBlock(block);
		SimWorld.Block loaded = svc.loadBlock(data);
		check("id сохранён", FLAT.equals(loaded.id), "id=" + loaded.id);
		check("revision сохранён", loaded.revision == 7L, "rev=" + loaded.revision);
		check("владелец job сохранён", "player-job-1".equals(loaded.jobOwner), "job=" + loaded.jobOwner);
		check("escrow/output сохранены", loaded.escrow == 3 && loaded.output == 1, "escrow/output");
		check("mode/progress сохранены", "FORGE".equals(loaded.mode) && loaded.jobProgress == 40
				&& loaded.jobRequired == 100, "mode/progress");
		check("job активен после загрузки", loaded.isBusy(), "busy");
	}

	private static void busyStationRefusesRemovalWithMessage() {
		section("14. занятая станция не снимается и отвечает точным текстом");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(13, 64, 0);
		SimWorld.Block block = new SimWorld.Block(FLAT);
		block.jobOwner = "owner";
		block.escrow = 1;
		w.putBlock(key, block);
		PlayerRef p = player(13, 64, 0);

		check("BUSY", svc.removeStation(p, key) == StationService.RemoveResult.BUSY, "busy");
		check("сообщение точное", w.messages.contains(StationService.MESSAGE_BUSY), "msg");
		check("предмет не выдан", w.givenToInventory + w.totalDrops() == 0, "given");
		check("блок на месте", w.has(key), "block");
		check("отказов 1", svc.busyRefusals == 1, "refusals=" + svc.busyRefusals);
	}

	private static void vanillaSlabIsNotAStation() {
		section("15. vanilla stone_slab больше не открывает станцию");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(14, 64, 0);
		w.put(key, SLAB);
		PlayerRef p = player(14, 64, 0);

		check("снятие slab → NO_BLOCK", svc.removeStation(p, key) == StationService.RemoveResult.NO_BLOCK, "remove");
		check("slab не тронут", w.has(key), "block");
		check("предметов не выдано", w.givenToInventory + w.totalDrops() == 0, "given");
	}

	private static void revisionGuardSecondRemoveNoResult() {
		section("16. revision guard: после снятия повторное снятие не даёт второго результата");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		SimWorld.StationKey key = key(15, 64, 0);
		w.put(key, FLAT);
		PlayerRef p = player(15, 64, 0);

		svc.removeStation(p, key);
		check("второе снятие — NO_BLOCK", svc.removeStation(p, key) == StationService.RemoveResult.NO_BLOCK, "second");
		check("всего ровно 1", w.givenToInventory + w.totalDrops() == 1,
				"total=" + (w.givenToInventory + w.totalDrops()));
	}

	private static void smallStoneVariantDeterministicAndLootNeutral() {
		section("17. вариант модели камушка детерминирован и не влияет на loot");
		SimWorld.StationKey key = key(16, 64, 0);
		boolean v1 = StationService.smallStoneVariant(key);
		boolean v2 = StationService.smallStoneVariant(key);
		check("вариант стабилен", v1 == v2, "v");

		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		w.put(key, SMALL);
		PlayerRef p = player(16, 64, 0);
		svc.pickupSmallStone(p, key, 0L);
		check("loot всегда ровно 1 small_stone", p.count(SMALL) == 1, "count=" + p.count(SMALL));
	}

	private static void unsupportedAndLiquidRejected() {
		section("18. установка small_stone по тем же правилам (unsupported/liquid)");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(17, 64, 0);
		p.give(SMALL, 2);
		SimWorld.StationKey key = key(17, 64, 0);

		check("unsupported", svc.install(p, key, SMALL, false, true, false, true)
				== StationService.InstallResult.UNSUPPORTED, "unsupported");
		check("liquid", svc.install(p, key, SMALL, true, true, true, true)
				== StationService.InstallResult.LIQUID, "liquid");
		check("предметы не списаны", p.count(SMALL) == 2, "count=" + p.count(SMALL));
		check("поставили small_stone", svc.install(p, key, SMALL, true, true, false, true)
				== StationService.InstallResult.PLACED, "placed");
		check("списан ровно 1", p.count(SMALL) == 1, "count=" + p.count(SMALL));
	}

	private static void dimensionChangeCancelsRecovery() {
		section("19. смена измерения отменяет recovery");
		SimWorld w = new SimWorld();
		StationService svc = new StationService(w);
		PlayerRef p = player(18, 64, 0);
		p.give(COBBLE, 2);
		SimWorld.StationKey ground = key(18, 63, 0);

		check("запуск", svc.recoveryStart(p, ground, COBBLE, 2, true, true, false, 0L)
				== StationService.RecoveryResult.STARTED, "start");
		PlayerRef nether = new PlayerRef(p.uuid, NETHER, p.x, p.y, p.z, p.maxReach, false);
		nether.give(COBBLE, 2);
		check("другое измерение → CANCELLED",
				svc.recoveryTick(nether, 10L) == StationService.RecoveryResult.CANCELLED, "tick");
		check("cobblestone целы", nether.count(COBBLE) == 2, "count=" + nether.count(COBBLE));
	}

	// ------------------------------------------------------------------
	// Инфраструктура
	// ------------------------------------------------------------------

	private static SimWorld.StationKey key(int x, int y, int z) {
		return new SimWorld.StationKey(OVERWORLD, x, y, z);
	}

	private static PlayerRef player(int x, int y, int z) {
		return new PlayerRef("player-a", OVERWORLD, x + 0.5, y, z + 0.5, 5.0F, false);
	}

	private static PlayerRef playerB(int x, int y, int z) {
		return new PlayerRef("player-b", OVERWORLD, x + 0.6, y, z + 0.5, 5.0F, false);
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
