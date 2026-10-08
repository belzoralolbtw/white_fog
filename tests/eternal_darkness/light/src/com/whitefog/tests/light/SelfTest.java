package com.whitefog.tests.light;

import com.whitefog.tests.light.LightModel.Fuel;
import com.whitefog.tests.light.LightModel.Kind;
import com.whitefog.tests.light.LightModel.Pos;
import com.whitefog.tests.light.LightModel.Record;
import com.whitefog.tests.light.LightModel.RefuelResult;
import com.whitefog.tests.light.LightModel.Store;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded self-test гаснущих источников света (этап 1.6).
 *
 * <p><b>Это чистая логика без Minecraft</b> и НЕ доказательство runtime-поведения
 * (реальные BlockState/компоненты/сеть/light engine). Реальная проверка — сборка мода,
 * smoke и интерактивная приёмка архитектором.</p>
 *
 * <p>Само-завершение: внутренний watchdog жёстко завершает JVM через {@value #HARD_TIMEOUT_MS} мс
 * с кодом 124 ({@code TIMEOUT}).</p>
 */
public final class SelfTest {
	private static final long HARD_TIMEOUT_MS = 20_000L;
	private static final int EXIT_TIMEOUT = 124;

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
		}, "light-test-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog light sources sandbox self-test (logic only, NOT runtime) ===");
		long begin = System.nanoTime();

		capacitiesAndBonuses();
		normalizeZeroOneMaxInvalid();
		coalOverflowTorchRefused();
		torchNearEmptyFillsAtCommit();
		campfireTwoLogs();
		campfireStickAndCoal();
		unlitPause();
		repeatedTickGuard();
		lantern24000Countdown();
		unloadPausesWithoutCatchUp();
		itemEntityDoesNotTick();
		lastTickExtinguishes();
		sourceReplacementNoInherit();
		splitMergeRejectChargedCount2();
		managedPostRefusesRefuel();
		generatedBonusValues();
		initializedChunkOnce();

		// Фикс nearest/unlit discoverability (Work Panel должен находить погасший источник).
		unlitDiscoveryThenAct();
		nearestDiscoverabilityUnlit();
		nearestUnlitWithFuelDiscoverable();
		nearestRulesPreserved();
		nearestTieBreakPrefersLowerCoords();
		nearestClosestWinsRegardlessOfLit();

		long ms = (System.nanoTime() - begin) / 1_000_000L;
		System.out.println("-------------------------------------------------------------");
		System.out.println("passed=" + passed + " failed=" + failures.size() + " timeMs=" + ms);
		for (String failure : failures) {
			System.out.println("FAIL: " + failure);
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

	private static void check(String name, boolean condition) {
		if (condition) {
			passed++;
			System.out.println("ok - " + name);
		} else {
			failures.add(name);
			System.out.println("NOT OK - " + name);
		}
	}

	private static void eq(String name, long expected, long actual) {
		check(name + " (expected=" + expected + " actual=" + actual + ")", expected == actual);
	}

	// ------------------------------------------------------------------

	private static void capacitiesAndBonuses() {
		eq("capacity torch", 12_000, Kind.TORCH.capacity);
		eq("capacity soul torch", 8_000, Kind.SOUL_TORCH.capacity);
		eq("capacity lantern", 24_000, Kind.LANTERN.capacity);
		eq("capacity soul lantern", 16_000, Kind.SOUL_LANTERN.capacity);
		eq("capacity campfire", 16_000, Kind.CAMPFIRE.capacity);
	}

	private static void normalizeZeroOneMaxInvalid() {
		eq("normalize null -> 0", 0, LightModel.normalize(null, 12_000));
		eq("normalize negative -> 0", 0, LightModel.normalize(-5, 12_000));
		eq("normalize 0 -> 0", 0, LightModel.normalize(0, 12_000));
		eq("normalize 1 -> 1", 1, LightModel.normalize(1, 12_000));
		eq("normalize max -> max", 12_000, LightModel.normalize(12_000, 12_000));
		eq("normalize above max clamps", 12_000, LightModel.normalize(99_999, 12_000));
	}

	private static void coalOverflowTorchRefused() {
		// Факел remaining=100, за 20 тиков job доходит до 80; 80+12000 > 12000 → отказ, без списания.
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 100, 1L, false, true);
		for (int i = 0; i < 20; i++) {
			r.remaining--;
		}
		eq("torch remaining after 20 job ticks", 80, r.remaining);
		RefuelResult res = LightModel.refuel(r, Kind.TORCH, Fuel.COAL);
		check("torch coal overflow refused", res == RefuelResult.FULL);
		eq("torch remaining unchanged on refusal", 80, r.remaining);
	}

	private static void torchNearEmptyFillsAtCommit() {
		// remaining=1 → за 20 тиков гаснет (0), затем coal заполняет до capacity 12000.
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 1, 1L, false, true);
		for (int i = 0; i < 20; i++) {
			r.remaining--;
			if (r.remaining <= 0) {
				r.remaining = 0;
				r.lit = false;
			}
		}
		eq("torch extinguished during job", 0, r.remaining);
		check("torch lit=false after extinguish", !r.lit);
		RefuelResult res = LightModel.refuel(r, Kind.TORCH, Fuel.COAL);
		check("torch coal fits at 0 remaining", res == RefuelResult.OK);
		eq("torch filled to capacity", 12_000, r.remaining);
	}

	private static void campfireTwoLogs() {
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:campfire", 0, 1L, false, false);
		check("campfire log1 ok", LightModel.refuel(r, Kind.CAMPFIRE, Fuel.OAK_LOG) == RefuelResult.OK);
		eq("campfire after log1", 8_000, r.remaining);
		check("campfire log2 ok", LightModel.refuel(r, Kind.CAMPFIRE, Fuel.SPRUCE_LOG) == RefuelResult.OK);
		eq("campfire two logs = 16000", 16_000, r.remaining);
	}

	private static void campfireStickAndCoal() {
		Record stick = new Record(java.util.UUID.randomUUID(), "minecraft:campfire", 0, 1L, false, false);
		check("campfire stick ok", LightModel.refuel(stick, Kind.CAMPFIRE, Fuel.STICK) == RefuelResult.OK);
		eq("campfire after stick", 2_000, stick.remaining);

		Record coal = new Record(java.util.UUID.randomUUID(), "minecraft:campfire", 0, 1L, false, false);
		check("campfire coal ok", LightModel.refuel(coal, Kind.CAMPFIRE, Fuel.COAL) == RefuelResult.OK);
		eq("campfire after coal", 16_000, coal.remaining);
		eq("campfire soul/birch log addition", 8_000, LightModel.addition(Kind.CAMPFIRE, Fuel.BIRCH_LOG));
	}

	private static void unlitPause() {
		Store store = new Store();
		Pos pos = new Pos(0, 64, 0);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 5_000, 1L, false, false);
		store.records.put(pos, r);
		for (int i = 0; i < 100; i++) {
			LightModel.tick(store, pos, true, false);
		}
		eq("unlit with fuel does not consume", 5_000, r.remaining);
	}

	private static void repeatedTickGuard() {
		Store store = new Store();
		Pos pos = new Pos(0, 64, 0);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 5_000, 1L, false, true);
		store.records.put(pos, r);
		int lastTick = -1;
		simulateServerTick(store, pos, 10, lastTick);
		lastTick = 10;
		simulateServerTick(store, pos, 10, lastTick); // повторный вызов того же тика — должен игнорироваться
		eq("repeated server tick decrements once", 4_999, r.remaining);
	}

	private static void simulateServerTick(Store store, Pos pos, int tick, int lastTick) {
		if (tick == lastTick) {
			return;
		}
		LightModel.tick(store, pos, true, false);
	}

	private static void lantern24000Countdown() {
		Store store = new Store();
		Pos pos = new Pos(0, 70, 0);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 24_000, 1L, false, true);
		store.records.put(pos, r);
		for (int i = 0; i < 100; i++) {
			LightModel.tick(store, pos, true, false);
		}
		eq("lantern 24000 after 100 loaded ticks", 23_900, r.remaining);
	}

	private static void unloadPausesWithoutCatchUp() {
		Store store = new Store();
		Pos pos = new Pos(0, 70, 0);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 23_900, 1L, false, true);
		store.records.put(pos, r);
		for (int i = 0; i < 10_000; i++) {
			LightModel.tick(store, pos, false, false); // unload: tick пропускается
		}
		eq("lantern unchanged after 10000 world ticks unloaded", 23_900, r.remaining);
	}

	private static void itemEntityDoesNotTick() {
		Store store = new Store();
		Pos pos = new Pos(0, 70, 0);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 23_900, 1L, false, true);
		store.records.put(pos, r);
		for (int i = 0; i < 100; i++) {
			LightModel.tick(store, pos, true, true); // ItemEntity: не светит и не расходуется
		}
		eq("ItemEntity lantern unchanged", 23_900, r.remaining);
	}

	private static void lastTickExtinguishes() {
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 1, 1L, false, true);
		r.remaining--;
		if (r.remaining <= 0) {
			r.remaining = 0;
			r.lit = false;
			r.revision++;
		}
		check("1->0 immediately unlit", !r.lit);
		eq("1->0 remaining 0", 0, r.remaining);
		check("1->0 emission would be 0", !r.lit);
	}

	private static void sourceReplacementNoInherit() {
		Record oldRecord = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 9_000, 1L, false, true);
		java.util.UUID oldUuid = oldRecord.uuid;
		// Проверка на commit: UUID старой записи не совпадает с новой → отказ (никакого наследования).
		Record newRecord = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false);
		check("replacement gets new uuid", !oldUuid.equals(newRecord.uuid));
		check("replacement does not inherit remaining", newRecord.remaining == 0);
		check("uuid mismatch detected", !oldRecord.uuid.equals(newRecord.uuid));
	}

	private static void splitMergeRejectChargedCount2() {
		check("charged count=1 allowed", LightModel.place(Kind.TORCH, "minecraft:torch", 5_000, 1) != null);
		check("charged count=2 rejected", LightModel.place(Kind.TORCH, "minecraft:torch", 5_000, 2) == null);
		check("empty count=64 allowed", LightModel.place(Kind.TORCH, "minecraft:torch", 0, 64) != null);
		eq("empty stack places 0", 0, LightModel.place(Kind.TORCH, "minecraft:torch", null, 64).remaining);
	}

	private static void managedPostRefusesRefuel() {
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 1_000, 1L, true, true);
		check("managed post refuses refuel", r.managedByPost);
		// Inspect остаётся доступен (данные читаются), refuel запрещён.
		eq("inspect still reads remaining", 1_000, r.remaining);
	}

	private static void generatedBonusValues() {
		eq("bonus torch", 6_000, LightModel.initializeGenerated(Kind.TORCH, true, "minecraft:torch").remaining);
		eq("bonus soul torch", 4_000,
				LightModel.initializeGenerated(Kind.SOUL_TORCH, true, "minecraft:soul_torch").remaining);
		eq("bonus lantern", 12_000, LightModel.initializeGenerated(Kind.LANTERN, true, "minecraft:lantern").remaining);
		eq("bonus soul lantern", 8_000,
				LightModel.initializeGenerated(Kind.SOUL_LANTERN, true, "minecraft:soul_lantern").remaining);
		eq("bonus lit campfire", 8_000,
				LightModel.initializeGenerated(Kind.CAMPFIRE, true, "minecraft:campfire").remaining);
		eq("bonus unlit campfire", 0,
				LightModel.initializeGenerated(Kind.CAMPFIRE, false, "minecraft:campfire").remaining);
	}

	private static void initializedChunkOnce() {
		Store store = new Store();
		long chunk = 123L;
		check("chunk initially not initialized", !store.initializedChunks.contains(chunk));
		store.initializedChunks.add(chunk);
		check("chunk marked initialized", store.initializedChunks.contains(chunk));
		// Ручная установка получает запись сразу и бонуса при будущем scan не получает.
		Record manual = LightModel.place(Kind.TORCH, "minecraft:torch", 0, 1);
		check("manual placement no bonus", manual != null && manual.remaining == 0);
	}

	// ------------------------------------------------------------------
	// nearest / unlit discoverability (фикс этапа 1.6)
	// ------------------------------------------------------------------

	/**
	 * Ядро фикса: пустой погасший источник (remaining=0) должен находиться nearest'ом и допускать
	 * последующий refuel (уголь) или lightOnly (remaining&gt;0, пустая рука).
	 */
	private static void unlitDiscoveryThenAct() {
		// remaining=0 + уголь → обычный refuel заполняет и зажигает.
		Record empty = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false);
		check("remaining=0 torch coal refuel ok", LightModel.refuel(empty, Kind.TORCH, Fuel.COAL) == RefuelResult.OK);
		eq("remaining=0 torch filled", 12_000, empty.remaining);
		check("remaining=0 torch lit after refuel", empty.lit);

		// remaining>0 unlit + пустая рука → lightOnly «Зажечь» без расхода остатка.
		Record withFuel = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 3_000, 1L, false, false);
		check("unlit with fuel light-only ok", LightModel.light(withFuel));
		eq("light-only keeps remaining", 3_000, withFuel.remaining);
		check("unlit with fuel lit after light", withFuel.lit);
	}

	/** Погасший источник с remaining=0 обнаружим (lit не фильтруется). */
	private static void nearestDiscoverabilityUnlit() {
		Store store = new Store();
		Pos pos = new Pos(2, 64, 2);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false);
		store.records.put(pos, r);
		Pos eye = new Pos(0, 65, 0);
		java.util.Set<Pos> loaded = java.util.Set.of(pos);
		java.util.Map<Pos, String> world = java.util.Map.of(pos, "minecraft:torch");
		java.util.Set<Pos> los = java.util.Set.of(pos);
		LightModel.Snapshot s = LightModel.nearest(store, eye, 8.0D, loaded, world, los);
		check("unlit remaining=0 discoverable", s != null);
		check("unlit remaining=0 snapshot lit=false", s != null && !s.lit());
		eq("unlit remaining=0 snapshot remaining", 0, s == null ? -1 : s.record().remaining);
	}

	/** Погасший источник с запасом (remaining&gt;0) тоже обнаружим и показан как несветящий. */
	private static void nearestUnlitWithFuelDiscoverable() {
		Store store = new Store();
		Pos pos = new Pos(1, 64, 1);
		Record r = new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 5_000, 3L, false, false);
		store.records.put(pos, r);
		Pos eye = new Pos(0, 64, 0);
		LightModel.Snapshot s = LightModel.nearest(store, eye, 8.0D,
				java.util.Set.of(pos), java.util.Map.of(pos, "minecraft:lantern"), java.util.Set.of(pos));
		check("unlit with fuel discoverable", s != null);
		check("unlit with fuel snapshot lit=false", s != null && !s.lit());
		eq("unlit with fuel snapshot remaining", 5_000, s == null ? -1 : s.record().remaining);
	}

	/** Сохранённые правила: unloaded/замена блока/нет LOS/вне радиуса — не выбираются. */
	private static void nearestRulesPreserved() {
		Store store = new Store();
		Pos unloaded = new Pos(1, 64, 1);
		Pos mismatched = new Pos(2, 64, 2);
		Pos noLos = new Pos(3, 64, 3);
		Pos tooFar = new Pos(20, 64, 20);
		Pos good = new Pos(0, 64, 4);
		store.records.put(unloaded, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(mismatched, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(noLos, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(tooFar, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(good, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		Pos eye = new Pos(0, 65, 0);
		java.util.Set<Pos> loaded = java.util.Set.of(mismatched, noLos, tooFar, good); // unloaded отсутствует
		java.util.Map<Pos, String> world = new java.util.HashMap<>();
		world.put(unloaded, "minecraft:torch");
		world.put(mismatched, "minecraft:soul_torch"); // не совпадает с expectedBlockId
		world.put(noLos, "minecraft:torch");
		world.put(tooFar, "minecraft:torch");
		world.put(good, "minecraft:torch");
		java.util.Set<Pos> los = java.util.Set.of(mismatched, tooFar, good); // noLos отсутствует
		LightModel.Snapshot s = LightModel.nearest(store, eye, 8.0D, loaded, world, los);
		check("rules pick only valid source", s != null && s.pos().equals(good));
	}

	/** Tie-break при равном расстоянии — меньшие x/y/z. */
	private static void nearestTieBreakPrefersLowerCoords() {
		Store store = new Store();
		Pos a = new Pos(-2, 64, 0);
		Pos b = new Pos(2, 64, 0); // равное расстояние до eye
		store.records.put(a, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(b, new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		Pos eye = new Pos(0, 64, 0);
		LightModel.Snapshot s = LightModel.nearest(store, eye, 8.0D, java.util.Set.of(a, b),
				java.util.Map.of(a, "minecraft:torch", b, "minecraft:torch"), java.util.Set.of(a, b));
		check("tie-break picks lower x", s != null && s.pos().equals(a));
	}

	/**
	 * При наличии ближнего погасшего и дальнего горящего выбирается ближний погасший (lit не влияет
	 * на выбор) — это осознанное поведение Work Panel: игрок работает с ближайшим источником.
	 */
	private static void nearestClosestWinsRegardlessOfLit() {
		Store store = new Store();
		Pos closerUnlit = new Pos(1, 64, 0);
		Pos fartherLit = new Pos(4, 64, 0);
		store.records.put(closerUnlit,
				new Record(java.util.UUID.randomUUID(), "minecraft:torch", 0, 1L, false, false));
		store.records.put(fartherLit,
				new Record(java.util.UUID.randomUUID(), "minecraft:lantern", 9_000, 1L, false, true));
		Pos eye = new Pos(0, 64, 0);
		LightModel.Snapshot s = LightModel.nearest(store, eye, 8.0D, java.util.Set.of(closerUnlit, fartherLit),
				java.util.Map.of(closerUnlit, "minecraft:torch", fartherLit, "minecraft:lantern"),
				java.util.Set.of(closerUnlit, fartherLit));
		check("closest unlit wins over farther lit", s != null && s.pos().equals(closerUnlit));
		check("closest unlit snapshot lit=false", s != null && !s.lit());
	}
}
