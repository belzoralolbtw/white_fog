package com.whitefog.tests.darkness;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded self-test сервера вечной ночи и воздействия тьмы (этап 1.5).
 *
 * <p><b>Это чистая логика без Minecraft</b> и НЕ доказательство runtime-поведения
 * (эффекты, attributes, clock, сеть, реальные Level#getBrightness/canSeeSky).
 * Реальная проверка — сборка мода + запуск игры архитектором.</p>
 *
 * <p>Само-завершение: внутренний watchdog жёстко убивает JVM через {@value #HARD_TIMEOUT_MS} мс
 * и оставляет код возврата 124 ({@code TIMEOUT}).</p>
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
		}, "eternal-darkness-test-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog eternal darkness / light exposure sandbox self-test (logic only, NOT runtime) ===");
		long begin = System.nanoTime();

		lightThresholdsAndDeltas();
		canSeeSkyAndShelter();
		exemptAndDead();
		victorySafe();
		capBoundaries();
		deterministic();
		oracleOpenDarkness();
		oracleBrightLight();
		neutralResetsSafeTimer();
		conditionThresholds();
		conditionDrainExact();
		conditionRecovery();
		flagThresholds();
		repeatedTick();
		sampleChunks19And1();
		sampleChunks10And10();
		serializationRoundTrip();
		saveAtRemainder19();
		migrationFromLegacyCondition();
		disconnectWithoutOfflineCatchUp();
		noModifierAccumulation();
		exemptPauseAndResume();
		unloadedEyeSkipsSample();
		netherNoSkylightGate();
		twoPlayersDifferentExposure();
		snapshotHeartbeatAndMinInterval();

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

	private static void lightThresholdsAndDeltas() {
		section("1. свет 4/5/8/9 — дельты exposure");
		check("light4 dark + sky открыт → +2", result(0, light(4, true, false)).exposure() == 2, "e");
		check("light4 dark + sky закрыт → +1", result(0, light(4, false, false)).exposure() == 1, "e");
		check("light5 → 0", result(50, light(5, true, false)).exposure() == 50, "e");
		check("light8 → 0", result(50, light(8, true, false)).exposure() == 50, "e");
		check("light9 → -2", result(50, light(9, false, false)).exposure() == 48, "e");
	}

	private static void canSeeSkyAndShelter() {
		section("2. canSeeSky true/false и укрытие (адаптер-заглушка shelter=false)");
		check("sky=true, shelter=false → +2", result(0, light(0, true, false)).exposure() == 2, "e");
		check("sky=false, shelter=false → +1", result(0, light(0, false, false)).exposure() == 1, "e");
		check("sky=true, shelter=true → +1 (укрытие гасит бонус)", result(0, light(0, true, true)).exposure() == 1,
				"e=" + result(0, light(0, true, true)).exposure());
	}

	private static void exemptAndDead() {
		section("3. exempt (creative/spectator) и мёртвый игрок не получают штрафов");
		ExposurePolicy.Result exempt = ExposurePolicy.evaluate(
				new ExposurePolicy.Input(0, true, false, true, true, false), 50, 0, 100000);
		check("exempt: exposure не изменился", exempt.exposure() == 50, "e=" + exempt.exposure());
		check("exempt: без эффекта/скорости/смерти",
				!exempt.darkEffectRequired() && !exempt.speedRestricted() && !exempt.deathRequired(), "flags");
		ExposurePolicy.Result dead = ExposurePolicy.evaluate(
				new ExposurePolicy.Input(0, true, false, false, false, false), 50, 0, 100000);
		check("dead: exposure не изменился", dead.exposure() == 50, "e=" + dead.exposure());
	}

	private static void victorySafe() {
		section("4. victorySafe: delta -2 и светлый отдых");
		ExposurePolicy.Result r = ExposurePolicy.evaluate(
				new ExposurePolicy.Input(0, false, false, true, false, true), 50, 0, 100000);
		check("victorySafe → -2", r.exposure() == 48, "e=" + r.exposure());
		check("victorySafe копит safeTicks", r.safeTicks() == 20, "safe=" + r.safeTicks());
	}

	private static void capBoundaries() {
		section("5. cap 0/100");
		check("bright не уходит ниже 0", result(0, light(9, false, false)).exposure() == 0, "e");
		check("dark не уходит выше 100 (с 99)", result(99, light(0, true, false)).exposure() == 100, "e");
		check("dark не уходит выше 100 (с 100)", result(100, light(0, true, false)).exposure() == 100, "e");
	}

	private static void deterministic() {
		section("6. два входа с одинаковыми DTO дают одинаковый результат");
		ExposurePolicy.Input in = light(7, true, true);
		ExposurePolicy.Result a = ExposurePolicy.evaluate(in, 40, 100, 60000);
		ExposurePolicy.Result b = ExposurePolicy.evaluate(in, 40, 100, 60000);
		check("результаты равны", a.equals(b), a + " vs " + b);
		check("другой вход — другой результат", !a.equals(ExposurePolicy.evaluate(light(0, true, true), 40, 100, 60000)),
				"diff");
	}

	private static void oracleOpenDarkness() {
		section("7. Oracle: с 0 за 600т открытой тьмы exposure 60; под крышей 30");
		StateModel open = StateModel.fresh();
		for (int i = 0; i < 30; i++) {
			apply(open, light(0, true, false));
		}
		check("открытая тьма → 60", open.lightExposure == 60, "e=" + open.lightExposure);

		StateModel roofed = StateModel.fresh();
		for (int i = 0; i < 30; i++) {
			apply(roofed, light(0, true, true));
		}
		check("под крышей → 30", roofed.lightExposure == 30, "e=" + roofed.lightExposure);
	}

	private static void oracleBrightLight() {
		section("8. Oracle: с 100 в ярком свете 500т→50, 580т→42, 600т→0");
		StateModel s = StateModel.fresh();
		s.lightExposure = 100;
		for (int i = 0; i < 25; i++) {
			apply(s, light(9, false, false));
		}
		check("после 500т exposure 50", s.lightExposure == 50, "e=" + s.lightExposure);
		check("safeTicks 500", s.safeLightTicks == 500, "safe=" + s.safeLightTicks);
		for (int i = 0; i < 4; i++) {
			apply(s, light(9, false, false));
		}
		check("после 580т exposure 42", s.lightExposure == 42, "e=" + s.lightExposure);
		apply(s, light(9, false, false));
		check("после 600т exposure 0 (полный сброс)", s.lightExposure == 0, "e=" + s.lightExposure);
		check("safeTicks 600", s.safeLightTicks == 600, "safe=" + s.safeLightTicks);
	}

	private static void neutralResetsSafeTimer() {
		section("9. light 5..8: safe timer сбрасывается, exposure не меняется");
		StateModel s = StateModel.fresh();
		s.lightExposure = 40;
		s.safeLightTicks = 300;
		apply(s, light(6, false, false));
		check("exposure не изменился", s.lightExposure == 40, "e=" + s.lightExposure);
		check("safe timer обнулён", s.safeLightTicks == 0, "safe=" + s.safeLightTicks);
	}

	private static void conditionThresholds() {
		section("10. Condition: порог истощения 90, восстановление в ярком, нет в промежуточном");
		check("exposure 89 — урона нет",
				result(89, light(5, false, false)).conditionMilli() == 100000, "c");
		check("exposure 90 — урон 50",
				result(90, light(5, false, false)).conditionMilli() == 99950, "c");
		StateModel bright = StateModel.fresh();
		bright.lightExposure = 40;
		bright.darknessConditionMilli = 1000;
		apply(bright, light(9, false, false));
		check("bright восстанавливает +50", bright.darknessConditionMilli == 1050, "c=" + bright.darknessConditionMilli);
		StateModel mid = StateModel.fresh();
		mid.lightExposure = 40;
		mid.darknessConditionMilli = 1000;
		apply(mid, light(6, false, false));
		check("промежуточный — без восстановления", mid.darknessConditionMilli == 1000, "c=" + mid.darknessConditionMilli);
	}

	private static void conditionDrainExact() {
		section("11. Oracle: 2000 тёмных damage samples снимают ровно 100 п.п. Condition");
		StateModel s = StateModel.fresh();
		s.lightExposure = 100;
		ExposurePolicy.Result last = null;
		for (int i = 0; i < 1999; i++) {
			last = apply(s, light(0, true, false));
		}
		check("после 1999 samples остаток 50 тыс.", s.darknessConditionMilli == 50,
				"c=" + s.darknessConditionMilli);
		check("death ещё не требуется", last != null && !last.deathRequired(), "death");
		last = apply(s, light(0, true, false));
		check("после 2000 samples Condition 0", s.darknessConditionMilli == 0, "c=" + s.darknessConditionMilli);
		check("deathRequired на 2000-м", last.deathRequired(), "death");
	}

	private static void conditionRecovery() {
		section("12. Condition cap 100000 при восстановлении");
		StateModel s = StateModel.fresh();
		s.lightExposure = 40;
		s.darknessConditionMilli = 99990;
		apply(s, light(9, false, false));
		check("не превышает cap", s.darknessConditionMilli == 100000, "c=" + s.darknessConditionMilli);
	}

	private static void flagThresholds() {
		section("13. Пороги 49/50/74/75/89/90/99/100");
		check("49: нет Darkness", !result(49, light(5, false, false)).darkEffectRequired(), "49");
		check("50: нужен Darkness", result(50, light(5, false, false)).darkEffectRequired(), "50");
		check("74: нет штрафа скорости", !result(74, light(5, false, false)).speedRestricted(), "74");
		check("75: штраф скорости", result(75, light(5, false, false)).speedRestricted(), "75");
		check("89/90: урон Condition", result(89, light(5, false, false)).conditionMilli() == 100000
				&& result(90, light(5, false, false)).conditionMilli() == 99950, "89/90");
		check("99: exposure 99", result(99, light(5, false, false)).exposure() == 99, "99");
		check("100: exposure 100", result(100, light(5, false, false)).exposure() == 100, "100");
	}

	private static void repeatedTick() {
		section("14. повтор tick с тем же номером не начисляет второй sample");
		ExposureServiceModel svc = new ExposureServiceModel(StateModel.fresh());
		svc.tick(1);
		svc.tick(1);
		svc.tick(1);
		check("remainder начислен один раз", svc.state.sampleRemainderTicks == 1,
				"rem=" + svc.state.sampleRemainderTicks);
		check("sample не выполнен", svc.sampleCount == 0, "samples=" + svc.sampleCount);
	}

	private static void sampleChunks19And1() {
		section("15. sample chunks 19+1");
		ExposureServiceModel svc = new ExposureServiceModel(StateModel.fresh());
		svc.run(1, 19);
		check("после 19 тиков sample нет", svc.sampleCount == 0, "samples=" + svc.sampleCount);
		check("remainder 19", svc.state.sampleRemainderTicks == 19, "rem=" + svc.state.sampleRemainderTicks);
		svc.tick(20);
		check("20-й тик — ровно один sample", svc.sampleCount == 1, "samples=" + svc.sampleCount);
		check("remainder 0", svc.state.sampleRemainderTicks == 0, "rem=" + svc.state.sampleRemainderTicks);
	}

	private static void sampleChunks10And10() {
		section("16. sample chunks 10+10");
		ExposureServiceModel svc = new ExposureServiceModel(StateModel.fresh());
		svc.run(1, 10);
		check("после 10 тиков sample нет", svc.sampleCount == 0, "samples=" + svc.sampleCount);
		check("remainder 10", svc.state.sampleRemainderTicks == 10, "rem=" + svc.state.sampleRemainderTicks);
		svc.run(11, 10);
		check("итого ровно один sample", svc.sampleCount == 1, "samples=" + svc.sampleCount);
	}

	private static void serializationRoundTrip() {
		section("17. serialization round-trip полей тьмы");
		StateModel s = StateModel.fresh();
		s.lightExposure = 42;
		s.safeLightTicks = 300;
		s.darknessConditionMilli = 57500;
		s.condition = 57.5F;
		s.sampleRemainderTicks = 7;
		s.darknessRevision = 9L;
		StateModel loaded = StateModel.deserialize(s.serialize());
		check("schema", loaded.darknessSchema == 1, "schema=" + loaded.darknessSchema);
		check("exposure", loaded.lightExposure == 42, "e=" + loaded.lightExposure);
		check("safeTicks", loaded.safeLightTicks == 300, "safe=" + loaded.safeLightTicks);
		check("remainder", loaded.sampleRemainderTicks == 7, "rem=" + loaded.sampleRemainderTicks);
		check("conditionMilli", loaded.darknessConditionMilli == 57500, "c=" + loaded.darknessConditionMilli);
		check("condition отображает милли", Math.abs(loaded.condition - 57.5F) < 1e-4, "cond=" + loaded.condition);
		check("darknessRevision", loaded.darknessRevision == 9L, "rev=" + loaded.darknessRevision);
	}

	private static void saveAtRemainder19() {
		section("18. save на remainder 19: load сохраняет и следующий тик даёт sample");
		StateModel s = StateModel.fresh();
		s.sampleRemainderTicks = 19;
		StateModel loaded = StateModel.deserialize(s.serialize());
		check("remainder 19 сохранён", loaded.sampleRemainderTicks == 19, "rem=" + loaded.sampleRemainderTicks);
		ExposureServiceModel svc = new ExposureServiceModel(loaded);
		svc.tick(1);
		check("следующий тик — sample", svc.sampleCount == 1, "samples=" + svc.sampleCount);
	}

	private static void migrationFromLegacyCondition() {
		section("19. миграция: condition 63.0 → darkness_condition_milli 63000");
		StateModel legacy = StateModel.legacy(63.0F);
		StateModel migrated = StateModel.deserialize(legacy.serializeLegacy());
		check("schema=1", migrated.darknessSchema == 1, "schema=" + migrated.darknessSchema);
		check("milli = round(63.0*1000)", migrated.darknessConditionMilli == 63000,
				"c=" + migrated.darknessConditionMilli);
		check("condition отображает милли", Math.abs(migrated.condition - 63.0F) < 1e-4, "cond=" + migrated.condition);
		StateModel legacyZero = StateModel.legacy(0.0F);
		StateModel migratedZero = StateModel.deserialize(legacyZero.serializeLegacy());
		check("condition 0 → milli 0", migratedZero.darknessConditionMilli == 0,
				"c=" + migratedZero.darknessConditionMilli);
	}

	private static void disconnectWithoutOfflineCatchUp() {
		section("20. disconnect без offline catch-up");
		ExposureServiceModel svc = new ExposureServiceModel(StateModel.fresh());
		svc.run(1, 5);
		svc.onDisconnect();
		svc.onJoin(1000);
		int samplesAfterJoin = svc.sampleCount;
		// Повтор того же тика после join не даёт sample (guard = now).
		svc.tick(1000);
		check("повтор тика join не даёт sample", svc.sampleCount == samplesAfterJoin, "samples=" + svc.sampleCount);
		// Долгий offline: нет догоняющих samples, следующий тик даёт максимум +1 remainder.
		int remainderBefore = svc.state.sampleRemainderTicks;
		svc.tick(1_000_000);
		check("нет догоняющего расчёта", svc.sampleCount == samplesAfterJoin, "samples=" + svc.sampleCount);
		check("remainder вырос максимум на 1", svc.state.sampleRemainderTicks == remainderBefore + 1,
				"rem=" + svc.state.sampleRemainderTicks);
	}

	private static void noModifierAccumulation() {
		section("21. отсутствие накопления modifier'ов");
		StateModel s = StateModel.fresh();
		s.lightExposure = 80; // сразу выше порога скорости
		ExposureServiceModel svc = new ExposureServiceModel(s);
		svc.blockLight = 0;
		svc.canSeeSky = true;
		svc.run(1, 2000);
		check("ровно один modifier", svc.modifiers.size() == 1, "mods=" + svc.modifiers.size());
		check("это именованный white_fog:darkness_slow",
				svc.modifiers.containsKey(ExposureServiceModel.SPEED_MODIFIER_ID), "id");
	}

	private static void exemptPauseAndResume() {
		section("22. creative/spectator приостанавливает шкалы, возврат восстанавливает гейт");
		StateModel s = StateModel.fresh();
		s.lightExposure = 50;
		ExposureServiceModel svc = new ExposureServiceModel(s);
		svc.exempt = true;
		svc.blockLight = 0;
		svc.canSeeSky = true;
		svc.run(1, 100);
		check("sample не выполняется", svc.sampleCount == 0, "samples=" + svc.sampleCount);
		check("modifier снят", svc.modifiers.isEmpty(), "mods=" + svc.modifiers.size());
		check("exposure приостановлен", svc.state.lightExposure == 50, "e=" + svc.state.lightExposure);
		svc.exempt = false;
		svc.run(101, 20);
		check("возврат в survival: sample пошёл", svc.sampleCount == 1, "samples=" + svc.sampleCount);
		check("гейт восстановлен по сохранённому exposure", svc.state.lightExposure > 50,
				"e=" + svc.state.lightExposure);
	}

	private static void unloadedEyeSkipsSample() {
		section("23. eye position не загружена — sample пропускается без догоняющего расчёта");
		StateModel s = StateModel.fresh();
		s.lightExposure = 50;
		ExposureServiceModel svc = new ExposureServiceModel(s);
		svc.loaded = false;
		svc.blockLight = 0;
		svc.canSeeSky = true;
		svc.run(1, 20);
		check("sample пропущен", svc.sampleCount == 0, "samples=" + svc.sampleCount);
		check("пропуск зафиксирован", svc.skippedUnloadedSamples == 1, "skipped=" + svc.skippedUnloadedSamples);
		check("exposure не изменился", svc.state.lightExposure == 50, "e=" + svc.state.lightExposure);
		svc.loaded = true;
		svc.run(21, 20);
		check("после загрузки sample пошёл", svc.sampleCount == 1, "samples=" + svc.sampleCount);
	}

	private static void netherNoSkylightGate() {
		section("24. Nether/End: block light без skylight-гейта");
		// В Nether адаптер canSeeSky=false: тёмный блок даёт +1, а не +2.
		check("light0 без неба → +1", result(50, light(0, false, false)).exposure() == 51,
				"e=" + result(50, light(0, false, false)).exposure());
		// block light работает и там: яркий свет снижает exposure.
		check("light9 в Nether → -2", result(50, light(9, false, false)).exposure() == 48, "e");
	}

	private static void twoPlayersDifferentExposure() {
		section("25. два игрока имеют разные exposure");
		StateModel a = StateModel.fresh();
		a.lightExposure = 50;
		ExposureServiceModel sa = new ExposureServiceModel(a);
		sa.blockLight = 0;
		sa.canSeeSky = false;
		StateModel b = StateModel.fresh();
		b.lightExposure = 50;
		ExposureServiceModel sb = new ExposureServiceModel(b);
		sb.blockLight = 9;
		sa.run(1, 20);
		sb.run(1, 20);
		check("игрок A вырос (тьма)", sa.state.lightExposure == 51, "a=" + sa.state.lightExposure);
		check("игрок B упал (свет)", sb.state.lightExposure == 48, "b=" + sb.state.lightExposure);
		check("шкалы различаются", sa.state.lightExposure != sb.state.lightExposure, "diff");
	}

	private static void snapshotHeartbeatAndMinInterval() {
		section("26. синхронизация: heartbeat 100 и min 5 тиков");
		// Стабильное состояние: exposure уже на cap, Condition 0, скорость уже ограничена —
		// следящий sample не меняет сохранённые поля, поэтому снимок только на heartbeat.
		StateModel stable = StateModel.fresh();
		stable.lightExposure = 100;
		stable.darknessConditionMilli = 0;
		stable.condition = 0.0F;
		ExposureServiceModel svc = new ExposureServiceModel(stable);
		svc.blockLight = 0;
		svc.canSeeSky = false;
		svc.lastSpeedRestricted = true;
		svc.onJoin(0);
		check("join — немедленный снимок", svc.snapshotCount == 1, "snaps=" + svc.snapshotCount);
		svc.run(1, 99);
		check("без изменений до 100т новых снимков нет", svc.snapshotCount == 1,
				"snaps=" + svc.snapshotCount);
		svc.tick(100);
		check("heartbeat 100 → снимок", svc.snapshotCount == 2, "snaps=" + svc.snapshotCount);

		// min 5 тиков: изменение ревизии сразу после join не отправляется до истечения 5 тиков.
		ExposureServiceModel fast = new ExposureServiceModel(StateModel.fresh());
		fast.onJoin(0);
		int base = fast.snapshotCount;
		fast.state.darknessRevision++;
		fast.tick(2);
		fast.state.darknessRevision++;
		fast.tick(3);
		check("min 5 тиков соблюдён", fast.snapshotCount == base, "snaps=" + fast.snapshotCount);
		fast.tick(6);
		check("после 5 тиков снимок ушёл", fast.snapshotCount == base + 1, "snaps=" + fast.snapshotCount);
	}

	// ------------------------------------------------------------------
	// Инфраструктура
	// ------------------------------------------------------------------

	private static ExposurePolicy.Input light(int blockLight, boolean canSeeSky, boolean shelter) {
		return new ExposurePolicy.Input(blockLight, canSeeSky, shelter, true, false, false);
	}

	private static ExposurePolicy.Result result(int currentExposure, ExposurePolicy.Input input) {
		return ExposurePolicy.evaluate(input, currentExposure, 0, 100000);
	}

	/** Применяет один sample к модели состояния (как сервис). */
	private static ExposurePolicy.Result apply(StateModel s, ExposurePolicy.Input input) {
		ExposurePolicy.Result r = ExposurePolicy.evaluate(input, s.lightExposure, s.safeLightTicks,
				s.darknessConditionMilli);
		s.lightExposure = r.exposure();
		s.safeLightTicks = r.safeTicks();
		s.darknessConditionMilli = r.conditionMilli();
		s.condition = r.conditionMilli() / 1000.0F;
		return r;
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
