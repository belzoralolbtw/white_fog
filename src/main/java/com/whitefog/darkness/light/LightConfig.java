package com.whitefog.darkness.light;

/**
 * tunables/константы механики гаснущих источников света (этап 1.6).
 *
 * <p>Все значения вынесены в один класс, каждое поле снабжено комментарием на русском.
 * Сервер остаётся единственным источником истины; значения читаются на этапе компиляции.
 * Единицы времени Minecraft 26.2: {@code 20 тиков = 1 секунда}, длительности — в игровых тиках.</p>
 *
 * <p>Значения таблицы топлива/ёмкостей утверждены ROADMAP_STEPS.md (этап 1.6, строки 44–75).</p>
 */
public final class LightConfig {
	private LightConfig() {
		// Утилитарный класс — экземпляры не создаются.
	}

	// ------------------------------------------------------------------
	// Имена данных
	// ------------------------------------------------------------------

	/** Имя boolean-свойства состояния (torch/wall_torch/lantern/soul_lantern): lit = true. */
	public static final String LIT_PROPERTY_NAME = "white_fog_lit";
	/** Путь item-компонента {@code white_fog:light_fuel}. */
	public static final String FUEL_COMPONENT_PATH = "light_fuel";
	/** Идентификатор persistent-хранилища источников (SavedData) на измерение. */
	public static final String STORE_ID = "light_sources";
	/** Версия схемы хранилища (для будущих миграций). */
	public static final int STORE_SCHEMA_VERSION = 1;

	// ------------------------------------------------------------------
	// Генерационные бонусы (естественно сгенерированный источник без записи)
	// ------------------------------------------------------------------

	/** Факел: 6000 тиков генерационного запаса. */
	public static final int BONUS_TORCH = 6_000;
	/** Факел душ: 4000 тиков. */
	public static final int BONUS_SOUL_TORCH = 4_000;
	/** Фонарь: 12000 тиков. */
	public static final int BONUS_LANTERN = 12_000;
	/** Фонарь душ: 8000 тиков. */
	public static final int BONUS_SOUL_LANTERN = 8_000;
	/** Зажжённый костёр: 8000 тиков. */
	public static final int BONUS_CAMPFIRE_LIT = 8_000;
	/** Погасший костёр: 0 тиков (бонуса нет). */
	public static final int BONUS_CAMPFIRE_UNLIT = 0;

	// ------------------------------------------------------------------
	// Ёмкости (capacity) по видам источников
	// ------------------------------------------------------------------

	/** Ёмкость факела. */
	public static final int CAPACITY_TORCH = 12_000;
	/** Ёмкость факела душ. */
	public static final int CAPACITY_SOUL_TORCH = 8_000;
	/** Ёмкость фонаря. */
	public static final int CAPACITY_LANTERN = 24_000;
	/** Ёмкость фонаря душ. */
	public static final int CAPACITY_SOUL_LANTERN = 16_000;
	/** Ёмкость костра. */
	public static final int CAPACITY_CAMPFIRE = 16_000;

	// ------------------------------------------------------------------
	// Добавка топлива за один предмет (по видам источников и топливу)
	// ------------------------------------------------------------------
	//
	// Повторная заправка (тикет поверх 1.7): уголь/древесный уголь добавляет РОВНО 20% ёмкости
	// (целое, округление вниз), поэтому один предмет не заполняет бак целиком, и «осталось 20%
	// топлива → +20%». Переполнение отклоняется без частичного списания (см. LightFuelPolicy.fits).
	// Значения = ровно 20% от CAPACITY_* и продублированы с утверждённой sandbox-таблицей
	// (tests/eternal_darkness/portable_light, FuelStateModel.coalAddition).

	/** Уголь/древесный уголь в факел: 20% от 12000 = +2400. */
	public static final int ADD_TORCH_COAL = 2_400;
	/** Уголь/древесный уголь в факел душ: 20% от 8000 = +1600. */
	public static final int ADD_SOUL_TORCH_COAL = 1_600;
	/** Уголь/древесный уголь в фонарь: 20% от 24000 = +4800. */
	public static final int ADD_LANTERN_COAL = 4_800;
	/** Уголь/древесный уголь в фонарь душ: 20% от 16000 = +3200. */
	public static final int ADD_SOUL_LANTERN_COAL = 3_200;
	/** Уголь/древесный уголь в костёр: 20% от 16000 = +3200. */
	public static final int ADD_CAMPFIRE_COAL = 3_200;
	/** Палочка в костёр: +2000 (НЕ уголь; прежнее значение сохранено). */
	public static final int ADD_CAMPFIRE_STICK = 2_000;
	/** Бревно (дуб/ель/берёза) в костёр: +8000 (НЕ уголь; прежнее значение сохранено). */
	public static final int ADD_CAMPFIRE_LOG = 8_000;

	// ------------------------------------------------------------------
	// Взаимодействие (refuel job, nearest, cooldown)
	// ------------------------------------------------------------------

	/** Максимальное расстояние «от глаз до центра блока» для refuel, в блоках. */
	public static final double REFUEL_MAX_DISTANCE = 4.0D;
	/** Радиус поиска ближайшего источника методом nearest, в блоках. */
	public static final double NEAREST_RADIUS = 8.0D;
	/** Длительность refuel/light job в тиках (20 тиков = 1 секунда). */
	public static final int REFUEL_JOB_TICKS = 20;
	/** Cooldown сообщения «Топливный запас заполнен» на игрока, тиков. */
	public static final int MESSAGE_COOLDOWN_TICKS = 40;
	/** Минимальный интервал между S2C-снимками ближайшего источника, тиков. */
	public static final int SNAPSHOT_INTERVAL_TICKS = 5;
	/** Точный текст отказа при переполнении (утверждён ТЗ). */
	public static final String MESSAGE_TANK_FULL = "Топливный запас заполнен";
	/** Текст подписи кнопки refuel (Work Panel). */
	public static final String LABEL_REFUEL = "Заправить";
	/** Текст подписи кнопки light (Work Panel). */
	public static final String LABEL_LIGHT = "Зажечь";
}
