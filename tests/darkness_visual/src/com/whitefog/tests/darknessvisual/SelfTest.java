package com.whitefog.tests.darknessvisual;

import java.util.ArrayList;
import java.util.List;

/**
 * Детерминированный self-test чистой модели визуального адаптера модовой тьмы.
 *
 * <p>Проверяет bounded-огибающую и выходные отображения: frame-delta, отсутствие пульсации,
 * монотонность, отсутствие перехлёста, cap, частотную независимость, переходы по устареванию
 * snapshot, disconnect/creative/spectator/death, порог exposure, детерминизм.</p>
 *
 * <p>Hard-timeout — watchdog-поток (20 c) => exit 124 = TIMEOUT. Это logic-only sandbox:
 * НЕ runtime-proof, миксины/шейдеры/реальный свет не проверяются.</p>
 */
public final class SelfTest {
	private static final long HARD_TIMEOUT_MS = 20_000L;

	private static int passed;
	private static int failed;
	private static final List<String> failures = new ArrayList<>();

	private SelfTest() {
	}

	public static void main(String[] args) {
		startWatchdog();

		System.out.println("=== White Fog darkness visual adapter sandbox ===");
		System.out.println("policy: exposure>=" + DarknessVisualPolicy.MOD_DARK_EXPOSURE_THRESHOLD
				+ " stale=" + DarknessVisualPolicy.SNAPSHOT_STALE_SECONDS + "s"
				+ " ownedTail=" + DarknessVisualPolicy.OWNED_TAIL_SECONDS + "s"
				+ " fadeIn=" + DarknessVisualPolicy.FADE_IN_PER_SECOND + "/s"
				+ " fadeOut=" + DarknessVisualPolicy.FADE_OUT_PER_SECOND + "/s");
		System.out.println("caps: brightness.floor=" + DarknessVisualPolicy.MAX_BRIGHTNESS_FLOOR
				+ " darkness.offset=" + DarknessVisualPolicy.MAX_DARKNESS_OFFSET
				+ " fog.min=" + DarknessVisualPolicy.MIN_FOG_DISTANCE
				+ " fog.darkness=" + DarknessVisualPolicy.MAX_FOG_DARKNESS);
		System.out.println("vanilla reference peak=" + VanillaDarknessReference.VANILLA_PEAK
				+ " period=" + VanillaDarknessReference.PULSE_PERIOD_TICKS + " ticks");
		System.out.println();

		try {
			testFadeInMonotonicNoOvershoot();
			testFadeOutMonotonicNoOvershoot();
			testFrameRateIndependence();
			testBoundedCap();
			testSnapshotStaleTransition();
			testDisconnectFade();
			testCreativeSpectatorDeathFade();
			testExposureThresholdBoundary();
			testTargetEngagedImmediately();
			testOwnedTailSuppressesAfterExposureDrop();
			testOwnedTailExpiresThenFades();
			testForeignDarknessNotEngaged();
			testNoPulseVersusVanilla();
			testOutputMonotonicContinuousBounded();
			testDeterminism();
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
	// Тесты
	// ------------------------------------------------------------------

	/** Fade-in: монотонно вверх, без колебаний, без перехлёста, в [0,1]. */
	private static void testFadeInMonotonicNoOvershoot() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(100);
		double dt = 1.0 / 60.0;
		float prev = model.envelope();
		boolean monotonic = true;
		boolean bounded = true;
		boolean noNegSteps = true;
		for (int i = 0; i < 240; i++) { // 4 секунды кадров
			float delta = model.frame(dt, true, false, false);
			float now = model.envelope();
			if (now < prev - 1e-7f) {
				monotonic = false;
			}
			if (delta < 0.0f) {
				noNegSteps = false;
			}
			if (now < 0.0f || now > 1.0f) {
				bounded = false;
			}
			prev = now;
		}
		check("fadeIn.monotonic", monotonic);
		check("fadeIn.noNegSteps", noNegSteps);
		check("fadeIn.bounded", bounded);
		checkClose("fadeIn.reachesCap", 1.0f, model.envelope(), 1e-6f);
	}

	/** Fade-out: монотонно вниз, без перехлёста, в [0,1]. */
	private static void testFadeOutMonotonicNoOvershoot() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(100);
		for (int i = 0; i < 240; i++) {
			model.frame(1.0 / 60.0, true, false, false);
		}
		checkClose("fadeOut.setupAtCap", 1.0f, model.envelope(), 1e-6f);

		// Убираем серверную тьму и мгновенно блокируем свежесть -> цель 0.
		model.onDisconnect();
		float prev = model.envelope();
		boolean monotonic = true;
		boolean bounded = true;
		boolean noPosSteps = true;
		for (int i = 0; i < 240; i++) {
			float delta = model.frame(1.0 / 60.0, true, false, false);
			float now = model.envelope();
			if (now > prev + 1e-7f) {
				monotonic = false;
			}
			if (delta > 0.0f) {
				noPosSteps = false;
			}
			if (now < 0.0f || now > 1.0f) {
				bounded = false;
			}
			prev = now;
		}
		check("fadeOut.monotonic", monotonic);
		check("fadeOut.noPosSteps", noPosSteps);
		check("fadeOut.bounded", bounded);
		checkClose("fadeOut.reachesZero", 0.0f, model.envelope(), 1e-6f);
	}

	/** Одинаковое реальное время при разном dt даёт одинаковый результат (линейный шаг). */
	private static void testFrameRateIndependence() {
		// Середина fade-in: ожидание считается из фактической константы (rate * t), а не хардкодится,
		// поэтому тест не ломается при осознанной настройке скорости (тикет: замедление fade-in).
		double rate = DarknessVisualPolicy.FADE_IN_PER_SECOND;
		double midTime = 0.25;
		float midExpected = (float) (rate * midTime);
		MildnessModel fast = new MildnessModel();
		MildnessModel slow = new MildnessModel();
		fast.onSnapshot(80);
		slow.onSnapshot(80);

		for (int i = 0; i < 15; i++) { // 15 * (1/60) = 0.25 c
			fast.frame(1.0 / 60.0, true, false, false);
		}
		for (int i = 0; i < 5; i++) { // 5 * (1/20) = 0.25 c
			slow.frame(1.0 / 20.0, true, false, false);
		}
		checkClose("frameRate.midpoint60fps", midExpected, fast.envelope(), 1e-4f);
		checkClose("frameRate.midpoint20fps", midExpected, slow.envelope(), 1e-4f);
		checkClose("frameRate.midpointEqual", fast.envelope(), slow.envelope(), 1e-4f);

		// Полное завершение: время до cap = 1/rate; берём его с запасом на обоих частотах.
		double settleSeconds = 1.0 / rate + 0.5;
		int fastFrames = (int) Math.ceil(settleSeconds * 60.0);
		int slowFrames = (int) Math.ceil(settleSeconds * 20.0);
		for (int i = 15; i < fastFrames; i++) {
			fast.frame(1.0 / 60.0, true, false, false);
		}
		for (int i = 5; i < slowFrames; i++) {
			slow.frame(1.0 / 20.0, true, false, false);
		}
		checkClose("frameRate.settle60fps", 1.0f, fast.envelope(), 1e-6f);
		checkClose("frameRate.settle20fps", 1.0f, slow.envelope(), 1e-6f);

		// Ни один отдельный кадр не даёт скачка больше rate*dt.
		MildnessModel probe = new MildnessModel();
		probe.onSnapshot(80);
		float maxStepSeen = 0.0f;
		for (int i = 0; i < 60; i++) {
			float d = probe.frame(1.0 / 30.0, true, false, false);
			maxStepSeen = Math.max(maxStepSeen, Math.abs(d));
		}
		float limit = (float) (DarknessVisualPolicy.FADE_IN_PER_SECOND / 30.0) + 1e-5f;
		check("frameRate.noAbruptStep", maxStepSeen <= limit);
	}

	/** Значение и все выходы упираются в умеренный cap. */
	private static void testBoundedCap() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(100);
		for (int i = 0; i < 600; i++) {
			if (i % 240 == 0) {
				model.onSnapshot(100); // heartbeat сервера удерживает свежесть
			}
			model.frame(1.0 / 60.0, true, false, false);
		}
		checkClose("cap.envelope", 1.0f, model.envelope(), 1e-6f);
		checkClose("cap.brightnessFloor", DarknessVisualPolicy.MAX_BRIGHTNESS_FLOOR, model.brightnessFloor(), 1e-6f);
		checkClose("cap.darknessOffset", DarknessVisualPolicy.MAX_DARKNESS_OFFSET, model.darknessOffset(), 1e-6f);
		check("cap.offsetBelowVanillaPeak",
				model.darknessOffset() < VanillaDarknessReference.VANILLA_PEAK - 1e-3f);
		check("cap.fogEndAtLeastMin", model.fogEnd(15.0f) >= DarknessVisualPolicy.MIN_FOG_DISTANCE - 1e-4f);
		check("cap.fogDarknessAtMostMax", model.fogDarkness(1.0f) <= DarknessVisualPolicy.MAX_FOG_DARKNESS + 1e-6f);
	}

	/** Устаревание snapshot: пока свежий — активно; после stale — спад до 0. */
	private static void testSnapshotStaleTransition() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(100);
		// Свежий снимок: активно.
		check("stale.activeWhenFresh", model.targetActive(true, false, false));
		// Снимков больше нет; идём до stale + запас.
		double dt = 1.0 / 60.0;
		int framesToStale = (int) Math.ceil(DarknessVisualPolicy.SNAPSHOT_STALE_SECONDS / dt) + 2;
		for (int i = 0; i < framesToStale; i++) {
			model.frame(dt, true, false, false);
		}
		check("stale.inactiveAfterWindow", !model.targetActive(true, false, false));
		// Даём догореть fade-out.
		for (int i = 0; i < 300; i++) {
			model.frame(dt, true, false, false);
		}
		checkClose("stale.envelopeZero", 0.0f, model.envelope(), 1e-6f);
	}

	/** Disconnect мгновенно делает данные недостоверными -> спад. */
	private static void testDisconnectFade() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(90);
		int settle = settleFrames(1.0 / 60.0);
		for (int i = 0; i < settle; i++) {
			model.frame(1.0 / 60.0, true, false, false);
		}
		checkClose("disconnect.beforeAtCap", 1.0f, model.envelope(), 1e-6f);
		model.onDisconnect();
		check("disconnect.snapshotNotFresh", !model.snapshotFresh());
		check("disconnect.targetInactive", !model.targetActive(true, false, false));
		for (int i = 0; i < 300; i++) {
			model.frame(1.0 / 60.0, true, false, false);
		}
		checkClose("disconnect.afterZero", 0.0f, model.envelope(), 1e-6f);
	}

	/** Creative/spectator/смерть гасят модовую тьму (спад), даже при свежем exposure. */
	private static void testCreativeSpectatorDeathFade() {
		for (String mode : new String[] { "creative", "spectator", "dead" }) {
			MildnessModel model = new MildnessModel();
			model.onSnapshot(100);
			for (int i = 0; i < settleFrames(1.0 / 60.0); i++) {
				model.frame(1.0 / 60.0, true, false, false);
			}
			checkClose(mode + ".beforeCap", 1.0f, model.envelope(), 1e-6f);
			boolean alive = !"dead".equals(mode);
			boolean creative = "creative".equals(mode);
			boolean spectator = "spectator".equals(mode);
			check(mode + ".targetInactive", !model.targetActive(alive, creative, spectator));
			for (int i = 0; i < 300; i++) {
				model.frame(1.0 / 60.0, alive, creative, spectator);
			}
			checkClose(mode + ".afterZero", 0.0f, model.envelope(), 1e-6f);
		}
	}

	/** Порог: exposure 49 неактивно, 50 активно. */
	private static void testExposureThresholdBoundary() {
		MildnessModel below = new MildnessModel();
		below.onSnapshot(49);
		check("threshold.49Inactive", !below.targetActive(true, false, false));

		MildnessModel at = new MildnessModel();
		at.onSnapshot(50);
		check("threshold.50Active", at.targetActive(true, false, false));
	}

	/**
	 * Пульсацию надо подавить СРАЗУ при targetActive, ещё до набора огибающей: {@code active()}
	 * истинно уже на первом кадре (даже dt=0), иначе fade-in оставит чёрные импульсы косинуса.
	 */
	private static void testTargetEngagedImmediately() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(50);
		model.frame(0.0, true, false, false, false);
		check("immediate.activeAtThreshold", model.active());
		checkClose("immediate.envelopeStillZero", 0.0f, model.envelope(), 1e-6f);
	}

	/**
	 * Уход exposure ниже порога при живом собственном Darkness-эффекте: targetActive false, но
	 * «хвост принадлежности» держит адаптер активным (огибающая не гаснет) — пульсация не вернётся
	 * до истечения эффекта.
	 */
	private static void testOwnedTailSuppressesAfterExposureDrop() {
		MildnessModel model = new MildnessModel();
		double dt = 1.0 / 60.0;
		model.onSnapshot(100);
		for (int i = 0; i < settleFrames(dt); i++) {
			if (i % 60 == 0) {
				model.onSnapshot(100); // heartbeat
			}
			model.frame(dt, true, false, false, true);
		}
		checkClose("tail.beforeDropAtCap", 1.0f, model.envelope(), 1e-6f);

		// Exposure уходит ниже порога, но эффект ещё жив (selfDarkness=true) и снимок свежий.
		model.onSnapshot(49);
		boolean heldActive = true;
		boolean heldEnvelope = true;
		for (int i = 0; i < 30; i++) { // 0.5 c — внутри окна хвоста
			if (i % 30 == 0) {
				model.onSnapshot(49);
			}
			model.frame(dt, true, false, false, true);
			if (!model.active()) {
				heldActive = false;
			}
			if (model.envelope() < 1.0f - 1e-4f) {
				heldEnvelope = false;
			}
		}
		check("tail.ownedTailFlag", model.ownedTail());
		check("tail.staysActive", heldActive);
		check("tail.envelopeHeld", heldEnvelope);
	}

	/** «Хвост» ограничен по времени: после его истечения (эффект пропал) — спад и inactive. */
	private static void testOwnedTailExpiresThenFades() {
		MildnessModel model = new MildnessModel();
		double dt = 1.0 / 60.0;
		model.onSnapshot(100);
		for (int i = 0; i < settleFrames(dt); i++) {
			model.frame(dt, true, false, false, true);
		}
		model.onSnapshot(49);
		// Живём за пределами окна хвоста с живым эффектом.
		int framesToPastTail = (int) Math.ceil(DarknessVisualPolicy.OWNED_TAIL_SECONDS / dt) + 5;
		for (int i = 0; i < framesToPastTail; i++) {
			model.frame(dt, true, false, false, true);
		}
		check("tail.expiredFlag", !model.ownedTail());
		// Эффект пропал: цель 0, спад до нуля за разумное время.
		for (int i = 0; i < 300; i++) {
			model.frame(dt, true, false, false, false);
		}
		checkClose("tail.fadedToZero", 0.0f, model.envelope(), 1e-6f);
		check("tail.inactiveAfterFade", !model.active());
	}

	/**
	 * Чужой Darkness без недавней модовой активности (нет снимка / после disconnect) не трогается:
	 * ownedTail=false, active=false, огибающая остаётся нулевой.
	 */
	private static void testForeignDarknessNotEngaged() {
		MildnessModel noSnapshot = new MildnessModel();
		for (int i = 0; i < 60; i++) {
			noSnapshot.frame(1.0 / 60.0, true, false, false, true);
		}
		check("foreign.noSnapshotInactive", !noSnapshot.active());
		checkClose("foreign.noSnapshotEnvelopeZero", 0.0f, noSnapshot.envelope(), 1e-6f);

		MildnessModel disconnected = new MildnessModel();
		disconnected.onSnapshot(100);
		for (int i = 0; i < settleFrames(1.0 / 60.0); i++) {
			disconnected.frame(1.0 / 60.0, true, false, false, true);
		}
		disconnected.onDisconnect();
		for (int i = 0; i < 300; i++) {
			disconnected.frame(1.0 / 60.0, true, false, false, true);
		}
		check("foreign.afterDisconnectInactive", !disconnected.active());
		checkClose("foreign.afterDisconnectZero", 0.0f, disconnected.envelope(), 1e-6f);
	}

	/** Наше постоянное слагаемое не пульсирует, vanilla — пульсирует. */
	private static void testNoPulseVersusVanilla() {
		MildnessModel model = new MildnessModel();
		model.onSnapshot(100);
		double dt = 1.0 / 60.0;
		// Ждём полного завершения fade-in (время = 1/rate), затем снимаем 480 кадров = 2 периода.
		int warmupFrames = (int) Math.ceil(1.0 / DarknessVisualPolicy.FADE_IN_PER_SECOND / dt) + 5;
		int frames = warmupFrames + 480;
		float ourMin = Float.MAX_VALUE;
		float ourMax = -Float.MAX_VALUE;
		float vanillaMin = Float.MAX_VALUE;
		float vanillaMax = -Float.MAX_VALUE;
		for (int i = 0; i < frames; i++) {
			if (i % 240 == 0) {
				model.onSnapshot(100); // heartbeat сервера удерживает свежесть
			}
			model.frame(dt, true, false, false);
			int tick = (int) Math.floor(i * dt * 20.0);
			if (i > warmupFrames) { // после завершения fade-in
				float ours = model.darknessOffset();
				float vanilla = VanillaDarknessReference.pulse(tick, 1.0f);
				ourMin = Math.min(ourMin, ours);
				ourMax = Math.max(ourMax, ours);
				vanillaMin = Math.min(vanillaMin, vanilla);
				vanillaMax = Math.max(vanillaMax, vanilla);
			}
		}
		checkClose("noPulse.ourVarianceZero", 0.0f, ourMax - ourMin, 1e-6f);
		check("noPulse.vanillaVariancePositive", (vanillaMax - vanillaMin) > 0.1f);
	}

	/** Выходы: монотонны по e, непрерывны, в границах; e=0 == vanilla no-op. */
	private static void testOutputMonotonicContinuousBounded() {
		float step = 1.0f / 1000.0f;
		float prevB = DarknessVisualPolicy.brightnessFloor(-1.0f, 1.0f);
		float prevO = DarknessVisualPolicy.darknessOffset(-1.0f, 1.0f);
		float prevF = DarknessVisualPolicy.fogEnd(15.0f, -1.0f);
		float prevD = DarknessVisualPolicy.fogDarkness(1.0f, -1.0f);
		boolean bMono = true;
		boolean oMono = true;
		boolean fMono = true;
		boolean dMono = true;
		boolean bounded = true;
		boolean continuous = true;
		for (float e = -0.5f; e <= 1.5f + 1e-6f; e += step) {
			float b = DarknessVisualPolicy.brightnessFloor(e, 1.0f);
			float o = DarknessVisualPolicy.darknessOffset(e, 1.0f);
			float f = DarknessVisualPolicy.fogEnd(15.0f, e);
			float d = DarknessVisualPolicy.fogDarkness(1.0f, e);
			if (b < prevB - 1e-6f) {
				bMono = false;
			}
			if (o < prevO - 1e-6f) {
				oMono = false;
			}
			if (f < prevF - 1e-6f) {
				fMono = false;
			}
			if (d > prevD + 1e-6f) {
				dMono = false;
			}
			if (Math.abs(b - prevB) > 0.05f || Math.abs(o - prevO) > 0.05f
					|| Math.abs(f - prevF) > 0.05f || Math.abs(d - prevD) > 0.05f) {
				continuous = false;
			}
			if (b < -1e-6f || b > DarknessVisualPolicy.MAX_BRIGHTNESS_FLOOR + 1e-6f
					|| o < -1e-6f || o > DarknessVisualPolicy.MAX_DARKNESS_OFFSET + 1e-6f
					|| f < 15.0f - 1e-4f || f > DarknessVisualPolicy.MIN_FOG_DISTANCE + 1e-4f
					|| d < DarknessVisualPolicy.MAX_FOG_DARKNESS - 1e-4f || d > 1.0f + 1e-6f) {
				bounded = false;
			}
			prevB = b;
			prevO = o;
			prevF = f;
			prevD = d;
		}
		check("outputs.brightnessMonotonic", bMono);
		check("outputs.offsetMonotonic", oMono);
		check("outputs.fogEndMonotonic", fMono);
		check("outputs.fogDarknessMonotonic", dMono);
		check("outputs.continuous", continuous);
		check("outputs.bounded", bounded);

		// e=0 => ровно vanilla no-op.
		checkClose("outputs.e0.brightness", 0.0f, DarknessVisualPolicy.brightnessFloor(0.0f, 1.0f), 1e-6f);
		checkClose("outputs.e0.offset", 0.0f, DarknessVisualPolicy.darknessOffset(0.0f, 1.0f), 1e-6f);
		checkClose("outputs.e0.fogEnd", 15.0f, DarknessVisualPolicy.fogEnd(15.0f, 0.0f), 1e-6f);
		checkClose("outputs.e0.fogDarkness", 1.0f, DarknessVisualPolicy.fogDarkness(1.0f, 0.0f), 1e-6f);

		// blend=0 => no-op (чужой/отсутствующий Darkness-фактор не усиливается).
		checkClose("outputs.blend0.offset", 0.0f, DarknessVisualPolicy.darknessOffset(1.0f, 0.0f), 1e-6f);
	}

	/** Один и тот же вход даёт один и тот же выход (детерминизм). */
	private static void testDeterminism() {
		List<Float> a = runSequence();
		List<Float> b = runSequence();
		check("determinism.sameLength", a.size() == b.size());
		boolean same = a.size() == b.size();
		for (int i = 0; same && i < a.size(); i++) {
			if (!a.get(i).equals(b.get(i))) {
				same = false;
			}
		}
		check("determinism.sameValues", same);
	}

	private static List<Float> runSequence() {
		MildnessModel model = new MildnessModel();
		List<Float> out = new ArrayList<>();
		double dt = 1.0 / 40.0;
		model.onSnapshot(60);
		for (int i = 0; i < 30; i++) {
			model.frame(dt, true, false, false);
			out.add(model.envelope());
		}
		model.onSnapshot(40); // ниже порога
		for (int i = 0; i < 30; i++) {
			model.frame(dt, true, false, false);
			out.add(model.envelope());
		}
		model.onDisconnect();
		for (int i = 0; i < 30; i++) {
			model.frame(dt, false, false, false);
			out.add(model.envelope());
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Инфраструктура
	// ------------------------------------------------------------------

	/** Кадров, заведомо достаточных для полного fade-in при данном dt (время 1/rate + запас). */
	private static int settleFrames(double dt) {
		return (int) Math.ceil(1.0 / DarknessVisualPolicy.FADE_IN_PER_SECOND / dt) + 10;
	}

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
