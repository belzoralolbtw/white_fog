package com.whitefog.tests.darkness;

/**
 * Чистый sandbox-зеркало {@code com.whitefog.darkness.LightExposurePolicy} (этап 1.5).
 *
 * <p><b>Это логика без Minecraft</b> и НЕ proof runtime-поведения (эффекты, attributes, сеть,
 * реальные {@code Level#getBrightness}). Тест доказывает только формулу по контракту тикета.
 * Значения и пороги синхронизированы с {@code DarknessConfig} в src.</p>
 */
public final class ExposurePolicy {

	// Синхронизировано с DarknessConfig.
	public static final int EXPOSURE_MIN = 0;
	public static final int EXPOSURE_MAX = 100;
	public static final int SAFE_TICKS_MIN = 0;
	public static final int SAFE_TICKS_MAX = 600;
	public static final int CONDITION_MILLI_MIN = 0;
	public static final int CONDITION_MILLI_MAX = 100_000;
	public static final int LIGHT_BRIGHT_THRESHOLD = 9;
	public static final int LIGHT_NEUTRAL_MIN = 5;
	public static final int EXPOSURE_DELTA_BRIGHT = -2;
	public static final int EXPOSURE_DELTA_NEUTRAL = 0;
	public static final int EXPOSURE_DELTA_DARK = 1;
	public static final int EXPOSURE_DELTA_OPEN_SKY_EXTRA = 1;
	public static final int SAFE_TICKS_PER_SAMPLE = 20;
	public static final int CONDITION_DAMAGE_EXPOSURE_THRESHOLD = 90;
	public static final int CONDITION_DAMAGE_MILLI_PER_SAMPLE = 50;
	public static final int CONDITION_RECOVERY_MILLI_PER_SAMPLE = 50;
	public static final int DARK_EFFECT_EXPOSURE_THRESHOLD = 50;
	public static final int SPEED_RESTRICT_EXPOSURE_THRESHOLD = 75;

	private ExposurePolicy() {
	}

	public record Input(int blockLight, boolean canSeeSky, boolean shelter, boolean alive, boolean exempt,
			boolean victorySafe) {
	}

	public record Result(int exposure, int safeTicks, int conditionMilli, boolean speedRestricted,
			boolean darkEffectRequired, boolean deathRequired) {
	}

	public static Result evaluate(Input input, int currentExposure, int currentSafeTicks,
			int currentConditionMilli) {
		int exposure = clamp(currentExposure, EXPOSURE_MIN, EXPOSURE_MAX);
		int safeTicks = clamp(currentSafeTicks, SAFE_TICKS_MIN, SAFE_TICKS_MAX);
		int condition = clamp(currentConditionMilli, CONDITION_MILLI_MIN, CONDITION_MILLI_MAX);

		if (!input.alive() || input.exempt()) {
			return new Result(exposure, safeTicks, condition, false, false, false);
		}

		boolean bright = input.victorySafe() || input.blockLight() >= LIGHT_BRIGHT_THRESHOLD;

		int delta;
		if (input.victorySafe()) {
			delta = EXPOSURE_DELTA_BRIGHT;
		} else if (input.blockLight() >= LIGHT_BRIGHT_THRESHOLD) {
			delta = EXPOSURE_DELTA_BRIGHT;
		} else if (input.blockLight() >= LIGHT_NEUTRAL_MIN) {
			delta = EXPOSURE_DELTA_NEUTRAL;
		} else {
			delta = EXPOSURE_DELTA_DARK;
			if (input.canSeeSky() && !input.shelter()) {
				delta += EXPOSURE_DELTA_OPEN_SKY_EXTRA;
			}
		}
		exposure = clamp(exposure + delta, EXPOSURE_MIN, EXPOSURE_MAX);

		if (bright) {
			safeTicks = Math.min(safeTicks + SAFE_TICKS_PER_SAMPLE, SAFE_TICKS_MAX);
		} else {
			safeTicks = SAFE_TICKS_MIN;
		}
		if (safeTicks >= SAFE_TICKS_MAX) {
			exposure = EXPOSURE_MIN;
		}

		boolean deathRequired = false;
		if (exposure >= CONDITION_DAMAGE_EXPOSURE_THRESHOLD) {
			condition -= CONDITION_DAMAGE_MILLI_PER_SAMPLE;
			if (condition <= CONDITION_MILLI_MIN) {
				condition = CONDITION_MILLI_MIN;
				deathRequired = true;
			}
		} else if (bright) {
			condition = Math.min(condition + CONDITION_RECOVERY_MILLI_PER_SAMPLE, CONDITION_MILLI_MAX);
		}

		boolean speedRestricted = exposure >= SPEED_RESTRICT_EXPOSURE_THRESHOLD;
		boolean darkEffectRequired = exposure >= DARK_EFFECT_EXPOSURE_THRESHOLD;
		return new Result(exposure, safeTicks, condition, speedRestricted, darkEffectRequired, deathRequired);
	}

	public static int clamp(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
