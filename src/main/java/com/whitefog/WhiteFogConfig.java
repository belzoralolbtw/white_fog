package com.whitefog;

/**
 * Конфигурация мода «Белая Мгла» (этап 1.1).
 *
 * <p>Все изменяемые (tunable) значения выживания вынесены сюда, чтобы их можно было
 * править в одном месте. Каждое значение снабжено комментарием на русском языке.
 * Это обычный Java-конфиг с константами: он читается на этапе компиляции и не требует
 * отдельного файла, при этом сервер остаётся единственным источником истины.</p>
 *
 * <p>Единицы времени Minecraft 26.2: {@code 20 тиков = 1 секунда},
 * {@code 24000 тиков = 1 игровой день}.</p>
 */
public final class WhiteFogConfig {
	private WhiteFogConfig() {
		// Утилитарный класс — экземпляры не создаются.
	}

	// ------------------------------------------------------------------
	// Безопасные значения по умолчанию (новый игрок / отсутствующий компонент)
	// ------------------------------------------------------------------

	/** Усталость по умолчанию, 0–100. 100 = отдохнувший игрок. */
	public static final float DEFAULT_FATIGUE = 100.0F;
	/** Состояние тела по умолчанию, 0–100. 100 = здоровое тело. */
	public static final float DEFAULT_BODY = 100.0F;
	/** Калории по умолчанию, 0–3000. 3000 = сытый игрок. */
	public static final float DEFAULT_CALORIES = 3000.0F;
	/** Вода по умолчанию, 0–100. 100 = полная гидратация. */
	public static final float DEFAULT_WATER = 100.0F;
	/** Condition (общее состояние) по умолчанию, 0–100. */
	public static final float DEFAULT_CONDITION = 100.0F;
	/** Вес по умолчанию в килограммах (точность 0.1). */
	public static final double DEFAULT_WEIGHT_KG = 0.0D;
	/** Ощущаемая температура по умолчанию в градусах Цельсия. */
	public static final float DEFAULT_TEMPERATURE_C = 0.0F;
	/** Мокрота одежды по умолчанию для каждого слота, 0–100. */
	public static final float DEFAULT_CLOTHING_WETNESS = 0.0F;
	/** Дизентерия по умолчанию отсутствует (0 тиков). */
	public static final int DEFAULT_DYSENTERY_TICKS = 0;

	// ------------------------------------------------------------------
	// Границы значений (clamp). Любое присваивание зажимается в эти рамки.
	// ------------------------------------------------------------------

	/** Минимум усталости. */
	public static final float FATIGUE_MIN = 0.0F;
	/** Максимум усталости. */
	public static final float FATIGUE_MAX = 100.0F;
	/** Минимум состояния тела. */
	public static final float BODY_MIN = 0.0F;
	/** Максимум состояния тела. */
	public static final float BODY_MAX = 100.0F;
	/** Минимум калорий. */
	public static final float CALORIES_MIN = 0.0F;
	/** Максимум калорий. */
	public static final float CALORIES_MAX = 3000.0F;
	/** Минимум воды. */
	public static final float WATER_MIN = 0.0F;
	/** Максимум воды. */
	public static final float WATER_MAX = 100.0F;
	/** Минимум Condition. */
	public static final float CONDITION_MIN = 0.0F;
	/** Максимум Condition. */
	public static final float CONDITION_MAX = 100.0F;
	/** Минимум мокроты слота одежды. */
	public static final float WETNESS_MIN = 0.0F;
	/** Максимум мокроты слота одежды. */
	public static final float WETNESS_MAX = 100.0F;
	/** Минимальный вес в килограммах. */
	public static final double WEIGHT_MIN_KG = 0.0D;
	/** Максимальный вес в килограммах (защита от абсурдных значений). */
	public static final double WEIGHT_MAX_KG = 500.0D;
	/** Минимум оставшегося времени дизентерии в тиках. */
	public static final int DYSENTERY_MIN_TICKS = 0;
	/**
	 * Максимум оставшегося времени дизентерии в тиках.
	 * Не более 7 игровых дней: 7 * 24000 = 168000 тиков.
	 */
	public static final int DYSENTERY_MAX_TICKS = 7 * 24_000;
	/** Минимум ощущаемой температуры в градусах Цельсия. */
	public static final float TEMPERATURE_MIN_C = -100.0F;
	/** Максимум ощущаемой температуры в градусах Цельсия. */
	public static final float TEMPERATURE_MAX_C = 100.0F;

	// ------------------------------------------------------------------
	// Точность и периодичность
	// ------------------------------------------------------------------

	/** Шаг точности веса: 0.1 кг. Значение всегда округляется до этого шага. */
	public static final double WEIGHT_STEP_KG = 0.1D;
	/**
	 * Периодичность полной синхронизации состояния с клиентом, в тиках.
	 * 10 тиков = 0.5 секунды — достаточно для плавного HUD.
	 */
	public static final int SYNC_INTERVAL_TICKS = 10;

	// ------------------------------------------------------------------
	// Временные константы (для читаемости расчётов, не для магии чисел)
	// ------------------------------------------------------------------

	/** 20 тиков = 1 секунда. */
	public static final int TICKS_PER_SECOND = 20;
	/** 24000 тиков = 1 игровой день. */
	public static final int TICKS_PER_DAY = 24_000;

	// ------------------------------------------------------------------
	// Этап 1.3 — разрушение блоков и станции (все значения в ИГРОВЫХ тиках)
	// ------------------------------------------------------------------

	/**
	 * Длительность ручного разрушения «обычного мягкого» блока (трава, листва,
	 * цветы, саженцы, грибы, снег, фонарь) — 40 тиков (2 секунды).
	 * Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_SOFT_DEFAULT_TICKS = 40;
	/**
	 * Земля/песок/гравий/глина РУКОЙ — 60 тиков (3 секунды).
	 * Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_SOFT_SOIL_HAND_TICKS = 60;
	/**
	 * Земля/песок/гравий/глина ЛОПАТОЙ — 10 тиков (0.5 секунды).
	 * Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_SOFT_SOIL_SHOVEL_TICKS = 10;
	/**
	 * Лёд (ice/packed_ice/blue_ice/frosted_ice) РУКОЙ — 80 тиков (4 секунды).
	 * Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_SOFT_ICE_TICKS = 80;
	/**
	 * Снятие любой станции (верстак, ткацкий станок, печь, котёл, наковальня) —
	 * 40 тиков (2 секунды). Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_STATION_TICKS = 40;
	/**
	 * Модовое кайло, tier BRONZE: разрушение твёрдого блока — 120 тиков (самый медленный tier).
	 * Диапазон 40–120 тиков утверждён ROADMAP_STEPS.md; конкретное значение — дефолт конфига.
	 */
	public static final int BREAK_HARD_BRONZE_TICKS = 120;
	/** Модовое кайло, tier IRON: разрушение твёрдого блока — 80 тиков. */
	public static final int BREAK_HARD_IRON_TICKS = 80;
	/** Модовое кайло, tier STEEL: разрушение твёрдого блока — 40 тиков (самый быстрый tier). */
	public static final int BREAK_HARD_STEEL_TICKS = 40;
	/**
	 * Cooldown подсказки {@code Слишком крепко — нужен инструмент} — 20 тиков
	 * на игрока и причину отказа. Значение утверждено ROADMAP_STEPS.md (этап 1.3).
	 */
	public static final int BREAK_REFUSE_MESSAGE_COOLDOWN_TICKS = 20;
	/**
	 * Допуск дальности взаимодействия с блоком при старте и каждом обновлении.
	 * Вызов на сервере: {@code Player#isWithinBlockInteractionRange(pos, 1.0)}.
	 */
	public static final double BREAK_INTERACTION_RANGE_MARGIN = 1.0D;

	/** Exact item id модового кайла, tier BRONZE (регистрация предмета — этап 6.4). */
	public static final String BREAK_MOD_PICKAXE_BRONZE = "white_fog:bronze_pickaxe";
	/** Exact item id модового кайла, tier IRON (регистрация предмета — этап 6.4). */
	public static final String BREAK_MOD_PICKAXE_IRON = "white_fog:iron_pickaxe";
	/** Exact item id модового кайла, tier STEEL (регистрация предмета — этап 6.4). */
	public static final String BREAK_MOD_PICKAXE_STEEL = "white_fog:steel_pickaxe";

	// ------------------------------------------------------------------
	// Этап 1.4 — плоский камень и камушки (значения паспорта из ROADMAP_STEPS.md)
	// ------------------------------------------------------------------

	/** Размер стака «Плоский камень» (flat_stone). */
	public static final int FLAT_STONE_STACK_SIZE = 16;
	/** Размер стака «Камушка» (small_stone). */
	public static final int SMALL_STONE_STACK_SIZE = 64;
	/** Масса «Плоского камня» в кг (для будущей системы веса, этап 1.x+). */
	public static final double FLAT_STONE_MASS_KG = 3.0D;
	/** Масса «Камушки» в кг (для будущей системы веса, этап 1.x+). */
	public static final double SMALL_STONE_MASS_KG = 0.2D;
	/**
	 * Cooldown подбора камушка на игрока, тиков (утверждено ТЗ: камушек имеет cooldown 2 тика,
	 * чтобы двойной клик не выдал больше одной камушки).
	 */
	public static final int SMALL_STONE_PICKUP_COOLDOWN_TICKS = 2;
	/**
	 * Задержка подбора у остатка, выброшенного при полном инвентаре (pending drop),
	 * тиков. Утверждено ТЗ этапа 1.4.
	 */
	public static final int PENDING_DROP_PICKUP_DELAY_TICKS = 10;
	/**
	 * Recovery-взаимодействие «2 cobblestone → 1 поставленный flat_stone»:
	 * длительность в игровых тиках (утверждено ТЗ: 100 тиков).
	 */
	public static final int RECOVERY_FLAT_STONE_TICKS = 100;
	/** Recovery: сколько cobblestone требуется (утверждено ТЗ: 2). */
	public static final int RECOVERY_COBBLESTONE_COST = 2;
	/**
	 * Порог отмены recovery по движению игрока, в квадраte блоков.
	 * 0.05 ≈ 0.22 блока: микро-дрожание камеры задачу не отменяет, реальный шаг — отменяет.
	 */
	public static final double RECOVERY_CANCEL_MOVE_SQR = 0.05D;
	/** schemaVersion block entity станции (задел под миграции, этап 1.4 = 1). */
	public static final int FLAT_STONE_SCHEMA_VERSION = 1;
	/** Точный текст отказа для занятой станции (утверждён ТЗ этапа 1.4). */
	public static final String MESSAGE_STATION_BUSY = "Сначала забери материалы и результат";
	/** Точный текст отказа для полного инвентаря (поверх pending-дропа). */
	public static final String MESSAGE_INVENTORY_FULL = "Инвентарь полон — предмет выпал рядом";
}
