package com.whitefog.tests.darkness;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Мини-модель серверного состояния выживания для sandbox (этап 1.5).
 *
 * <p>Повторяет ровно те поля/ключи, что добавляются в реальный
 * {@code com.whitefog.state.PlayerSurvivalState} (NBT-ключи {@code darkness_schema},
 * {@code light_exposure}, {@code safe_light_ticks}, {@code sample_remainder_ticks},
 * {@code darkness_condition_milli}, {@code darkness_revision}). Сериализация — простой
 * текст/карта (в src используется {@code CompoundTag}).</p>
 *
 * <p>Ключевое: миграция отсутствующего поля {@code darkness_condition_milli} из старого
 * {@code condition}: {@code round(clamp(condition,0,100)*1000)}.</p>
 */
public final class StateModel {
	public static final int CONDITION_MILLI_MAX = 100_000;
	public static final int EXPOSURE_MAX = 100;
	public static final int SAFE_TICKS_MAX = 600;
	public static final int SAMPLE_REMAINDER_MAX = 19;

	public float condition = 100.0F;
	public int darknessSchema;
	public int lightExposure;
	public int safeLightTicks;
	public int sampleRemainderTicks;
	public int darknessConditionMilli;
	public long darknessRevision;

	private StateModel() {
	}

	/** Состояние без полей тьмы (старый формат): schema=0, milli не задан. */
	public static StateModel legacy(float conditionPct) {
		StateModel m = new StateModel();
		m.condition = conditionPct;
		m.darknessSchema = 0;
		m.lightExposure = 0;
		m.safeLightTicks = 0;
		m.sampleRemainderTicks = 0;
		m.darknessConditionMilli = -1; // маркер «поля нет» до миграции
		m.darknessRevision = 0L;
		return m;
	}

	/** Новое состояние со значениями по умолчанию (после миграции). */
	public static StateModel fresh() {
		StateModel m = new StateModel();
		m.darknessSchema = 1;
		m.lightExposure = 0;
		m.safeLightTicks = 0;
		m.sampleRemainderTicks = 0;
		m.darknessConditionMilli = CONDITION_MILLI_MAX;
		m.condition = 100.0F;
		m.darknessRevision = 0L;
		return m;
	}

	/** Сериализация в стабильную строку {@code key=value;} (аналог NBT-компаунда). */
	public String serialize() {
		StringBuilder sb = new StringBuilder();
		sb.append("condition=").append(this.condition).append(';');
		sb.append("darkness_schema=").append(this.darknessSchema).append(';');
		sb.append("light_exposure=").append(this.lightExposure).append(';');
		sb.append("safe_light_ticks=").append(this.safeLightTicks).append(';');
		sb.append("sample_remainder_ticks=").append(this.sampleRemainderTicks).append(';');
		sb.append("darkness_condition_milli=").append(this.darknessConditionMilli).append(';');
		sb.append("darkness_revision=").append(this.darknessRevision).append(';');
		return sb.toString();
	}

	/** Сериализация старого формата: только {@code condition}, без полей тьмы. */
	public String serializeLegacy() {
		return "condition=" + this.condition + ";";
	}

	/** Обратная операция + миграция отсутствующего {@code darkness_condition_milli}. */
	public static StateModel deserialize(String data) {		Map<String, String> map = new LinkedHashMap<>();
		for (String part : data.split(";")) {
			if (part.isEmpty()) {
				continue;
			}
			int eq = part.indexOf('=');
			if (eq > 0) {
				map.put(part.substring(0, eq), part.substring(eq + 1));
			}
		}
		StateModel m = new StateModel();
		float legacyCondition = parseFloat(map.getOrDefault("condition", "100.0"), 100.0F);
		m.darknessSchema = Math.max(1, parseInt(map.getOrDefault("darkness_schema", "1"), 1));
		m.lightExposure = ExposurePolicy.clamp(parseInt(map.getOrDefault("light_exposure", "0"), 0),
				0, EXPOSURE_MAX);
		m.safeLightTicks = ExposurePolicy.clamp(parseInt(map.getOrDefault("safe_light_ticks", "0"), 0),
				0, SAFE_TICKS_MAX);
		m.sampleRemainderTicks = ExposurePolicy.clamp(
				parseInt(map.getOrDefault("sample_remainder_ticks", "0"), 0), 0, SAMPLE_REMAINDER_MAX);
		if (map.containsKey("darkness_condition_milli")) {
			m.darknessConditionMilli = ExposurePolicy.clamp(
					parseInt(map.get("darkness_condition_milli"), CONDITION_MILLI_MAX), 0, CONDITION_MILLI_MAX);
		} else {
			float clamped = Math.max(0.0F, Math.min(100.0F, legacyCondition));
			m.darknessConditionMilli = ExposurePolicy.clamp(Math.round(clamped * 1000.0F), 0, CONDITION_MILLI_MAX);
		}
		m.condition = m.darknessConditionMilli / 1000.0F;
		m.darknessRevision = Math.max(0L, parseLong(map.getOrDefault("darkness_revision", "0"), 0L));
		return m;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static long parseLong(String value, long fallback) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static float parseFloat(String value, float fallback) {
		try {
			return Float.parseFloat(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
