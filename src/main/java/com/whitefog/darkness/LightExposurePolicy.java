package com.whitefog.darkness;

/**
 * Чистая (без Minecraft) политика воздействия тьмы за один серверный sample (этап 1.5).
 *
 * <p>Класс не импортирует ни одного класса Minecraft: это делает формулу тестируемой
 * отдельно от движка. Адаптеры (свет уровня, эффект, modifier, урон) живут в
 * {@code com.whitefog.darkness.LightExposureService}.</p>
 *
 * <p>Ссылка на источник/логику: значения и пороги утверждены ROADMAP_STEPS.md, этап 1.5
 * (строки 42–53). Реальный код Minecraft, на который опираются адаптеры, прочитан по
 * деобфусцированным jar 26.2 ({@code BlockAndLightGetter#getBrightness/canSeeSky},
 * {@code MobEffects.DARKNESS}, {@code Attributes.MOVEMENT_SPEED}). Удалённый open-source
 * поиск в этой среде недоступен (нет сети), поэтому формула выверена по контракту тикета
 * и API-сигнатурам реальных jar.</p>
 *
 * <p>Правила одного sample (block light = серверный уровень света в клетке глаза):</p>
 * <ol>
 *     <li>exposure: {@code victorySafe} → −2; иначе {@code blockLight >= 9} → −2;
 *         {@code 5..8} → 0; {@code 0..4} → +1 (и ещё +1, если видно небо и нет укрытия);
 *         итог clamp 0..100;</li>
 *     <li>светлый отдых: {@code blockLight >= 9 || victorySafe} → +20 (cap 600), иначе 0;
 *         после 600 exposure обнуляется;</li>
 *     <li>Condition: {@code exposure >= 90} → −50 тысячных (0 → deathRequired);
 *         иначе в ярком свете/victorySafe → +50 (cap 100000); в промежуточном свете без изменений;</li>
 *     <li>флаги: speedRestricted = exposure >= 75; darkEffectRequired = exposure >= 50.</li>
 * </ol>
 */
public final class LightExposurePolicy {
	private LightExposurePolicy() {
	}

	/**
	 * Проектный DTO входа политики (НЕ vanilla-класс). Описывает контекст клетки глаза
	 * и состояние игрока, влияющие на один sample.
	 *
	 * @param blockLight  серверный уровень block light в клетке глаза, 0..15;
	 * @param canSeeSky   видно ли небо из клетки глаза;
	 * @param shelter     укрытие (до готовности shelter-сервиса адаптер отдаёт {@code false});
	 * @param alive       жив ли игрок (для мёртвого sample не выполняется);
	 * @param exempt      исключён ли игрок из штрафов (creative/spectator);
	 * @param victorySafe победа (до готовности механики адаптер отдаёт {@code false}).
	 */
	public record Input(int blockLight, boolean canSeeSky, boolean shelter, boolean alive, boolean exempt,
			boolean victorySafe) {
	}

	/**
	 * Неизменяемый результат одного sample.
	 *
	 * @param exposure         новое воздействие тьмы 0..100;
	 * @param safeLightTicks   новый счётчик светлого отдыха 0..600;
	 * @param conditionMilli   новое Condition в тысячных п.п. 0..100000;
	 * @param speedRestricted  нужен ли штраф скорости;
	 * @param darkEffectRequired нужен ли vanilla Darkness;
	 * @param deathRequired    должен ли сервер завершить жизнь игрока обычным death path.
	 */
	public record Result(int exposure, int safeLightTicks, int conditionMilli, boolean speedRestricted,
			boolean darkEffectRequired, boolean deathRequired) {
	}

	/**
	 * Вычисляет результат одного sample. Функция детерминирована: одинаковые аргументы
	 * дают одинаковый результат и не зависят от внешнего состояния.
	 *
	 * @param input              контекст клетки глаза;
	 * @param currentExposure    текущее exposure 0..100;
	 * @param currentSafeTicks   текущий счётчик светлого отдыха 0..600;
	 * @param currentConditionMilli текущее Condition в тысячных п.п. 0..100000.
	 */
	public static Result evaluate(Input input, int currentExposure, int currentSafeTicks,
			int currentConditionMilli) {
		int exposure = clamp(currentExposure, DarknessConfig.EXPOSURE_MIN, DarknessConfig.EXPOSURE_MAX);
		int safeTicks = clamp(currentSafeTicks, DarknessConfig.SAFE_TICKS_MIN, DarknessConfig.SAFE_TICKS_MAX);
		int condition = clamp(currentConditionMilli,
				DarknessConfig.CONDITION_MILLI_MIN, DarknessConfig.CONDITION_MILLI_MAX);

		// Мёртвый/исключённый игрок: sample не меняет шкалы и не даёт штрафов.
		if (!input.alive() || input.exempt()) {
			return new Result(exposure, safeTicks, condition, false, false, false);
		}

		boolean bright = input.victorySafe() || input.blockLight() >= DarknessConfig.LIGHT_BRIGHT_THRESHOLD;

		// 1) Изменение exposure за sample.
		int delta;
		if (input.victorySafe()) {
			delta = DarknessConfig.EXPOSURE_DELTA_BRIGHT;
		} else if (input.blockLight() >= DarknessConfig.LIGHT_BRIGHT_THRESHOLD) {
			delta = DarknessConfig.EXPOSURE_DELTA_BRIGHT;
		} else if (input.blockLight() >= DarknessConfig.LIGHT_NEUTRAL_MIN) {
			delta = DarknessConfig.EXPOSURE_DELTA_NEUTRAL;
		} else {
			delta = DarknessConfig.EXPOSURE_DELTA_DARK;
			if (input.canSeeSky() && !input.shelter()) {
				delta += DarknessConfig.EXPOSURE_DELTA_OPEN_SKY_EXTRA;
			}
		}
		exposure = clamp(exposure + delta, DarknessConfig.EXPOSURE_MIN, DarknessConfig.EXPOSURE_MAX);

		// 2) Счётчик непрерывного светлого отдыха.
		if (bright) {
			safeTicks = Math.min(safeTicks + DarknessConfig.SAFE_TICKS_PER_SAMPLE, DarknessConfig.SAFE_TICKS_MAX);
		} else {
			safeTicks = DarknessConfig.SAFE_TICKS_MIN;
		}
		// Полный сброс воздействия после непрерывного отдыха.
		if (safeTicks >= DarknessConfig.SAFE_TICKS_MAX) {
			exposure = DarknessConfig.EXPOSURE_MIN;
		}

		// 3) Condition: сначала истощение тьмой, затем восстановление в ярком свете.
		boolean deathRequired = false;
		if (exposure >= DarknessConfig.CONDITION_DAMAGE_EXPOSURE_THRESHOLD) {
			condition -= DarknessConfig.CONDITION_DAMAGE_MILLI_PER_SAMPLE;
			if (condition <= DarknessConfig.CONDITION_MILLI_MIN) {
				condition = DarknessConfig.CONDITION_MILLI_MIN;
				deathRequired = true;
			}
		} else if (bright) {
			condition = Math.min(condition + DarknessConfig.CONDITION_RECOVERY_MILLI_PER_SAMPLE,
					DarknessConfig.CONDITION_MILLI_MAX);
		}

		boolean speedRestricted = exposure >= DarknessConfig.SPEED_RESTRICT_EXPOSURE_THRESHOLD;
		boolean darkEffectRequired = exposure >= DarknessConfig.DARK_EFFECT_EXPOSURE_THRESHOLD;
		return new Result(exposure, safeTicks, condition, speedRestricted, darkEffectRequired, deathRequired);
	}

	private static int clamp(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
