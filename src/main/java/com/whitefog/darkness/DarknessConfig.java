package com.whitefog.darkness;

/**
 * tunables/константы механики вечной ночи и воздействия тьмы (этап 1.5).
 *
 * <p>Все значения вынесены в один класс, каждое поле снабжено комментарием на русском.
 * Это обычный Java-конфиг: сервер остаётся единственным источником истины, значения
 * читаются на этапе компиляции и не требуют отдельного файла.</p>
 *
 * <p>Единицы времени Minecraft 26.2: {@code 20 тиков = 1 секунда},
 * {@code 24000 тиков = 1 игровой день}. Все длительности ниже — в игровых тиках.</p>
 */
public final class DarknessConfig {
	private DarknessConfig() {
		// Утилитарный класс — экземпляры не создаются.
	}

	// ------------------------------------------------------------------
	// Версия схемы данных вложения
	// ------------------------------------------------------------------

	/** Версия схемы полей тьмы в существующем attachment (для будущих миграций). */
	public static final int DARKNESS_SCHEMA_VERSION = 1;

	// ------------------------------------------------------------------
	// Границы значений (clamp)
	// ------------------------------------------------------------------

	/** Минимум воздействия тьмы (0 — полностью вне тьмы). */
	public static final int EXPOSURE_MIN = 0;
	/** Максимум воздействия тьмы (100 — полная тьма). */
	public static final int EXPOSURE_MAX = 100;
	/** Минимум счётчика непрерывного светлого отдыха. */
	public static final int SAFE_TICKS_MIN = 0;
	/** Максимум счётчика непрерывного светлого отдыха (600 = 30 секунд). */
	public static final int SAFE_TICKS_MAX = 600;
	/** Минимум остатка накопления до следующего sample. */
	public static final int SAMPLE_REMAINDER_MIN = 0;
	/** Максимум остатка накопления до следующего sample (интервал 20, остаток 0..19). */
	public static final int SAMPLE_REMAINDER_MAX = 19;
	/** Минимум Condition в тысячных п.п. (0 = смерть при тёмном sample). */
	public static final int CONDITION_MILLI_MIN = 0;
	/** Максимум Condition в тысячных п.п. (100000 = 100 п.п.). */
	public static final int CONDITION_MILLI_MAX = 100_000;

	// ------------------------------------------------------------------
	// Пороговые значения по уровню block light (0..15) и дельты exposure
	// ------------------------------------------------------------------

	/** Уровень block light, начиная с которого свет считается «ярким» (отдых, восстановление). */
	public static final int LIGHT_BRIGHT_THRESHOLD = 9;
	/** Нижняя граница «промежуточного» света (5..8): exposure не меняется. */
	public static final int LIGHT_NEUTRAL_MIN = 5;
	/** Верхняя граница «промежуточного» света (5..8). */
	public static final int LIGHT_NEUTRAL_MAX = 8;
	/** Верхняя граница «тёмного» света (0..4): exposure растёт. */
	public static final int LIGHT_DARK_MAX = 4;

	/** Дельта exposure за sample в ярком свете / при victorySafe. */
	public static final int EXPOSURE_DELTA_BRIGHT = -2;
	/** Дельта exposure за sample в промежуточном свете. */
	public static final int EXPOSURE_DELTA_NEUTRAL = 0;
	/** Базовая дельта exposure за sample в тёмном свете. */
	public static final int EXPOSURE_DELTA_DARK = 1;
	/** Дополнительная дельта за sample в тёмном свете под открытым небом без укрытия. */
	public static final int EXPOSURE_DELTA_OPEN_SKY_EXTRA = 1;

	// ------------------------------------------------------------------
	// Sample и счётчик светлого отдыха
	// ------------------------------------------------------------------

	/** Интервал между sample в тиках (20 тиков = 1 секунда). */
	public static final int SAMPLE_INTERVAL_TICKS = 20;
	/** Прирост счётчика светлого отдыха за один sample. */
	public static final int SAFE_TICKS_PER_SAMPLE = 20;

	// ------------------------------------------------------------------
	// Condition: урон и восстановление
	// ------------------------------------------------------------------

	/** Порог exposure, при котором Condition уменьшается каждый sample. */
	public static final int CONDITION_DAMAGE_EXPOSURE_THRESHOLD = 90;
	/** Урон Condition за sample в тысячных п.п. (50 = 0.05 п.п.). */
	public static final int CONDITION_DAMAGE_MILLI_PER_SAMPLE = 50;
	/** Восстановление Condition за sample в ярком свете/victorySafe, тысячных п.п. */
	public static final int CONDITION_RECOVERY_MILLI_PER_SAMPLE = 50;

	// ------------------------------------------------------------------
	// Эффект Darkness (vanilla)
	// ------------------------------------------------------------------

	/** Порог exposure, начиная с которого накладывается vanilla Darkness. */
	public static final int DARK_EFFECT_EXPOSURE_THRESHOLD = 50;

	/**
	 * Длительность vanilla Darkness в тиках (60 тиков = 3 секунды).
	 *
	 * <p>Почему не короткая (было 40): vanilla-эффект Darkness имеет blend-длительность
	 * {@link #DARK_BLEND_ADVANCE_TICKS} тиков. Пока остаток длительности выше неё, фактор
	 * смешивания {@code MobEffectInstance$BlendState} держится на 1; как только остаток
	 * опускается до неё, фактор начинает падать. Если сервер обновлял эффект при остатке
	 * 20 (&lt; 22), фактор каждую секунду «проваливался» вниз и возвращался — это и давало
	 * видимую пульсацию затемнения/тумана. Длительность выбрана заметно выше порога обновления,
	 * чтобы до обновления остаток всегда оставался больше blend-advance.</p>
	 */
	public static final int DARK_EFFECT_DURATION_TICKS = 60;

	/**
	 * Обновлять Darkness, когда до конца осталось не больше 40 тиков.
	 *
	 * <p>Обязательно больше {@link #DARK_BLEND_ADVANCE_TICKS} (22): тогда к моменту
	 * обновления остаток ещё превышает blend-advance, фактор никогда не начинает спадать, и
	 * пульсации нет. При уходе exposure ниже порога обновление прекращается, и эффект сам
	 * гаснет за ≤ {@code DARK_EFFECT_DURATION_TICKS + DARK_BLEND_ADVANCE_TICKS} тиков
	 * (≤ 82 тика ≈ 4.1 с), что покрывается «хвостом» клиентского адаптера.</p>
	 */
	public static final int DARK_EFFECT_REFRESH_REMAINING_TICKS = 40;

	/**
	 * Blend-advance vanilla Darkness в тиках. Источник: байткод 26.2
	 * {@code MobEffects} — {@code DARKNESS = new MobEffect(HARMFUL, ...).setBlendDuration(22)}
	 * (все три компонента равны 22); {@code BlendState.tick} считает эффект «видимым», пока
	 * {@code !instance.endsWithin(22)}. Значение зафиксировано здесь как обоснование порога
	 * обновления и проверяется sandbox-моделью {@code tests/darkness_light_fix}.
	 */
	public static final int DARK_BLEND_ADVANCE_TICKS = 22;

	/** Amplifier vanilla Darkness (0 — базовая сила). */
	public static final int DARK_EFFECT_AMPLIFIER = 0;

	// ------------------------------------------------------------------
	// Скорость: именованный modifier и запрет спринта
	// ------------------------------------------------------------------

	/** Порог exposure, начиная с которого накладывается штраф скорости. */
	public static final int SPEED_RESTRICT_EXPOSURE_THRESHOLD = 75;
	/** Путь идентификатора modifier: итоговый id — {@code white_fog:darkness_slow}. */
	public static final String SPEED_MODIFIER_PATH = "darkness_slow";
	/** Множитель скорости (0.85 = 85% от базовой; amount = multiplier - 1 = -0.15). */
	public static final double SPEED_MODIFIER_MULTIPLIER = 0.85D;
	/** Значение modifier для операции {@code ADD_MULTIPLIED_TOTAL} (multiplier - 1). */
	public static final double SPEED_MODIFIER_AMOUNT = SPEED_MODIFIER_MULTIPLIER - 1.0D;

	// ------------------------------------------------------------------
	// Синхронизация снимка тьмы
	// ------------------------------------------------------------------

	/** Минимальный интервал между снимками тьмы в тиках. */
	public static final int SNAPSHOT_MIN_INTERVAL_TICKS = 5;
	/** Периодический heartbeat снимка тьмы в тиках. */
	public static final int SNAPSHOT_HEARTBEAT_TICKS = 100;

	// ------------------------------------------------------------------
	// Вечная ночь (Overworld clock)
	// ------------------------------------------------------------------

	/** Время суток, удерживаемое в Overworld (18000 = полночь). */
	public static final long ETERNAL_NIGHT_DAY_TIME = 18_000L;
	/** 24000 тиков = 1 игровой день; используется для приведения clock к времени суток. */
	public static final long TICKS_PER_DAY = 24_000L;
}
