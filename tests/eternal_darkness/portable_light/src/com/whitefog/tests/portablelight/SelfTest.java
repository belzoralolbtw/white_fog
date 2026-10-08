package com.whitefog.tests.portablelight;

import com.whitefog.tests.portablelight.PortableLightLayout.Button;
import com.whitefog.tests.portablelight.PortableLightLayout.Layout;
import com.whitefog.tests.portablelight.PortableLightLayout.Row;
import com.whitefog.tests.portablelight.PortableLightLayout.View;
import com.whitefog.tests.portablelight.PortableLightModel.Kind;
import com.whitefog.tests.portablelight.PortableLightModel.RefuelResult;
import com.whitefog.tests.portablelight.PortableLightModel.RefuelStatus;
import com.whitefog.tests.portablelight.PortableLightModel.State;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded self-test переносного света факела в левой руке (logic-only sandbox).
 *
 * <p><b>Это чистая логика без Minecraft</b> и НЕ доказательство runtime-поведения: реальные
 * {@code ItemStack}/item-компонент {@code white_fog:light_fuel}, инвентарь левой руки, серверный
 * tick, сеть, light engine, меню и HUD не проверяются. Runtime-доказательство — сборка мода,
 * smoke-логи и интерактивная приёмка архитектора.</p>
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
		}, "portable-light-test-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog portable offhand light sandbox self-test (logic only, NOT runtime) ===");
		long begin = System.nanoTime();

		hudSourceStrings();
		itemMatching();
		componentSemantics();
		countCorruption();
		exposureAdapterAndFormula();
		tickCountdownAndPause();
		extinguishRelight();
		refuelRules();
		twoPlayersIndependent();
		durationFormatting();
		eternalNightClockLabel();
		buttonStates();
		sourceActions();
		layoutWide();
		layoutNarrowNoOverflow();
		fuelStatePartialRefuel();
		fuelStatePersistenceAndInventory();
		portableUiStrings();
		noPulseVisualOutputs();
		sourceMenuButtonsNoOverflow();

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

	private static void eqs(String name, String expected, String actual) {
		check(name + " (expected=\"" + expected + "\" actual=\"" + actual + "\")",
				expected == null ? actual == null : expected.equals(actual));
	}

	private static void eqObj(String name, Object expected, Object actual) {
		check(name + " (expected=" + expected + " actual=" + actual + ")", expected == actual);
	}

	private static void contains(String name, String haystack, String needle) {
		check(name + " (contains \"" + needle + "\" actual=\"" + haystack + "\")",
				haystack != null && haystack.contains(needle));
	}

	private static boolean fitsInner(String text, int panelWidth) {
		return PortableLightLayout.textWidth(text) <= Math.max(0, panelWidth - PortableLightLayout.PAD * 2);
	}

	// ------------------------------------------------------------------
	// 1. Сопоставление offhand-предмета
	// ------------------------------------------------------------------

	private static void itemMatching() {
		eqObj("kind torch", Kind.TORCH, PortableLightModel.kindForItem("minecraft:torch"));
		eqObj("kind wall torch alias", Kind.TORCH, PortableLightModel.kindForItem("minecraft:wall_torch"));
		eqObj("kind soul torch", Kind.SOUL_TORCH, PortableLightModel.kindForItem("minecraft:soul_torch"));
		eqObj("kind soul wall torch alias", Kind.SOUL_TORCH,
				PortableLightModel.kindForItem("minecraft:soul_wall_torch"));
		eqObj("kind case-insensitive", Kind.TORCH, PortableLightModel.kindForItem("MINECRAFT:TORCH"));

		check("portable torch", PortableLightModel.isPortableItem("minecraft:torch"));
		check("portable soul torch", PortableLightModel.isPortableItem("minecraft:soul_torch"));
		check("not portable lantern", !PortableLightModel.isPortableItem("minecraft:lantern"));
		check("not portable soul lantern", !PortableLightModel.isPortableItem("minecraft:soul_lantern"));
		check("not portable campfire", !PortableLightModel.isPortableItem("minecraft:campfire"));
		check("not portable soul campfire", !PortableLightModel.isPortableItem("minecraft:soul_campfire"));
		check("not portable stick", !PortableLightModel.isPortableItem("minecraft:stick"));
		check("not portable air", !PortableLightModel.isPortableItem("minecraft:air"));
		check("not portable empty id", !PortableLightModel.isPortableItem(""));
		check("not portable null id", !PortableLightModel.isPortableItem(null));
		eqObj("lantern kind null", null, PortableLightModel.kindForItem("minecraft:lantern"));

		State lantern = PortableLightModel.readOffhand("minecraft:lantern", 500, 1);
		check("lantern offhand absent", !lantern.present());
		check("lantern offhand no light", !PortableLightModel.activeLight(lantern));

		State empty = PortableLightModel.readOffhand(null, null, 0);
		check("null offhand absent", !empty.present());
		check("empty offhand no light", !PortableLightModel.activeLight(empty));
		eq("empty offhand emission", 0, PortableLightModel.emission(empty));
	}

	// ------------------------------------------------------------------
	// 2. Семантика компонента
	// ------------------------------------------------------------------

	private static void componentSemantics() {
		State noComponent = PortableLightModel.readOffhand("minecraft:torch", null, 1);
		check("no component present", noComponent.present());
		eq("no component remaining", 0, noComponent.remaining());
		check("no component not lit", !noComponent.lit());
		check("no component no light", !PortableLightModel.activeLight(noComponent));

		State zero = PortableLightModel.readOffhand("minecraft:torch", 0, 1);
		eq("zero component remaining", 0, zero.remaining());
		check("zero component no light", !PortableLightModel.activeLight(zero));

		State one = PortableLightModel.readOffhand("minecraft:torch", 1, 1);
		eq("component 1 remaining", 1, one.remaining());
		check("component 1 charged means light", PortableLightModel.activeLight(one));

		State full = PortableLightModel.readOffhand("minecraft:torch", 12_000, 1);
		eq("component full remaining", 12_000, full.remaining());
		check("component full lit", full.lit());

		State over = PortableLightModel.readOffhand("minecraft:torch", 20_000, 1);
		eq("component over capacity clamps", 12_000, over.remaining());

		State negative = PortableLightModel.readOffhand("minecraft:torch", -5, 1);
		eq("component negative -> 0", 0, negative.remaining());
		check("component negative no light", !PortableLightModel.activeLight(negative));

		eq("normalize null", 0, PortableLightModel.normalize(null, 12_000));
		eq("normalize negative", 0, PortableLightModel.normalize(-1, 12_000));
		eq("normalize zero", 0, PortableLightModel.normalize(0, 12_000));
		eq("normalize one", 1, PortableLightModel.normalize(1, 12_000));
		eq("normalize capacity", 12_000, PortableLightModel.normalize(12_000, 12_000));
		eq("normalize above capacity", 12_000, PortableLightModel.normalize(12_001, 12_000));
		eq("normalize soul above capacity", 8_000, PortableLightModel.normalize(9_000, 8_000));

		State unchargedStack = PortableLightModel.readOffhand("minecraft:torch", null, 5);
		eq("uncharged stack remaining", 0, unchargedStack.remaining());
		check("uncharged stack no light", !PortableLightModel.activeLight(unchargedStack));
		check("uncharged stack not corrupt", !unchargedStack.corrupt());
	}

	// ------------------------------------------------------------------
	// 3. Порча count/компонент
	// ------------------------------------------------------------------

	private static void countCorruption() {
		check("corrupt charged count2", PortableLightModel.isCorrupt(1, 2));
		check("not corrupt zero count2", !PortableLightModel.isCorrupt(0, 2));
		check("not corrupt null count2", !PortableLightModel.isCorrupt(null, 2));
		check("not corrupt charged count1", !PortableLightModel.isCorrupt(1, 1));

		State corrupt = PortableLightModel.readOffhand("minecraft:torch", 100, 2);
		check("corrupt present", corrupt.present());
		check("corrupt flag", corrupt.corrupt());
		check("corrupt not lit", !corrupt.lit());
		check("corrupt no light", !PortableLightModel.activeLight(corrupt));
		eq("corrupt emission", 0, PortableLightModel.emission(corrupt));

		check("corrupt zapravit disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, corrupt, true));
		check("corrupt potushit disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.POTUSHIT, corrupt, true));
		check("corrupt zazhech disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAZHECH, corrupt, true));

		RefuelResult refused = PortableLightModel.refuel(corrupt, true, true);
		eqObj("corrupt refuel refused", RefuelStatus.REFUSED_CORRUPT, refused.status());
		check("corrupt refuel not consumed", !refused.consumed());

		State ticked = PortableLightModel.tick(corrupt);
		eq("corrupt tick no consume", 100, ticked.remaining());
	}

	// ------------------------------------------------------------------
	// 4. Адаптер block light + неизменная формула exposure
	// ------------------------------------------------------------------

	private static void exposureAdapterAndFormula() {
		State torch = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		State soul = PortableLightModel.readOffhand("minecraft:soul_torch", 4_000, 1);
		State inactive = PortableLightModel.readOffhand("minecraft:torch", null, 1);
		State absent = PortableLightModel.readOffhand("minecraft:lantern", 500, 1);

		eq("torch emission", 14, PortableLightModel.emission(torch));
		eq("soul torch emission", 10, PortableLightModel.emission(soul));
		eq("inactive emission", 0, PortableLightModel.emission(inactive));
		eq("absent emission", 0, PortableLightModel.emission(absent));

		eq("adapter dark + torch", 14, PortableLightModel.effectiveBlockLight(0, torch));
		eq("adapter low + torch", 14, PortableLightModel.effectiveBlockLight(2, torch));
		eq("adapter already bright + torch", 15, PortableLightModel.effectiveBlockLight(15, torch));
		eq("adapter equal + torch", 14, PortableLightModel.effectiveBlockLight(14, torch));
		eq("adapter dark + soul", 10, PortableLightModel.effectiveBlockLight(0, soul));
		eq("adapter 9 + soul", 10, PortableLightModel.effectiveBlockLight(9, soul));
		eq("adapter inactive passthrough", 4, PortableLightModel.effectiveBlockLight(4, inactive));
		eq("adapter absent passthrough", 11, PortableLightModel.effectiveBlockLight(11, absent));

		// max, а НЕ сложение/радиус: 2 + 14 недопустимо.
		eq("adapter is max not sum", 14, PortableLightModel.effectiveBlockLight(2, torch));
		check("adapter not sum 16", PortableLightModel.effectiveBlockLight(2, torch) != 16);

		boolean bounded = true;
		boolean monotonic = true;
		for (int v = 0; v <= 15; v++) {
			int withTorch = PortableLightModel.effectiveBlockLight(v, torch);
			int withSoul = PortableLightModel.effectiveBlockLight(v, soul);
			if (withTorch > 15 || withSoul > 15 || withTorch < v || withSoul < v) {
				bounded = false;
			}
			if (withTorch < PortableLightModel.effectiveBlockLight(v, inactive)) {
				monotonic = false;
			}
		}
		check("adapter bounded 0..15 and never lowers", bounded);
		check("adapter monotonic vs inactive", monotonic);

		// Формула exposure НЕ менялась (DarknessConfig).
		eq("delta dark no sky", 1, PortableLightModel.exposureDelta(0, false));
		eq("delta dark open sky", 2, PortableLightModel.exposureDelta(0, true));
		eq("delta dark4 no sky", 1, PortableLightModel.exposureDelta(4, false));
		eq("delta neutral5", 0, PortableLightModel.exposureDelta(5, false));
		eq("delta neutral8 open sky", 0, PortableLightModel.exposureDelta(8, true));
		eq("delta bright9", -2, PortableLightModel.exposureDelta(9, false));
		eq("delta bright15", -2, PortableLightModel.exposureDelta(15, false));

		// Адаптер меняет только вход, не формулу.
		eq("without adapter dark delta", 1, PortableLightModel.exposureDelta(2, false));
		eq("with torch adapter delta", -2,
				PortableLightModel.exposureDelta(PortableLightModel.effectiveBlockLight(2, torch), false));
		eq("with soul adapter delta", -2,
				PortableLightModel.exposureDelta(PortableLightModel.effectiveBlockLight(0, soul), false));
	}

	// ------------------------------------------------------------------
	// 5. Серверный countdown / пауза погашенного
	// ------------------------------------------------------------------

	private static void tickCountdownAndPause() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 100, 1);
		State afterOne = PortableLightModel.tick(lit);
		eq("tick decrements lit", 99, afterOne.remaining());
		check("tick keeps lit", afterOne.lit());

		State run = lit;
		for (int i = 0; i < 100; i++) {
			run = PortableLightModel.tick(run);
		}
		eq("100 ticks -> 0", 0, run.remaining());
		check("auto extinguish at 0", !run.lit());
		check("after auto extinguish no light", !PortableLightModel.activeLight(run));

		State lastTick = PortableLightModel.readOffhand("minecraft:torch", 1, 1);
		State extinguished = PortableLightModel.tick(lastTick);
		eq("1 tick -> 0", 0, extinguished.remaining());
		check("1->0 immediate extinguish", !extinguished.lit());

		State paused = PortableLightModel.extinguish(lit);
		State pausedTick = paused;
		for (int i = 0; i < 10; i++) {
			pausedTick = PortableLightModel.tick(pausedTick);
		}
		eq("unlit fuel pause", 100, pausedTick.remaining());
		check("unlit stays unlit", !pausedTick.lit());

		State absent = PortableLightModel.readOffhand("minecraft:lantern", null, 1);
		eq("absent tick no-op", 0, PortableLightModel.tick(absent).remaining());
	}

	// ------------------------------------------------------------------
	// 6. Тушение / зажигание
	// ------------------------------------------------------------------

	private static void extinguishRelight() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 500, 1);
		State dark = PortableLightModel.extinguish(lit);
		check("extinguish sets unlit", !dark.lit());
		eq("extinguish preserves remaining", 500, dark.remaining());
		check("extinguished no light", !PortableLightModel.activeLight(dark));
		eq("extinguished emission", 0, PortableLightModel.emission(dark));

		State again = PortableLightModel.relight(dark);
		check("relight sets lit", again.lit());
		eq("relight preserves remaining", 500, again.remaining());
		check("relit active", PortableLightModel.activeLight(again));

		State empty = PortableLightModel.readOffhand("minecraft:torch", 0, 1);
		State emptyRelight = PortableLightModel.relight(empty);
		check("relight with no fuel refused", !emptyRelight.lit());

		State alreadyDark = PortableLightModel.extinguish(dark);
		check("extinguish idempotent", !alreadyDark.lit());
		eq("extinguish idempotent remaining", 500, alreadyDark.remaining());

		State corrupt = PortableLightModel.readOffhand("minecraft:torch", 100, 2);
		check("relight corrupt refused", !PortableLightModel.relight(corrupt).lit());
		check("extinguish corrupt no-op", !PortableLightModel.extinguish(corrupt).lit());
	}

	// ------------------------------------------------------------------
	// 7. Правила заправки
	// ------------------------------------------------------------------

	private static void refuelRules() {
		State emptyTorch = PortableLightModel.readOffhand("minecraft:torch", 0, 1);
		RefuelResult autoRefused = PortableLightModel.refuel(emptyTorch, true, false);
		eqObj("extinguished auto-refuel refused", RefuelStatus.REFUSED_EXTINGUISHED, autoRefused.status());
		check("extinguished auto-refuel not consumed", !autoRefused.consumed());
		eq("extinguished auto-refuel remaining", 0, autoRefused.state().remaining());

		RefuelResult explicit = PortableLightModel.refuel(emptyTorch, true, true);
		eqObj("extinguished explicit refuel ok", RefuelStatus.OK, explicit.status());
		check("explicit refuel consumed", explicit.consumed());
		eq("explicit refuel remaining", 12_000, explicit.state().remaining());
		check("explicit refuel lights", explicit.state().lit());

		State emptySoul = PortableLightModel.readOffhand("minecraft:soul_torch", 0, 1);
		RefuelResult soulFill = PortableLightModel.refuel(emptySoul, true, true);
		eqObj("soul torch refuel ok", RefuelStatus.OK, soulFill.status());
		eq("soul torch refuel remaining", 8_000, soulFill.state().remaining());

		State lit = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		RefuelResult full = PortableLightModel.refuel(lit, true, false);
		eqObj("torch overflow refused", RefuelStatus.FULL, full.status());
		check("torch overflow not consumed", !full.consumed());
		eq("torch overflow remaining unchanged", 6_000, full.state().remaining());

		State litSoul = PortableLightModel.readOffhand("minecraft:soul_torch", 4_000, 1);
		RefuelResult fullSoul = PortableLightModel.refuel(litSoul, true, false);
		eqObj("soul torch overflow refused", RefuelStatus.FULL, fullSoul.status());

		RefuelResult noFuel = PortableLightModel.refuel(lit, false, false);
		eqObj("no fuel in hand", RefuelStatus.NO_FUEL, noFuel.status());
		check("no fuel not consumed", !noFuel.consumed());

		State absent = PortableLightModel.readOffhand("minecraft:lantern", 0, 1);
		eqObj("absent refuel no fuel", RefuelStatus.NO_FUEL, PortableLightModel.refuel(absent, true, true).status());

		State corrupt = PortableLightModel.readOffhand("minecraft:torch", 100, 2);
		eqObj("corrupt refuel refused", RefuelStatus.REFUSED_CORRUPT,
				PortableLightModel.refuel(corrupt, true, true).status());

		check("fits exact capacity", PortableLightModel.fits(0, 12_000, 12_000));
		check("fits over capacity", !PortableLightModel.fits(1, 12_000, 12_000));
		check("fits soul exact", PortableLightModel.fits(0, 8_000, 8_000));
		check("fits zero addition", !PortableLightModel.fits(8_000, 0, 8_000));
	}

	// ------------------------------------------------------------------
	// 8. Независимость игроков
	// ------------------------------------------------------------------

	private static void twoPlayersIndependent() {
		State playerA = PortableLightModel.readOffhand("minecraft:torch", 100, 1);
		State playerB = PortableLightModel.readOffhand("minecraft:soul_torch", 0, 1);

		playerA = PortableLightModel.tick(playerA);
		eq("player A ticked", 99, playerA.remaining());
		eq("player B unlit pause", 0, playerB.remaining());
		check("player B unlit", !playerB.lit());

		playerA = PortableLightModel.extinguish(playerA);
		check("player A extinguished", !playerA.lit());
		check("player B not lit", !playerB.lit());
		eq("player B remaining after A extinguish", 0, playerB.remaining());

		playerA = PortableLightModel.tick(playerA);
		eq("player A unlit pause", 99, playerA.remaining());

		State playerBTicked = PortableLightModel.tick(playerB);
		eq("player B unlit pause after tick", 0, playerBTicked.remaining());

		RefuelResult refuelB = PortableLightModel.refuel(playerB, true, true);
		eqObj("player B refuel ok", RefuelStatus.OK, refuelB.status());
		eq("player B refuel remaining", 8_000, refuelB.state().remaining());
		check("player B lit after refuel", refuelB.state().lit());
		eq("player A unaffected by B refuel", 99, playerA.remaining());
		check("player A still unlit", !playerA.lit());

		State relitA = PortableLightModel.relight(playerA);
		check("player A relit", relitA.lit());
		eq("player A relight preserves", 99, relitA.remaining());
		eq("player B unaffected by A relight", 8_000, refuelB.state().remaining());
		check("player B still lit", refuelB.state().lit());
	}

	// ------------------------------------------------------------------
	// 9. Форматирование длительности
	// ------------------------------------------------------------------

	private static void durationFormatting() {
		eqs("format 0", "нет топлива", PortableLightModel.formatDuration(0));
		eqs("format negative", "нет топлива", PortableLightModel.formatDuration(-5));
		eqs("format 1t", "1с", PortableLightModel.formatDuration(1));
		eqs("format 19t", "1с", PortableLightModel.formatDuration(19));
		eqs("format 20t", "1с", PortableLightModel.formatDuration(20));
		eqs("format 21t", "2с", PortableLightModel.formatDuration(21));
		eqs("format 39t", "2с", PortableLightModel.formatDuration(39));
		eqs("format 40t", "2с", PortableLightModel.formatDuration(40));
		eqs("format 1199t", "1м 0с", PortableLightModel.formatDuration(1_199));
		eqs("format 1200t", "1м 0с", PortableLightModel.formatDuration(1_200));
		eqs("format 1219t", "1м 1с", PortableLightModel.formatDuration(1_219));
		eqs("format 6000t", "5м 0с", PortableLightModel.formatDuration(6_000));
		eqs("format 12000t", "10м 0с", PortableLightModel.formatDuration(12_000));
		eqs("format 24000t", "20м 0с", PortableLightModel.formatDuration(24_000));
		eqs("format 72000t", "1ч 0м", PortableLightModel.formatDuration(72_000));
		eqs("format 78000t", "1ч 5м", PortableLightModel.formatDuration(78_000));
		eqs("format 1000000t", "13ч 53м", PortableLightModel.formatDuration(1_000_000));

		boolean onlyRussianUnits = true;
		int[] samples = { 0, 1, 20, 1200, 24_000, 72_000, 1_000_000 };
		for (int t : samples) {
			String s = PortableLightModel.formatDuration(t);
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				boolean allowed = (c >= '0' && c <= '9') || c == ' ' || c == 'с' || c == 'м' || c == 'ч'
						|| c == 'н' || c == 'е' || c == 'т' || c == 'о' || c == 'п' || c == 'л' || c == 'и'
						|| c == 'в' || c == 'а';
				if (!allowed) {
					onlyRussianUnits = false;
				}
			}
		}
		check("duration uses only russian units/digits", onlyRussianUnits);
	}

	// ------------------------------------------------------------------
	// 10. Кастомная подпись часов вечной ночи
	// ------------------------------------------------------------------

	private static void eternalNightClockLabel() {
		eqs("clock 18000", "Ночь · 00:00", PortableLightModel.nightClockLabel(18_000));
		eqs("clock 0", "Ночь · 06:00", PortableLightModel.nightClockLabel(0));
		eqs("clock 6000", "Ночь · 12:00", PortableLightModel.nightClockLabel(6_000));
		eqs("clock 13000", "Ночь · 19:00", PortableLightModel.nightClockLabel(13_000));
		eqs("clock 23000", "Ночь · 05:00", PortableLightModel.nightClockLabel(23_000));
		eqs("clock 12:34", "Ночь · 12:34", PortableLightModel.nightClockLabel(6_567));
		eqs("clock negative normalizes", "Ночь · 21:00", PortableLightModel.nightClockLabel(-9_000));

		boolean neverMisleading = true;
		long[] samples = { 0, 6_000, 13_000, 18_000, 23_000, -9_000 };
		for (long t : samples) {
			if (PortableLightModel.isDayNightMisleading(PortableLightModel.nightClockLabel(t))) {
				neverMisleading = false;
			}
		}
		check("mod clock label never misleading day", neverMisleading);

		check("vanilla-style day label detected", PortableLightModel.isDayNightMisleading("Day 5"));
		check("plain day label detected", PortableLightModel.isDayNightMisleading("день 5"));

		eqs("clock label stable", PortableLightModel.nightClockLabel(18_000),
				PortableLightModel.nightClockLabel(18_000));
	}

	// ------------------------------------------------------------------
	// 11. Состояния кнопок
	// ------------------------------------------------------------------

	private static void buttonStates() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		check("lit zapravit enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, lit, true));
		check("lit potushit enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.POTUSHIT, lit, true));
		check("lit zazhech disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAZHECH, lit, true));

		State full = PortableLightModel.readOffhand("minecraft:torch", 12_000, 1);
		check("full zapravit disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, full, true));
		check("full potushit enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.POTUSHIT, full, true));

		State unlitWithFuel = PortableLightModel.readOffhand("minecraft:torch", 5_000, 1);
		unlitWithFuel = PortableLightModel.extinguish(unlitWithFuel);
		check("unlit-with-fuel zapravit enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, unlitWithFuel, true));
		check("unlit-with-fuel potushit disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.POTUSHIT, unlitWithFuel, true));
		check("unlit-with-fuel zazhech enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAZHECH, unlitWithFuel, true));

		State unlitEmpty = PortableLightModel.readOffhand("minecraft:torch", 0, 1);
		check("unlit-empty zapravit enabled",
				PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, unlitEmpty, true));
		check("unlit-empty zazhech disabled",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAZHECH, unlitEmpty, true));
		check("unlit-empty zapravit disabled without fuel",
				!PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, unlitEmpty, false));

		State absent = PortableLightModel.readOffhand("minecraft:lantern", 500, 1);
		for (PortableLightModel.Button b : PortableLightModel.Button.values()) {
			check("absent button disabled " + b,
					!PortableLightModel.buttonEnabled(b, absent, true));
		}

		eqs("button label zapravit", "Заправить", PortableLightModel.buttonLabel(PortableLightModel.Button.ZAPRAVIT));
		eqs("button label potushit", "Потушить", PortableLightModel.buttonLabel(PortableLightModel.Button.POTUSHIT));
		eqs("button label zazhech", "Зажечь", PortableLightModel.buttonLabel(PortableLightModel.Button.ZAZHECH));
	}

	// ------------------------------------------------------------------
	// 11b. Действия с плацед-источником (action state)
	// ------------------------------------------------------------------

	private static void sourceActions() {
		// Тушение сохраняет остаток.
		SourceActionModel.Src lit = new SourceActionModel.Src(true, 5_000);
		SourceActionModel.Src dark = SourceActionModel.extinguish(lit);
		check("extinguish preserves remaining", dark.remaining() == 5_000);
		check("extinguish sets unlit", !dark.lit());
		check("extinguish can", SourceActionModel.canExtinguish(true));
		check("extinguish can't when unlit", !SourceActionModel.canExtinguish(false));

		// Зажигание сохраняет остаток; невозможно без остатка.
		SourceActionModel.Src again = SourceActionModel.relight(dark);
		check("relight preserves remaining", again.remaining() == 5_000);
		check("relight sets lit", again.lit());
		check("relight needs fuel", !SourceActionModel.canRelight(false, 0, false));
		check("relight can with fuel", SourceActionModel.canRelight(false, 5_000, false));
		check("relight refused managed", !SourceActionModel.canRelight(false, 5_000, true));

		// Статусы тушения/зажигания.
		eq("outcome extinguish lit", SourceActionModel.STATUS_OK,
				SourceActionModel.outcome(SourceActionModel.ACTION_EXTINGUISH, true, 100, 12_000, false, false, 0));
		eq("outcome extinguish already unlit", SourceActionModel.STATUS_ALREADY_UNLIT,
				SourceActionModel.outcome(SourceActionModel.ACTION_EXTINGUISH, false, 100, 12_000, false, false, 0));
		eq("outcome relight unlit with fuel", SourceActionModel.STATUS_OK,
				SourceActionModel.outcome(SourceActionModel.ACTION_RELIGHT, false, 100, 12_000, false, false, 0));
		eq("outcome relight already lit", SourceActionModel.STATUS_ALREADY_LIT,
				SourceActionModel.outcome(SourceActionModel.ACTION_RELIGHT, true, 100, 12_000, false, false, 0));
		eq("outcome relight no fuel", SourceActionModel.STATUS_NO_FUEL_TO_LIGHT,
				SourceActionModel.outcome(SourceActionModel.ACTION_RELIGHT, false, 0, 12_000, false, false, 0));

		// Заправка.
		eq("outcome refuel accepted", SourceActionModel.STATUS_ACCEPTED,
				SourceActionModel.outcome(SourceActionModel.ACTION_REFUEL, true, 0, 12_000, false, true, 12_000));
		eq("outcome refuel overflow", SourceActionModel.STATUS_FULL,
				SourceActionModel.outcome(SourceActionModel.ACTION_REFUEL, true, 6_000, 12_000, false, true, 12_000));
		eq("outcome refuel no fuel no relight", SourceActionModel.STATUS_NO_FUEL,
				SourceActionModel.outcome(SourceActionModel.ACTION_REFUEL, true, 6_000, 12_000, false, false, 0));
		eq("outcome refuel no fuel relights unlit", SourceActionModel.STATUS_OK,
				SourceActionModel.outcome(SourceActionModel.ACTION_REFUEL, false, 6_000, 12_000, false, false, 0));

		// managedByPost: inspect открыт, действия запрещены.
		for (int action : new int[] { SourceActionModel.ACTION_REFUEL, SourceActionModel.ACTION_EXTINGUISH,
				SourceActionModel.ACTION_RELIGHT }) {
			eq("outcome managed action " + action, SourceActionModel.STATUS_MANAGED_BY_POST,
					SourceActionModel.outcome(action, true, 5_000, 12_000, true, true, 12_000));
			check("managed button disabled " + action,
					!SourceActionModel.buttonEnabled(action, true, 5_000, 12_000, true, true, 12_000));
		}

		// Доступность кнопок (валидный источник).
		check("refuel enabled not full", SourceActionModel.buttonEnabled(SourceActionModel.ACTION_REFUEL,
				true, 1_000, 12_000, false, true, 12_000));
		check("refuel disabled full", !SourceActionModel.buttonEnabled(SourceActionModel.ACTION_REFUEL,
				true, 12_000, 12_000, false, true, 12_000));
		check("refuel disabled no fuel", !SourceActionModel.buttonEnabled(SourceActionModel.ACTION_REFUEL,
				true, 1_000, 12_000, false, false, 0));
		check("extinguish enabled lit", SourceActionModel.buttonEnabled(SourceActionModel.ACTION_EXTINGUISH,
				true, 1_000, 12_000, false, false, 0));
		check("extinguish disabled unlit", !SourceActionModel.buttonEnabled(SourceActionModel.ACTION_EXTINGUISH,
				false, 1_000, 12_000, false, false, 0));
		check("relight enabled unlit fuel", SourceActionModel.buttonEnabled(SourceActionModel.ACTION_RELIGHT,
				false, 1_000, 12_000, false, false, 0));
		check("relight disabled lit", !SourceActionModel.buttonEnabled(SourceActionModel.ACTION_RELIGHT,
				true, 1_000, 12_000, false, false, 0));

		// Статусы различимы.
		boolean distinct = SourceActionModel.STATUS_OPEN_OK != SourceActionModel.STATUS_OK
				&& SourceActionModel.STATUS_OK != SourceActionModel.STATUS_ACCEPTED
				&& SourceActionModel.STATUS_FULL != SourceActionModel.STATUS_NO_FUEL
				&& SourceActionModel.STATUS_MANAGED_BY_POST != SourceActionModel.STATUS_REFUSED
				&& SourceActionModel.STATUS_ALREADY_LIT != SourceActionModel.STATUS_ALREADY_UNLIT;
		check("action statuses distinct", distinct);
	}

	// ------------------------------------------------------------------
	// 12. Раскладка — широкая
	// ------------------------------------------------------------------

	private static void layoutWide() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		View view = View.from(lit, true, 18_000);
		Layout layout = PortableLightLayout.compute(260, 120, view);

		check("wide not compact", !layout.compact());
		check("wide panel within available", layout.panelWidth() <= 260);
		check("wide panel height within available", layout.panelHeight() <= 120);
		eq("wide row count", 4, layout.rows().size());
		eq("wide button count", 3, layout.buttons().size());

		eqs("wide title", PortableLightLayout.TITLE, layout.rows().get(0).label());
		contains("wide status kind", layout.rows().get(1).label(), "Факел");
		contains("wide fuel prefix", layout.rows().get(2).label(), "Топливо:");
		contains("wide fuel duration", layout.rows().get(2).label(), "5м 0с");
		contains("wide time label", layout.rows().get(3).label(), "Ночь");

		eqs("wide button0", "Заправить", layout.buttons().get(0).label());
		eqs("wide button1", "Потушить", layout.buttons().get(1).label());
		eqs("wide button2", "Зажечь", layout.buttons().get(2).label());
		check("wide zapravit enabled", layout.buttons().get(0).enabled());
		check("wide potushit enabled", layout.buttons().get(1).enabled());
		check("wide zazhech disabled", !layout.buttons().get(2).enabled());

		// Горизонтальные кнопки: одна строка, x возрастает, нет наложения.
		boolean sameY = layout.buttons().get(0).rect().y() == layout.buttons().get(1).rect().y()
				&& layout.buttons().get(1).rect().y() == layout.buttons().get(2).rect().y();
		boolean ordered = layout.buttons().get(0).rect().right() + PortableLightLayout.GAP
				<= layout.buttons().get(1).rect().x()
				&& layout.buttons().get(1).rect().right() + PortableLightLayout.GAP
						<= layout.buttons().get(2).rect().x();
		check("wide buttons single row", sameY);
		check("wide buttons ordered no overlap", ordered);
	}

	// ------------------------------------------------------------------
	// 13. Раскладка — узкие ширины без переполнения
	// ------------------------------------------------------------------

	private static void layoutNarrowNoOverflow() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		View view = View.from(lit, true, 18_000);

		int[] widths = { 260, 190, 170, 140, 118, 108, 92, 84, 60 };
		for (int w : widths) {
			Layout layout = PortableLightLayout.compute(w, 120, view);
			String tag = "w" + w;

			check(tag + " panel within width", layout.panelWidth() <= w);
			check(tag + " panel within height", layout.panelHeight() <= 120);
			check(tag + " compact flag", layout.compact() == (w < PortableLightLayout.HORIZONTAL_BUTTONS_MIN_WIDTH));

			boolean rowsWithin = true;
			boolean rowsOrdered = true;
			for (int i = 0; i < layout.rows().size(); i++) {
				Row row = layout.rows().get(i);
				if (row.rect().x() < 0 || row.rect().right() > layout.panelWidth()
						|| row.rect().bottom() > layout.panelHeight() || !fitsInner(row.label(), layout.panelWidth())) {
					rowsWithin = false;
				}
				if (i > 0 && layout.rows().get(i - 1).rect().bottom() > row.rect().y()) {
					rowsOrdered = false;
				}
			}
			check(tag + " rows within panel", rowsWithin);
			check(tag + " rows ordered", rowsOrdered);

			boolean buttonsWithin = true;
			boolean buttonsOrdered = true;
			int before = 0;
			for (int i = 0; i < layout.buttons().size(); i++) {
				Button b = layout.buttons().get(i);
				if (b.rect().x() < 0 || b.rect().right() > layout.panelWidth()
						|| b.rect().bottom() > layout.panelHeight()
						|| !fitsInner(b.label(), layout.panelWidth())) {
					buttonsWithin = false;
				}
				if (i > 0 && !layout.compact() && layout.buttons().get(i - 1).rect().right() > b.rect().x()) {
					buttonsOrdered = false;
				}
				if (i > 0 && layout.compact() && layout.buttons().get(i - 1).rect().y() >= b.rect().y()) {
					buttonsOrdered = false;
				}
				before++;
			}
			check(tag + " buttons within panel", buttonsWithin);
			check(tag + " buttons ordered", buttonsOrdered);
			check(tag + " buttons don't overlap rows", layout.rows().isEmpty()
					|| layout.buttons().get(0).rect().y() >= layout.rows().get(layout.rows().size() - 1).rect().bottom());
			eq(tag + " button count", 3, before);
		}

		// Очень узкая: панель равна доступной ширине, кнопки вертикальные компактные.
		Layout narrow = PortableLightLayout.compute(60, 120, view);
		eq("narrow panel == available", 60, narrow.panelWidth());
		check("narrow compact", narrow.compact());
		check("narrow buttons stacked",
				narrow.buttons().get(0).rect().y() < narrow.buttons().get(1).rect().y()
						&& narrow.buttons().get(1).rect().y() < narrow.buttons().get(2).rect().y());
		check("narrow all buttons same x", narrow.buttons().get(0).rect().x() == narrow.buttons().get(1).rect().x()
				&& narrow.buttons().get(1).rect().x() == narrow.buttons().get(2).rect().x());

		// Широкая всё ещё горизонтальная.
		Layout wide = PortableLightLayout.compute(260, 120, view);
		check("wide buttons same y", wide.buttons().get(0).rect().y() == wide.buttons().get(1).rect().y());
	}

	// ------------------------------------------------------------------
	// 14. НОВАЯ семантика топлива: повторная заправка 20% без изменения lit
	// ------------------------------------------------------------------

	private static void fuelStatePartialRefuel() {
		int torchCap = FuelStateModel.CAPACITY_TORCH;
		int coalTorch = FuelStateModel.coalAddition(torchCap);

		// Уголь = ровно 20% ёмкости для всех принимающих уголь видов.
		eq("coal torch 20%", 2_400, coalTorch);
		eq("coal soul torch 20%", 1_600, FuelStateModel.coalAddition(FuelStateModel.CAPACITY_SOUL_TORCH));
		eq("coal lantern 20%", 4_800, FuelStateModel.coalAddition(FuelStateModel.CAPACITY_LANTERN));
		eq("coal soul lantern 20%", 3_200, FuelStateModel.coalAddition(FuelStateModel.CAPACITY_SOUL_LANTERN));
		eq("coal campfire 20%", 3_200, FuelStateModel.coalAddition(FuelStateModel.CAPACITY_CAMPFIRE));
		// Палочка/бревно костра — НЕ уголь, прежние значения сохранены.
		eq("campfire stick preserved", 2_000, FuelStateModel.ADD_CAMPFIRE_STICK);
		eq("campfire log preserved", 8_000, FuelStateModel.ADD_CAMPFIRE_LOG);

		// «Осталось 20% → +20%», lit не меняется.
		FuelStateModel.FuelState twenty = new FuelStateModel.FuelState(2_400, true);
		FuelStateModel.RefuelResult partial = FuelStateModel.refuelPartial(twenty, torchCap, coalTorch, true);
		eqObj("partial status ok", FuelStateModel.RefuelStatus.OK, partial.status());
		eq("partial 20->40%", 4_800, partial.state().remaining());
		check("partial consumed", partial.consumed());
		check("partial keeps lit", partial.state().lit());
		check("partial still burning", partial.state().burning());

		// Погашенный источник: заправка добавляет топливо, но НЕ зажигает.
		FuelStateModel.FuelState unlit = new FuelStateModel.FuelState(2_400, false);
		FuelStateModel.RefuelResult unlitRefuel = FuelStateModel.refuelPartial(unlit, torchCap, coalTorch, true);
		eqObj("unlit refuel status ok", FuelStateModel.RefuelStatus.OK, unlitRefuel.status());
		eq("unlit refuel adds fuel", 4_800, unlitRefuel.state().remaining());
		check("unlit refuel stays unlit", !unlitRefuel.state().lit());
		check("unlit refuel not burning", !unlitRefuel.state().burning());

		// Повторная заправка до переполнения — без частичного списания.
		FuelStateModel.FuelState nearlyFull = new FuelStateModel.FuelState(torchCap - 100, true);
		FuelStateModel.RefuelResult overflow = FuelStateModel.refuelPartial(nearlyFull, torchCap, coalTorch, true);
		eqObj("overflow status full", FuelStateModel.RefuelStatus.FULL, overflow.status());
		check("overflow not consumed", !overflow.consumed());
		eq("overflow unchanged", torchCap - 100, overflow.state().remaining());
		check("overflow keeps lit", overflow.state().lit());

		// Нет топлива в руке.
		FuelStateModel.RefuelResult noFuel = FuelStateModel.refuelPartial(twenty, torchCap, coalTorch, false);
		eqObj("no fuel status", FuelStateModel.RefuelStatus.NO_FUEL, noFuel.status());
		check("no fuel not consumed", !noFuel.consumed());

		// Ровно по ёмкости помещается.
		FuelStateModel.FuelState exact = new FuelStateModel.FuelState(torchCap - coalTorch, true);
		FuelStateModel.RefuelResult exactRes = FuelStateModel.refuelPartial(exact, torchCap, coalTorch, true);
		eqObj("exact fit ok", FuelStateModel.RefuelStatus.OK, exactRes.status());
		eq("exact fit capacity", torchCap, exactRes.state().remaining());

		// Заправка (даже явная) НИКОГДА не включает погашенный источник.
		FuelStateModel.FuelState extinguished = FuelStateModel.extinguish(twenty);
		check("extinguish unlit", !extinguished.lit());
		eq("extinguish preserves remaining", 2_400, extinguished.remaining());
		FuelStateModel.RefuelResult afterExt = FuelStateModel.refuelPartial(extinguished, torchCap, coalTorch, true);
		check("refuel after extinguish stays unlit", !afterExt.state().lit());
		eq("refuel after extinguish adds fuel", 4_800, afterExt.state().remaining());

		// Зажечь — единственное действие unlit->lit, без расхода.
		FuelStateModel.FuelState relit = FuelStateModel.relight(extinguished);
		check("relight sets lit", relit.lit());
		eq("relight preserves remaining", 2_400, relit.remaining());
		check("relight burning", relit.burning());
		check("relight refused empty", !FuelStateModel.relight(FuelStateModel.FuelState.empty()).lit());
		FuelStateModel.FuelState alreadyLit = new FuelStateModel.FuelState(500, true);
		check("relight idempotent", FuelStateModel.relight(alreadyLit).equals(alreadyLit));

		// Формальный инвариант: refuelPartial(N) не меняет lit ни при каком N.
		boolean litInvariant = true;
		for (boolean lit : new boolean[] { true, false }) {
			for (int rem : new int[] { 0, 100, 2_400, 11_900, 12_000 }) {
				FuelStateModel.FuelState s = new FuelStateModel.FuelState(rem, lit);
				FuelStateModel.RefuelResult r = FuelStateModel.refuelPartial(s, torchCap, coalTorch, true);
				if (r.state().lit() != lit) {
					litInvariant = false;
				}
			}
		}
		check("refuel never mutates lit", litInvariant);
	}

	// ------------------------------------------------------------------
	// 15. Персистентность place/drop + расход в инвентаре + переносной свет
	// ------------------------------------------------------------------

	private static void fuelStatePersistenceAndInventory() {
		// place/drop roundtrip сохраняет И remaining, И lit.
		FuelStateModel.FuelState item = new FuelStateModel.FuelState(7_000, true);
		FuelStateModel.FuelState block = FuelStateModel.placeBlock(item);
		eq("place preserves remaining", 7_000, block.remaining());
		check("place preserves lit", block.lit());
		FuelStateModel.FuelState dropped = FuelStateModel.dropItem(block);
		eq("drop preserves remaining", 7_000, dropped.remaining());
		check("drop preserves lit", dropped.lit());
		check("place/drop roundtrip identity", item.equals(dropped));

		FuelStateModel.FuelState unlitItem = new FuelStateModel.FuelState(3_000, false);
		check("unlit roundtrip identity",
				unlitItem.equals(FuelStateModel.dropItem(FuelStateModel.placeBlock(unlitItem))));
		FuelStateModel.FuelState emptyItem = FuelStateModel.FuelState.empty();
		check("empty roundtrip identity",
				emptyItem.equals(FuelStateModel.dropItem(FuelStateModel.placeBlock(emptyItem))));

		// Расход горящего предмета в инвентаре (не в левой руке) — тикается каждый серверный тик.
		FuelStateModel.FuelState burning = new FuelStateModel.FuelState(5, true);
		FuelStateModel.FuelState t1 = FuelStateModel.tickInventory(burning);
		eq("inventory tick decrements", 4, t1.remaining());
		check("inventory tick keeps lit", t1.lit());
		FuelStateModel.FuelState run = new FuelStateModel.FuelState(5, true);
		for (int i = 0; i < 5; i++) {
			run = FuelStateModel.tickInventory(run);
		}
		eq("inventory 5->0", 0, run.remaining());
		check("inventory 5->0 extinguishes", !run.lit());
		FuelStateModel.FuelState last = FuelStateModel.tickInventory(new FuelStateModel.FuelState(1, true));
		eq("inventory 1->0", 0, last.remaining());
		check("inventory 1->0 extinguishes", !last.lit());
		FuelStateModel.FuelState paused = FuelStateModel.tickInventory(new FuelStateModel.FuelState(50, false));
		eq("unlit inventory no drain", 50, paused.remaining());
		check("unlit inventory stays unlit", !paused.lit());

		// Переносной свет — только из левой руки, только горящий и целый.
		eq("offhand torch lit emits 14", 14, FuelStateModel.offhandEmission(100, true, 1, 14));
		eq("offhand soul lit emits 10", 10, FuelStateModel.offhandEmission(100, true, 1, 10));
		eq("offhand unlit no light", 0, FuelStateModel.offhandEmission(100, false, 1, 14));
		eq("offhand empty no light", 0, FuelStateModel.offhandEmission(0, true, 1, 14));
		eq("offhand count2 no light", 0, FuelStateModel.offhandEmission(100, true, 2, 14));
		eq("offhand clamps emission", 15, FuelStateModel.offhandEmission(100, true, 1, 99));

		// Порча стека: заряженный count>1 invalid/no split; пустой count>1 не «заряжен».
		check("charged count2 invalid", FuelStateModel.invalidChargedStack(100, 2));
		check("charged count1 valid", !FuelStateModel.invalidChargedStack(100, 1));
		check("empty count2 not invalid", !FuelStateModel.invalidChargedStack(0, 2));
		check("usable charged count1", FuelStateModel.usableCharged(100, 1));
		check("not usable charged count2", !FuelStateModel.usableCharged(100, 2));
		check("not usable empty", !FuelStateModel.usableCharged(0, 1));

		// Нормализация ёмкости.
		eq("normalize over capacity", 12_000,
				new FuelStateModel.FuelState(20_000, true).normalized(FuelStateModel.CAPACITY_TORCH).remaining());
		eq("negative remaining clamps to 0", 0, new FuelStateModel.FuelState(-5, false).remaining());
		check("clamped negative not burning", !new FuelStateModel.FuelState(-5, true).burning());
	}

	// ------------------------------------------------------------------
	// 16. Строки HUD: без «свет 14» и без сырых тиков
	// ------------------------------------------------------------------

	private static void portableUiStrings() {
		eqs("held torch", PortableUiModel.HELD_TORCH, PortableUiModel.heldLine("Факел", true));
		eqs("held soul torch", PortableUiModel.HELD_SOUL_TORCH, PortableUiModel.heldLine("Факел душ", true));
		eqs("held absent when not present", PortableUiModel.HELD_ABSENT, PortableUiModel.heldLine("Факел", false));
		eqs("held absent null name", PortableUiModel.HELD_ABSENT, PortableUiModel.heldLine(null, true));

		check("held torch no light level", !PortableUiModel.containsLightLevel(PortableUiModel.HELD_TORCH));
		check("held soul no light level", !PortableUiModel.containsLightLevel(PortableUiModel.HELD_SOUL_TORCH));
		check("light level detector on old string", PortableUiModel.containsLightLevel("В руке: Факел (свет 14)"));

		eqs("fuel 12000", "Осталось: 10 мин 0 сек", PortableUiModel.fuelLine(12_000));
		eqs("fuel 6000", "Осталось: 5 мин 0 сек", PortableUiModel.fuelLine(6_000));
		eqs("fuel 1200", "Осталось: 1 мин 0 сек", PortableUiModel.fuelLine(1_200));
		eqs("fuel 20", "Осталось: 1 сек", PortableUiModel.fuelLine(20));
		eqs("fuel 0", "Осталось: нет топлива", PortableUiModel.fuelLine(0));
		eqs("duration 1219", "1 мин 1 сек", PortableUiModel.durationWords(1_219));
		eqs("duration 59000t", "49 мин 10 сек", PortableUiModel.durationWords(59_000));
		eqs("duration 72000t", "1 ч 0 мин", PortableUiModel.durationWords(72_000));

		boolean noRawTicks = true;
		int[] samples = { 0, 1, 20, 1_200, 12_000, 72_000, 1_000_000 };
		for (int t : samples) {
			if (PortableUiModel.containsRawFuelTicks(PortableUiModel.fuelLine(t))) {
				noRawTicks = false;
			}
		}
		check("fuel lines no raw ticks", noRawTicks);
		check("raw ticks detector narrow", PortableUiModel.containsRawFuelTicks("Топливо: 12000т"));
		check("raw ticks detector spaced", PortableUiModel.containsRawFuelTicks("12000 т"));
		check("no false positive on net topliva", !PortableUiModel.containsRawFuelTicks("нет топлива"));
		check("raw ticks detector on old line", PortableUiModel.containsRawFuelTicks("Топливо: 6000т (5м 0с)"));
	}

	// ------------------------------------------------------------------
	// 16b. Строка источника HUD: переносной свет считается источником для показа
	// ------------------------------------------------------------------

	/**
	 * Тикет: при валидном горящем offhand без поставленного блока HUD не должен писать «нет рядом».
	 * Поставленный блок в приоритете; пустой/погасший/испорченный offhand → «нет рядом».
	 */
	private static void hudSourceStrings() {
		eqs("hud placed lit", "Источник: Факел · горит",
				PortableUiModel.hudSourceLine(true, 0, true, false, -1));
		eqs("hud placed unlit", "Источник: Факел · погас",
				PortableUiModel.hudSourceLine(true, 0, false, false, -1));
		// Поставленный блок в приоритете даже при активном offhand.
		eqs("hud placed wins over portable", "Источник: Фонарь · горит",
				PortableUiModel.hudSourceLine(true, 2, true, true, 0));

		eqs("hud portable torch", "Источник: в руке — Факел",
				PortableUiModel.hudSourceLine(false, -1, false, true, 0));
		eqs("hud portable soul torch", "Источник: в руке — Факел душ",
				PortableUiModel.hudSourceLine(false, -1, false, true, 1));
		eqs("hud absent when portable inactive", "Источник: нет рядом",
				PortableUiModel.hudSourceLine(false, -1, false, false, 0));

		eqs("hud fuel placed", "Осталось: 10 мин 0 сек",
				PortableUiModel.hudFuelLine(true, 12_000, false, 0));
		eqs("hud fuel portable", "Осталось: 2 мин 0 сек",
				PortableUiModel.hudFuelLine(false, 0, true, 2_400));
		eqs("hud fuel none", "Осталось: —",
				PortableUiModel.hudFuelLine(false, 0, false, 0));

		// Никаких сырых тиков/уровня света в новых строках.
		boolean clean = true;
		String[] lines = {
				PortableUiModel.hudSourceLine(true, 0, true, false, -1),
				PortableUiModel.hudSourceLine(false, -1, false, true, 0),
				PortableUiModel.hudFuelLine(false, 0, true, 12_000)
		};
		for (String line : lines) {
			if (PortableUiModel.containsRawFuelTicks(line) || PortableUiModel.containsLightLevel(line)) {
				clean = false;
			}
		}
		check("hud source strings clean", clean);
	}

	// ------------------------------------------------------------------
	// 17. «No distant darkness pulse»: постоянный выход по partial tick и дальний туман
	// ------------------------------------------------------------------

	private static void noPulseVisualOutputs() {
		float eff = 1.0f;
		float our = NoPulseVisualModel.darknessOffset(eff);
		float minOur = Float.MAX_VALUE;
		float maxOur = -Float.MAX_VALUE;
		float minVanilla = Float.MAX_VALUE;
		float maxVanilla = -Float.MAX_VALUE;
		for (int tick = 0; tick <= 80; tick++) {
			for (int i = 0; i <= 8; i++) {
				float pt = i / 8.0f;
				float o = NoPulseVisualModel.darknessOffset(eff);
				float v = NoPulseVisualModel.vanillaDarknessScale(tick, pt, eff);
				minOur = Math.min(minOur, o);
				maxOur = Math.max(maxOur, o);
				minVanilla = Math.min(minVanilla, v);
				maxVanilla = Math.max(maxVanilla, v);
			}
		}
		eq("our offset constant across cycle", 0, Float.compare(minOur, maxOur));
		check("vanilla darkness pulses over cycle", maxVanilla - minVanilla > 0.4f);
		check("our offset is 0.16", Math.abs(our - 0.16f) < 1e-6f);
		check("our offset below vanilla peak", our < 0.45f);

		// В пределах ОДНОГО тика наш выход не меняется по partial tick; ванильный — меняется.
		float oMin = Float.MAX_VALUE;
		float oMax = -Float.MAX_VALUE;
		float vMin = Float.MAX_VALUE;
		float vMax = -Float.MAX_VALUE;
		float tick10 = 10.0f;
		for (int i = 0; i <= 40; i++) {
			float pt = i / 40.0f;
			float o = NoPulseVisualModel.darknessOffset(eff);
			float v = NoPulseVisualModel.vanillaDarknessScale(tick10, pt, eff);
			oMin = Math.min(oMin, o);
			oMax = Math.max(oMax, o);
			vMin = Math.min(vMin, v);
			vMax = Math.max(vMax, v);
		}
		eq("offset constant within a tick", 0, Float.compare(oMin, oMax));
		check("vanilla varies within a tick", vMax - vMin > 1e-4f);

		// eff=0 → ровно vanilla no-op.
		eq("eff0 offset zero", 0, Float.compare(NoPulseVisualModel.darknessOffset(0f), 0f));
		eq("eff0 brightness zero", 0, Float.compare(NoPulseVisualModel.brightnessFloor(0f), 0f));
		eq("eff0 fog vanilla", 0, Float.compare(NoPulseVisualModel.fogEnd(15f, 0f), 15f));
		eq("eff0 fog factor vanilla", 0, Float.compare(NoPulseVisualModel.fogDarknessFactor(0f, 1f, 0f), 1f));

		// Дальний туман: не ближе 48 → нет near-black; ванильный туман не сужается.
		check("fog far at eff1", NoPulseVisualModel.fogEnd(15f, 1f) >= NoPulseVisualModel.MIN_FOG_DISTANCE);
		eq("fog reaches 48", 0, Float.compare(NoPulseVisualModel.fogEnd(15f, 1f), 48f));
		check("fog never shrinks", NoPulseVisualModel.fogEnd(64f, 1f) >= 64f);

		// Фактор затемнения цвета capped; void (тьма ниже мира) сохранён.
		check("fog factor capped", NoPulseVisualModel.fogDarknessFactor(0f, 1f, 1f)
				<= NoPulseVisualModel.MAX_FOG_DARKNESS + 1e-6f);
		check("fog factor not black", NoPulseVisualModel.fogDarknessFactor(0f, 1f, 1f) < 1f);
		eq("fog void preserved", 0, Float.compare(NoPulseVisualModel.fogDarknessFactor(0.9f, 1f, 1f), 0.9f));

		// Огибающая: bounded, монотонная, доходит до цели.
		float env = 0f;
		boolean bounded = true;
		boolean monotonic = true;
		float prev = env;
		for (int i = 0; i < 600; i++) {
			env = NoPulseVisualModel.approach(env, 1f, 2f, 1.0 / 60.0);
			if (env < 0f || env > 1f) {
				bounded = false;
			}
			if (env < prev - 1e-6f) {
				monotonic = false;
			}
			prev = env;
		}
		check("envelope bounded", bounded);
		check("envelope monotonic", monotonic);
		check("envelope reaches 1", Math.abs(env - 1f) < 1e-6f);
		float jump = NoPulseVisualModel.approach(0f, 1f, 2f, 100.0);
		check("envelope clamps huge dt", jump <= 2f * (float) NoPulseVisualModel.MAX_FRAME_SECONDS + 1e-6f);

		// Ни один partial tick не даёт «пульсового» разброса больше нуля.
		java.util.TreeSet<Float> distinct = new java.util.TreeSet<>();
		for (int i = 0; i <= 100; i++) {
			distinct.add(NoPulseVisualModel.darknessOffset(0.7f));
		}
		eq("single distinct offset", 1, distinct.size());
		check("offset scales linearly with eff",
				Math.abs(NoPulseVisualModel.darknessOffset(0.7f) - 0.112f) < 1e-6f);
	}

	// ------------------------------------------------------------------
	// 18. Кнопки меню источника не переполняются (в т.ч. на узких ширинах)
	// ------------------------------------------------------------------

	private static void sourceMenuButtonsNoOverflow() {
		State lit = PortableLightModel.readOffhand("minecraft:torch", 6_000, 1);
		View view = View.from(lit, true, 18_000);

		int[] widths = { 320, 260, 200, 190, 160, 120, 96, 84, 60, 40 };
		for (int w : widths) {
			Layout layout = PortableLightLayout.compute(w, 200, view);
			check("menu w" + w + " panel within width", layout.panelWidth() <= w);
			boolean within = true;
			for (Button b : layout.buttons()) {
				if (b.rect().x() < 0 || b.rect().right() > layout.panelWidth()
						|| b.rect().bottom() > layout.panelHeight()
						|| PortableLightLayout.textWidth(b.label()) > layout.panelWidth()) {
					within = false;
				}
			}
			check("menu w" + w + " buttons within panel", within);
		}

		// Подписи кнопок совпадают с реальными действиями источника.
		Layout wide = PortableLightLayout.compute(300, 200, view);
		eqs("menu button0 label", PortableLightModel.buttonLabel(PortableLightModel.Button.ZAPRAVIT),
				wide.buttons().get(0).label());
		eqs("menu button1 label", PortableLightModel.buttonLabel(PortableLightModel.Button.POTUSHIT),
				wide.buttons().get(1).label());
		eqs("menu button2 label", PortableLightModel.buttonLabel(PortableLightModel.Button.ZAZHECH),
				wide.buttons().get(2).label());

		// Очень узкая панель: компактные подписи вертикально и в границах.
		Layout tiny = PortableLightLayout.compute(40, 200, view);
		eq("tiny panel equals available", 40, tiny.panelWidth());
		check("tiny buttons stacked", tiny.buttons().get(0).rect().y() < tiny.buttons().get(1).rect().y()
				&& tiny.buttons().get(1).rect().y() < tiny.buttons().get(2).rect().y());
		boolean tinyWithin = true;
		for (Button b : tiny.buttons()) {
			if (b.rect().x() < 0 || b.rect().right() > tiny.panelWidth()
					|| b.rect().bottom() > tiny.panelHeight()) {
				tinyWithin = false;
			}
		}
		check("tiny buttons within panel", tinyWithin);
	}
}
