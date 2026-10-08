package com.whitefog.darkness.light;

import java.util.Locale;

/**
 * Чистая (без импортов Minecraft) политика топлива и ёмкостей источников света (этап 1.6).
 *
 * <p>Здесь сосредоточены все правила таблицы ROADMAP_STEPS.md: ёмкости, добавка за один предмет,
 * проверка переполнения и нормализация значения item-компонента. Класс пригоден для независимого
 * sandbox-теста: методы не ссылаются на игровые классы.</p>
 */
public final class LightFuelPolicy {
	private LightFuelPolicy() {
	}

	/**
	 * Вид поддерживаемого источника света. Настенные варианты torch делят ёмкость/бонус
	 * со стоячими (см. {@link #capacityTicks(SourceKind)}).
	 */
	public enum SourceKind {
		/** Обычный факел (torch / wall_torch). */
		TORCH,
		/** Факел душ (soul_torch / soul_wall_torch). */
		SOUL_TORCH,
		/** Фонарь (lantern). */
		LANTERN,
		/** Фонарь душ (soul_lantern). */
		SOUL_LANTERN,
		/** Костёр (campfire / soul_campfire). */
		CAMPFIRE
	}

	/** Допустимое топливо. */
	public enum Fuel {
		/** Уголь. */
		COAL,
		/** Древесный уголь. */
		CHARCOAL,
		/** Палочка (только костёр). */
		STICK,
		/** Бревно дуба (только костёр). */
		OAK_LOG,
		/** Бревно ели (только костёр). */
		SPRUCE_LOG,
		/** Бревно берёзы (только костёр). */
		BIRCH_LOG
	}

	/** Ёмкость источника в тиках (0..capacity). */
	public static int capacityTicks(SourceKind kind) {
		return switch (kind) {
			case TORCH -> LightConfig.CAPACITY_TORCH;
			case SOUL_TORCH -> LightConfig.CAPACITY_SOUL_TORCH;
			case LANTERN -> LightConfig.CAPACITY_LANTERN;
			case SOUL_LANTERN -> LightConfig.CAPACITY_SOUL_LANTERN;
			case CAMPFIRE -> LightConfig.CAPACITY_CAMPFIRE;
		};
	}

	/** Генерационный бонус источника в тиках. Для костра зависит от текущего LIT. */
	public static int generatedBonusTicks(SourceKind kind, boolean lit) {
		return switch (kind) {
			case TORCH -> LightConfig.BONUS_TORCH;
			case SOUL_TORCH -> LightConfig.BONUS_SOUL_TORCH;
			case LANTERN -> LightConfig.BONUS_LANTERN;
			case SOUL_LANTERN -> LightConfig.BONUS_SOUL_LANTERN;
			case CAMPFIRE -> lit ? LightConfig.BONUS_CAMPFIRE_LIT : LightConfig.BONUS_CAMPFIRE_UNLIT;
		};
	}

	/**
	 * Добавка за один предмет данного топлива в источник; {@code -1} — топливо не принимается.
	 */
	public static int additionTicks(SourceKind kind, Fuel fuel) {
		return switch (kind) {
			case TORCH -> coalLike(fuel, LightConfig.ADD_TORCH_COAL);
			case SOUL_TORCH -> coalLike(fuel, LightConfig.ADD_SOUL_TORCH_COAL);
			case LANTERN -> coalLike(fuel, LightConfig.ADD_LANTERN_COAL);
			case SOUL_LANTERN -> coalLike(fuel, LightConfig.ADD_SOUL_LANTERN_COAL);
			case CAMPFIRE -> campfireAddition(fuel);
		};
	}

	private static int coalLike(Fuel fuel, int coalAddition) {
		if (fuel == Fuel.COAL || fuel == Fuel.CHARCOAL) {
			return coalAddition;
		}
		return -1;
	}

	private static int campfireAddition(Fuel fuel) {
		return switch (fuel) {
			case COAL, CHARCOAL -> LightConfig.ADD_CAMPFIRE_COAL;
			case STICK -> LightConfig.ADD_CAMPFIRE_STICK;
			case OAK_LOG, SPRUCE_LOG, BIRCH_LOG -> LightConfig.ADD_CAMPFIRE_LOG;
		};
	}

	/**
	 * Нормализация значения компонента: {@code null}/отрицательное → 0; значение больше ёмкости
	 * зажимается до ёмкости (capacity). Absent/invalid/negative = 0.
	 */
	public static int normalizeComponentValue(Integer componentValue, int capacity) {
		if (componentValue == null || componentValue < 0) {
			return 0;
		}
		return Math.min(componentValue, Math.max(0, capacity));
	}

	/** Проверка «remain + addition помещается в capacity» (без частичного добавления). */
	public static boolean fits(int remaining, int addition, int capacity) {
		if (addition <= 0) {
			return false;
		}
		return (long) remaining + (long) addition <= (long) capacity;
	}

	/** Человекочитаемое имя topлива (для логов/команд). */
	public static String fuelName(Fuel fuel) {
		return fuel.name().toLowerCase(Locale.ROOT);
	}
}
