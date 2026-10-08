package com.whitefog.tests.portablelight;

/**
 * Чистая (без Minecraft) модель НЕпульсирующего визуального адаптера модовой тьмы
 * (этап 1.5 визуальный hotfix; используется в тикете 1.7 для проверки «no distant darkness pulse»).
 *
 * <p>Модель повторяет реальные выходы {@code DarknessVisualConfig} (значения утверждены sandbox
 * {@code tests/darkness_visual}): постоянное вычитание lightmap {@code 0.16*eff} вместо пульсирующего
 * косинуса, пол brightness {@code 0.35*eff}, дистанция тумана не ближе {@code 48}, фактор затемнения
 * цвета тумана не выше {@code 0.55} с сохранением {@code voidFactor}. Ванильный пульсирующий косинус
 * оставлен отдельным методом {@link #vanillaDarknessScale} — как эталон контраста для теста.</p>
 *
 * <p>Это logic-only: реальные миксины/шейдеры/кадры не проверяются.</p>
 */
public final class NoPulseVisualModel {
	private NoPulseVisualModel() {
	}

	/** Порог exposure, при котором сервер накладывает Darkness. */
	public static final int MOD_DARK_EXPOSURE_THRESHOLD = 50;
	/** Максимальное постоянное вычитание из lightmap. */
	public static final float MAX_DARKNESS_OFFSET = 0.16f;
	/** Максимальный пол brightness. */
	public static final float MAX_BRIGHTNESS_FLOOR = 0.35f;
	/** Минимальная дистанция конца тумана в блоках. */
	public static final float MIN_FOG_DISTANCE = 48.0f;
	/** Максимальный фактор затемнения цвета тумана. */
	public static final float MAX_FOG_DARKNESS = 0.55f;
	/** Верхний предел шага огибающей за кадр (с). */
	public static final double MAX_FRAME_SECONDS = 0.25;

	// ------------------------------------------------------------------
	// Наши выходы: постоянные, монотонные, без пульсации
	// ------------------------------------------------------------------

	/** Постоянное вычитание lightmap (без косинуса): {@code eff=0} → ровно 0 (vanilla no-op). */
	public static float darknessOffset(float eff) {
		return clamp01(eff) * MAX_DARKNESS_OFFSET;
	}

	/** Пол brightness: {@code eff=0} → ровно 0 (vanilla no-op). */
	public static float brightnessFloor(float eff) {
		return clamp01(eff) * MAX_BRIGHTNESS_FLOOR;
	}

	/** Дистанция конца тумана: при {@code eff=0} ровно vanilla, при {@code eff=1} не меньше 48. */
	public static float fogEnd(float vanillaFogEnd, float eff) {
		float target = Math.max(vanillaFogEnd, MIN_FOG_DISTANCE);
		return lerp(clamp01(eff), vanillaFogEnd, target);
	}

	/** Фактор затемнения цвета тумана: снижает только модовый вклад, сохраняя {@code voidFactor}. */
	public static float fogDarknessFactor(float voidFactor, float blend, float eff) {
		float capped = Math.min(clamp01(blend), MAX_FOG_DARKNESS);
		float after = lerp(clamp01(eff), blend, capped);
		return Math.max(voidFactor, after);
	}

	/**
	 * Эталон ванильного ПУЛЬСИРУЮЩЕГО значения для контраста:
	 * {@code max(0, cos((tick - pt) * PI * 0.025)) * 0.45 * factor}.
	 */
	public static float vanillaDarknessScale(float tickCount, float partialTick, float factor) {
		double cosine = Math.cos((tickCount - partialTick) * Math.PI * 0.025);
		return (float) Math.max(0.0, cosine) * 0.45f * Math.max(0.0f, factor);
	}

	// ------------------------------------------------------------------
	// Огибающая (frame-delta, без перехлёста)
	// ------------------------------------------------------------------

	/**
	 * Ограниченное приближение: шаг не больше {@code speed*dt} с обрезкой dt до
	 * {@link #MAX_FRAME_SECONDS}; не перескакивает цель и не колеблется.
	 */
	public static float approach(float current, float target, float speedPerSecond, double dtSeconds) {
		float step = (float) Math.min(Math.max(0.0, dtSeconds), MAX_FRAME_SECONDS) * Math.max(0.0f, speedPerSecond);
		if (current < target) {
			return Math.min(target, current + step);
		}
		if (current > target) {
			return Math.max(target, current - step);
		}
		return current;
	}

	// ------------------------------------------------------------------
	// Помощники
	// ------------------------------------------------------------------

	public static float clamp01(float v) {
		if (v < 0.0f) {
			return 0.0f;
		}
		if (v > 1.0f) {
			return 1.0f;
		}
		return v;
	}

	public static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}
}
