package com.whitefog.tests.portabledynamic;

import java.util.ArrayList;
import java.util.List;

/**
 * Детерминированный self-test исправления динамического переносного света (тикет поверх 1.9):
 * <ol>
 *   <li><b>root cause</b>: старая проверка {@code level instanceof ClientLevel} обнуляла свет при
 *       запекании меша (там приходит {@code RenderSectionRegion}); новая логика этого не делает;</li>
 *   <li>затухание 1 уровень/блок и кламп 0..15 для факела (14) и факела душ (10);</li>
 *   <li>вклад в block light сущности (рука/модель игрока) — предмет виден, но не fullbright;</li>
 *   <li>радиус секций для перестройки меша.</li>
 * </ol>
 *
 * <p>Hard-timeout — watchdog-поток (20 c) → exit 124 = TIMEOUT. Logic-only sandbox, НЕ runtime-proof.</p>
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

		System.out.println("=== White Fog portable dynamic light sandbox ===");
		System.out.println("torchEmission=" + DynamicLightModel.TORCH_EMISSION
				+ " soulTorchEmission=" + DynamicLightModel.SOUL_TORCH_EMISSION);
		System.out.println();

		try {
			testAttenuation();
			testTorchFalloff();
			testSoulTorchFalloff();
			testZeroAndClamp();
			testRootCauseRenderRegion();
			testEntityHandLitNotFullbright();
			testFirstPersonHandRaise();
			testSectionRadius();
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
	// Затухание
	// ------------------------------------------------------------------

	private static void testAttenuation() {
		check("atten.zero", DynamicLightModel.attenuation(0.0) == 0);
		check("atten.subBlock", DynamicLightModel.attenuation(0.1) == 1);
		check("atten.halfBlock", DynamicLightModel.attenuation(0.5) == 1);
		check("atten.oneBlock", DynamicLightModel.attenuation(1.0) == 1);
		check("atten.onePlus", DynamicLightModel.attenuation(1.01) == 2);
		check("atten.thirteen", DynamicLightModel.attenuation(13.0) == 13);
		check("atten.negative", DynamicLightModel.attenuation(-3.0) == 0);
		check("atten.nan", DynamicLightModel.attenuation(Double.NaN) == 0);
	}

	// ------------------------------------------------------------------
	// Затухание факела / факела душ
	// ------------------------------------------------------------------

	private static void testTorchFalloff() {
		int e = DynamicLightModel.TORCH_EMISSION;
		check("torch.dist0", DynamicLightModel.newDynamicLevel(e, 0.0) == 14);
		check("torch.distHalf", DynamicLightModel.newDynamicLevel(e, 0.5) == 13);
		check("torch.dist1", DynamicLightModel.newDynamicLevel(e, 1.0) == 13);
		check("torch.dist13", DynamicLightModel.newDynamicLevel(e, 13.0) == 1);
		check("torch.dist14", DynamicLightModel.newDynamicLevel(e, 14.0) == 0);
		check("torch.dist20", DynamicLightModel.newDynamicLevel(e, 20.0) == 0);
	}

	private static void testSoulTorchFalloff() {
		int e = DynamicLightModel.SOUL_TORCH_EMISSION;
		check("soul.dist0", DynamicLightModel.newDynamicLevel(e, 0.0) == 10);
		check("soul.dist9", DynamicLightModel.newDynamicLevel(e, 9.0) == 1);
		check("soul.dist10", DynamicLightModel.newDynamicLevel(e, 10.0) == 0);
	}

	private static void testZeroAndClamp() {
		for (double d = 0.0; d <= 20.0; d += 0.5) {
			check("zero.alwaysZero.", DynamicLightModel.newDynamicLevel(0, d) == 0);
		}
		check("clamp.over15", DynamicLightModel.newDynamicLevel(20, 0.0) == 15);
		check("clamp.negative", DynamicLightModel.newDynamicLevel(-5, 0.0) == 0);
	}

	// ------------------------------------------------------------------
	// Root cause: RenderSectionRegion vs ClientLevel
	// ------------------------------------------------------------------

	/**
	 * Регрессия root cause: во время запекания меша getter — НЕ {@code ClientLevel}
	 * ({@code RenderSectionRegion}). Старый код возвращал 0, новый — реальный уровень.
	 */
	private static void testRootCauseRenderRegion() {
		int oldWorld = DynamicLightModel.oldDynamicLevel(true, DynamicLightModel.TORCH_EMISSION, 2.0);
		int oldRegion = DynamicLightModel.oldDynamicLevel(false, DynamicLightModel.TORCH_EMISSION, 2.0);
		int newRegion = DynamicLightModel.newDynamicLevel(DynamicLightModel.TORCH_EMISSION, 2.0);
		check("rootcause.oldClientLevel", oldWorld == 12);
		check("rootcause.oldRegionIsZero", oldRegion == 0);
		check("rootcause.newRegionNonZero", newRegion == 12);
		check("rootcause.newRegionNotFullbright", newRegion < 15);
	}

	/**
	 * Рука/модель игрока: block light = max(ванильный, динамический). Пустой/погасший факел
	 * (динамический 0) оставляет ванильное значение — предмет виден, но не светит (не fullbright).
	 */
	private static void testEntityHandLitNotFullbright() {
		// в темноте ванильный block light = 0; с горящим факелом рука получает ~13-14.
		check("entity.handLitInDarkness", DynamicLightModel.entityBlockLight(0, 14) == 14);
		check("entity.nearHandDistance", DynamicLightModel.entityBlockLight(0, 13) == 13);
		// unlit/empty/corrupt: dynamic=0 → ванильное значение не поднимается.
		check("entity.unlitVanillaPreserved", DynamicLightModel.entityBlockLight(0, 0) == 0);
		check("entity.unlitDoesNotFullbright", DynamicLightModel.entityBlockLight(3, 0) == 3);
		// не опускаем более яркий ванильный свет.
		check("entity.neverDarkens", DynamicLightModel.entityBlockLight(15, 10) == 15);
	}

	/**
	 * First-person кадр руки: горящий факел в левой руке поднимает packed block light до
	 * ~13-14 (не fullbright), unlit/empty/corrupt (emission 0) оставляет ванильный свет как есть.
	 */
	private static void testFirstPersonHandRaise() {
		check("fp.litTorchAtHand", DynamicLightModel.firstPersonHandLight(0, DynamicLightModel.TORCH_EMISSION, 0.5) == 13);
		check("fp.litTorchFullBlock", DynamicLightModel.firstPersonHandLight(0, DynamicLightModel.TORCH_EMISSION, 0.0) == 14);
		check("fp.soulTorchAtHand", DynamicLightModel.firstPersonHandLight(0, DynamicLightModel.SOUL_TORCH_EMISSION, 0.5) == 9);
		check("fp.notFullbright", DynamicLightModel.firstPersonHandLight(0, DynamicLightModel.TORCH_EMISSION, 0.0) < 15);
		// unlit/empty/corrupt: emission 0 → ванильный block light не поднимается.
		check("fp.unlitPreservesVanilla", DynamicLightModel.firstPersonHandLight(0, 0, 0.5) == 0);
		check("fp.unlitKeepsBrighterVanilla", DynamicLightModel.firstPersonHandLight(8, 0, 0.5) == 8);
		// Никогда не опускаем уже более яркий ванильный свет.
		check("fp.neverDarkens", DynamicLightModel.firstPersonHandLight(15, DynamicLightModel.TORCH_EMISSION, 5.0) == 15);
	}

	// ------------------------------------------------------------------
	// Перестройка секций
	// ------------------------------------------------------------------

	private static void testSectionRadius() {
		check("section.zero", DynamicLightModel.sectionRadius(0) == 0);
		check("section.one", DynamicLightModel.sectionRadius(1) == 1);
		check("section.torch14", DynamicLightModel.sectionRadius(14) == 1);
		check("section.fifteen", DynamicLightModel.sectionRadius(15) == 1);
		// emission зажат в 0..15, поэтому радиус всегда 1 для любого положительного света.
		check("section.seventeenClamped", DynamicLightModel.sectionRadius(17) == 1);
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

	private static void fail(String name) {
		failed++;
		failures.add(name);
	}
}
