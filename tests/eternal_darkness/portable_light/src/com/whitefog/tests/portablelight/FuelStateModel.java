package com.whitefog.tests.portablelight;

/**
 * Чистая (без Minecraft) модель НОВОЙ семантики топлива факела (этап 1.7, пользовательский тикет):
 * состояние предмета/блока как пара {@code (remaining, lit)}, повторная заправка углём на 20% ёмкости
 * БЕЗ изменения lit, персистентность {@code lit+remaining} при снятии блока, расход топлива горящим
 * предметом в инвентаре (не только в левой руке), переносной свет из левой руки и порча заряженного
 * стека {@code count>1} (без split/расхода).
 *
 * <p><b>Это logic-only</b> и НЕ runtime-proof: реальные {@code ItemStack}, item-компонент
 * {@code white_fog:light_fuel}, инвентарь, серверный tick и light engine не проверяются.</p>
 *
 * <p><b>Отличие от текущего main.</b> В sandbox-модели {@code PortableLightModel} (старая фаза)
 * уголь добавлял 100% ёмкости и зажигал источник. Новый тикет это отменяет: уголь = строго 20%
 * ёмкости, а {@code Заправить} НИКОГДА не меняет {@code lit}; единственное действие, включающее
 * источник, — {@code Зажечь} ({@code relight}), и оно не тратит топливо. Старые модели оставлены
 * без изменений (существующие проверки не ломаются), новая семантика живёт здесь.</p>
 *
 * <p>Таблица ёмкостей повторяет {@code LightConfig}; процент угля — из тикета. Палочка/бревно
 * костра НЕ являются углём и сохраняют прежние значения.</p>
 */
public final class FuelStateModel {
	private FuelStateModel() {
	}

	// ------------------------------------------------------------------
	// Ёмкости (совпадают с LightConfig)
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
	// Топливо
	// ------------------------------------------------------------------

	/** Уголь/древесный уголь = ровно 20% ёмкости (новый тикет). */
	public static final int COAL_PERCENT = 20;

	/** Добавка за уголь/древесный уголь для ёмкости: ровно 20% (целое, округление вниз). */
	public static int coalAddition(int capacity) {
		return Math.max(0, (int) ((long) capacity * COAL_PERCENT / 100L));
	}

	/** Палочка в костёр: прежнее значение СОХРАНЕНО (не процент). */
	public static final int ADD_CAMPFIRE_STICK = 2_000;
	/** Бревно в костёр: прежнее значение СОХРАНЕНО (не процент). */
	public static final int ADD_CAMPFIRE_LOG = 8_000;

	/** Итог заправки углём. */
	public enum RefuelStatus {
		/** Топливо принято: ровно один предмет списан, lit НЕ менялся. */
		OK,
		/** Переполнение: не списываем и не добавляем частично. */
		FULL,
		/** Нет топлива в руке. */
		NO_FUEL
	}

	/** Результат заправки: статус, новое состояние и был ли списан предмет. */
	public record RefuelResult(RefuelStatus status, FuelState state, boolean consumed) {
	}

	/**
	 * Состояние топлива предмета/блока. {@code lit} хранится отдельно от {@code remaining}, чтобы
	 * погашенный источник с запасом не зажигался ни заправкой, ни снятием блока.
	 */
	public record FuelState(int remaining, boolean lit) {
		/** Нормализация: остаток не бывает отрицательным. */
		public FuelState {
			if (remaining < 0) {
				remaining = 0;
			}
		}

		/** Пустое/погасшее состояние. */
		public static FuelState empty() {
			return new FuelState(0, false);
		}

		/** Горит и есть запас — только тогда предмет светит и расходует топливо. */
		public boolean burning() {
			return lit && remaining > 0;
		}

		/** Копия с остатком, зажатым по ёмкости. */
		public FuelState normalized(int capacity) {
			return new FuelState(Math.min(remaining, Math.max(0, capacity)), lit);
		}
	}

	// ------------------------------------------------------------------
	// Действия панели источника
	// ------------------------------------------------------------------

	/**
	 * Повторная заправка углём: добавляет {@code addition} (20% ёмкости) и НИКОГДА не меняет
	 * {@code lit}. Погашенный источник остаётся погашенным (зажигает только {@link #relight}).
	 * Переполнение → {@code FULL} без частичного списания; нет топлива → {@code NO_FUEL}.
	 */
	public static RefuelResult refuelPartial(FuelState s, int capacity, int addition, boolean fuelAvailable) {
		if (!fuelAvailable || addition <= 0) {
			return new RefuelResult(RefuelStatus.NO_FUEL, s, false);
		}
		if ((long) s.remaining() + (long) addition > (long) capacity) {
			return new RefuelResult(RefuelStatus.FULL, s, false);
		}
		return new RefuelResult(RefuelStatus.OK, new FuelState(s.remaining() + addition, s.lit()), true);
	}

	/** Потушить: сохраняет остаток, только {@code lit=false}. */
	public static FuelState extinguish(FuelState s) {
		return new FuelState(s.remaining(), false);
	}

	/** Зажечь: ЕДИНСТВЕННОЕ действие {@code unlit->lit}, без расхода топлива; требует остаток &gt; 0. */
	public static FuelState relight(FuelState s) {
		return s.remaining() > 0 ? new FuelState(s.remaining(), true) : s;
	}

	// ------------------------------------------------------------------
	// Персистентность предмет↔блок
	// ------------------------------------------------------------------

	/** Установка блока: блок наследует и остаток, и lit предмета. */
	public static FuelState placeBlock(FuelState itemFuel) {
		return new FuelState(itemFuel.remaining(), itemFuel.lit());
	}

	/** Снятие блока: выпавший предмет наследует и остаток, и lit блока. */
	public static FuelState dropItem(FuelState blockFuel) {
		return new FuelState(blockFuel.remaining(), blockFuel.lit());
	}

	// ------------------------------------------------------------------
	// Расход в инвентаре (count=1)
	// ------------------------------------------------------------------

	/**
	 * Один серверный тик предмета в инвентаре: тикается только горящий ({@code lit}) предмет с
	 * запасом, независимо от того, в какой руке/слоте он лежит. {@code 1 -> 0} сразу тушит.
	 * Погашенный/пустой не тратит топливо.
	 */
	public static FuelState tickInventory(FuelState s) {
		if (!s.burning()) {
			return s;
		}
		int next = s.remaining() - 1;
		return next <= 0 ? new FuelState(0, false) : new FuelState(next, true);
	}

	// ------------------------------------------------------------------
	// Порча стека / переносной свет
	// ------------------------------------------------------------------

	/** Заряженный стек обязан иметь {@code count=1}: {@code remaining>0 && count>1} = invalid. */
	public static boolean invalidChargedStack(int remaining, int count) {
		return remaining > 0 && count > 1;
	}

	/** Годится ли заряженный стек (count=1 и есть остаток). */
	public static boolean usableCharged(int remaining, int count) {
		return remaining > 0 && count == 1;
	}

	/**
	 * Эмиссия переносного света из ЛЕВОЙ руки: только горящий ({@code lit}) предмет с запасом и
	 * целым стеком ({@code count=1}). Пустой/погашенный/invalid не светит (0).
	 */
	public static int offhandEmission(int remaining, boolean lit, int count, int emission) {
		if (invalidChargedStack(remaining, count)) {
			return 0;
		}
		if (count != 1 || !lit || remaining <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(15, emission));
	}
}
