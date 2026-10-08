package com.whitefog.tests.portablelight;

import java.util.Locale;

/**
 * Чистая (без Minecraft) логическая модель переносного света факела в левой руке
 * для sandbox-теста (новый этап поверх незакоммиченного Stage 1.6).
 *
 * <p>Формализует предлагаемые правила будущего {@code PortableLightPolicy}/{@code PortableLightService}:
 * сопоставление offhand-предмета, семантику item-компонента {@code white_fog:light_fuel},
 * правила порчи стека (count/компонент), активный свет и его вклад в block light для exposure
 * <b>как отдельный адаптер</b> (формула exposure НЕ меняется), серверный countdown, ручное
 * тушение/зажигание, правила заправки, форматирование длительности и кастомную подпись часов
 * вечной ночи. Это НЕ runtime-proof.</p>
 *
 * <p>Значения ёмкостей/эмиссии согласованы с уже существующими
 * {@code LightFuelPolicy}/{@code LightSourceBlocks} (Stage 1.6): факел — эмиссия 14, ёмкость 12000,
 * добавка угля 12000; факел душ — эмиссия 10, ёмкость 8000, добавка угля 8000. Пороги exposure —
 * {@code DarknessConfig} (bright≥9, neutral 5..8, dark≤4).</p>
 */
public final class PortableLightModel {
	private PortableLightModel() {
	}

	/**
	 * Вид переносного света. Настенные варианты (wall_torch/soul_wall_torch) делят вид со стоячими.
	 * В vanilla `wall_torch` не существует как предмет, но id сохранён как алиас ради симметрии
	 * block-id и будущего маппинга.
	 */
	public enum Kind {
		/** Обычный факел: эмиссия 14, ёмкость 12000, уголь +12000. */
		TORCH("minecraft:torch", "Факел", 14, 12_000, 12_000),
		/** Факел душ: эмиссия 10, ёмкость 8000, уголь +8000. */
		SOUL_TORCH("minecraft:soul_torch", "Факел душ", 10, 8_000, 8_000);

		public final String itemId;
		public final String displayName;
		public final int emission;
		public final int capacity;
		public final int coalAddition;

		Kind(String itemId, String displayName, int emission, int capacity, int coalAddition) {
			this.itemId = itemId;
			this.displayName = displayName;
			this.emission = emission;
			this.capacity = capacity;
			this.coalAddition = coalAddition;
		}
	}

	/** Порог block light, с которого свет «яркий» (совпадает с {@code DarknessConfig}). */
	public static final int LIGHT_BRIGHT_THRESHOLD = 9;
	/** Нижняя граница «промежуточного» света (совпадает с {@code DarknessConfig}). */
	public static final int LIGHT_NEUTRAL_MIN = 5;
	/** Дельта exposure в ярком свете. */
	public static final int EXPOSURE_DELTA_BRIGHT = -2;
	/** Дельта exposure в тёмном свете (под открытым небом ещё +1). */
	public static final int EXPOSURE_DELTA_DARK = 1;
	/** Дополнительная дельта в тёмном свете под открытым небом. */
	public static final int EXPOSURE_DELTA_OPEN_SKY_EXTRA = 1;

	// ------------------------------------------------------------------
	// Идентификация offhand-предмета
	// ------------------------------------------------------------------

	/**
	 * Вид переносного света по точному item id левой руки или {@code null}, если предмет не
	 * поддерживается. Принимаются только факелы (стоячие/настенные, обычные/душ); фонари и костры
	 * НЕ являются переносным светом этого этапа.
	 */
	public static Kind kindForItem(String itemId) {
		if (itemId == null) {
			return null;
		}
		String id = itemId.trim().toLowerCase(Locale.ROOT);
		return switch (id) {
			case "minecraft:torch", "minecraft:wall_torch" -> Kind.TORCH;
			case "minecraft:soul_torch", "minecraft:soul_wall_torch" -> Kind.SOUL_TORCH;
			default -> null;
		};
	}

	/** Является ли item id переносным факелом. */
	public static boolean isPortableItem(String itemId) {
		return kindForItem(itemId) != null;
	}

	// ------------------------------------------------------------------
	// Компонент / порча стека
	// ------------------------------------------------------------------

	/** Нормализация значения компонента: null/негатив → 0; больше ёмкости → clamp по ёмкости. */
	public static int normalize(Integer component, int capacity) {
		if (component == null || component < 0) {
			return 0;
		}
		return Math.min(component, Math.max(0, capacity));
	}

	/** Порча: заряженный компонент ({@code >0}) при count{@code >1} запрещён (без split/расхода). */
	public static boolean isCorrupt(Integer component, int count) {
		return component != null && component > 0 && count > 1;
	}

	// ------------------------------------------------------------------
	// Состояние переносного света
	// ------------------------------------------------------------------

	/**
	 * Состояние переносного света игрока.
	 *
	 * @param present   в левой руке поддерживаемый факел;
	 * @param kind      вид факела (или null);
	 * @param remaining остаток топлива в тиках (0..capacity);
	 * @param lit       горит ли (погашенное состояние сохраняется отдельно от остатка);
	 * @param corrupt   стек повреждён (заряженный count&gt;1) — свет не даёт, операции запрещены.
	 */
	public record State(boolean present, Kind kind, int remaining, boolean lit, boolean corrupt) {
		/** Пустая левая рука / неподдерживаемый предмет. */
		public static State absent() {
			return new State(false, null, 0, false, false);
		}
	}

	/** Читает состояние из содержимого левой руки: item id + значение компонента + count. */
	public static State readOffhand(String itemId, Integer component, int count) {
		Kind kind = kindForItem(itemId);
		if (kind == null) {
			return State.absent();
		}
		boolean corrupt = isCorrupt(component, count);
		int remaining = normalize(component, kind.capacity);
		// Заряженный факел в левой руке по умолчанию горит; повреждённый — не горит.
		boolean lit = !corrupt && remaining > 0;
		return new State(true, kind, remaining, lit, corrupt);
	}

	/** Даёт ли состояние реальный переносной свет прямо сейчас. */
	public static boolean activeLight(State s) {
		return s.present() && !s.corrupt() && s.lit() && s.remaining() > 0;
	}

	/** Эмиссия переносного света (0..15): 0, если свет неактивен. */
	public static int emission(State s) {
		return activeLight(s) ? s.kind().emission : 0;
	}

	// ------------------------------------------------------------------
	// Адаптер вклада в block light (формула exposure НЕ меняется)
	// ------------------------------------------------------------------

	/**
	 * Явный адаптер входа exposure: фактический block light клетки глаза заменяется на
	 * {@code max(vanilla, emission)}. Это НЕ радиус и НЕ сложение (никакого «светового радиуса,
	 * умножающего реальный block light»), только поднятие уровня до эмиссии факела.
	 */
	public static int effectiveBlockLight(int vanillaBlockLight, State s) {
		int vanilla = Math.max(0, Math.min(15, vanillaBlockLight));
		int emission = emission(s);
		return Math.max(vanilla, emission);
	}

	/** Дельта exposure за sample по неизменной формуле {@code DarknessConfig} (block light 0..15). */
	public static int exposureDelta(int blockLight, boolean canSeeSky) {
		if (blockLight >= LIGHT_BRIGHT_THRESHOLD) {
			return EXPOSURE_DELTA_BRIGHT;
		}
		if (blockLight >= LIGHT_NEUTRAL_MIN) {
			return 0;
		}
		int delta = EXPOSURE_DELTA_DARK;
		if (canSeeSky) {
			delta += EXPOSURE_DELTA_OPEN_SKY_EXTRA;
		}
		return delta;
	}

	// ------------------------------------------------------------------
	// Серверный countdown
	// ------------------------------------------------------------------

	/** Один серверный тик: тикается только горящий (lit) факел с запасом; 1→0 сразу тушит. */
	public static State tick(State s) {
		if (!s.present() || s.corrupt() || !s.lit() || s.remaining() <= 0) {
			return s; // погашенный/повреждённый/пустой не тратит топливо (unlit fuel pause)
		}
		int next = s.remaining() - 1;
		if (next <= 0) {
			return new State(true, s.kind(), 0, false, false);
		}
		return new State(true, s.kind(), next, true, false);
	}

	// ------------------------------------------------------------------
	// Тушение / зажигание
	// ------------------------------------------------------------------

	/** Потушить: сохраняет остаток, только выставляет lit=false. */
	public static State extinguish(State s) {
		if (!s.present() || s.corrupt() || !s.lit()) {
			return s;
		}
		return new State(true, s.kind(), s.remaining(), false, false);
	}

	/** Зажечь: сохраняет остаток, lit=true; при остатке 0 не зажигается. */
	public static State relight(State s) {
		if (!s.present() || s.corrupt() || s.lit() || s.remaining() <= 0) {
			return s;
		}
		return new State(true, s.kind(), s.remaining(), true, false);
	}

	// ------------------------------------------------------------------
	// Заправка
	// ------------------------------------------------------------------

	/** Итог заправки. */
	public enum RefuelStatus {
		/** Успешно, ровно один предмет списан. */
		OK,
		/** Переполнение: не списываем и не добавляем частично. */
		FULL,
		/** Погашенный источник: авто-заправка запрещена (нужна явная заправка). */
		REFUSED_EXTINGUISHED,
		/** Стек повреждён. */
		REFUSED_CORRUPT,
		/** Нет переносного факела / нет топлива в руке. */
		NO_FUEL
	}

	/** Результат заправки: статус, новое состояние и был ли реально списан предмет. */
	public record RefuelResult(RefuelStatus status, State state, boolean consumed) {
	}

	/**
	 * Заправка факела углём.
	 *
	 * @param fuelAvailable уголь/древесный уголь есть в руке;
	 * @param explicit      {@code true} — явная заправка кнопкой; {@code false} — авто/фоновая.
	 *                      Погашенный факел НЕ расходует топливо при {@code explicit=false}
	 *                      (unlit fuel pause), но явная заправка разрешена и зажигает его.
	 */
	public static RefuelResult refuel(State s, boolean fuelAvailable, boolean explicit) {
		if (!s.present()) {
			return new RefuelResult(RefuelStatus.NO_FUEL, s, false);
		}
		if (s.corrupt()) {
			return new RefuelResult(RefuelStatus.REFUSED_CORRUPT, s, false);
		}
		if (!fuelAvailable) {
			return new RefuelResult(RefuelStatus.NO_FUEL, s, false);
		}
		if (!s.lit() && !explicit) {
			return new RefuelResult(RefuelStatus.REFUSED_EXTINGUISHED, s, false);
		}
		int addition = s.kind().coalAddition;
		if (!fits(s.remaining(), addition, s.kind().capacity)) {
			return new RefuelResult(RefuelStatus.FULL, s, false);
		}
		State next = new State(true, s.kind(), s.remaining() + addition, true, false);
		return new RefuelResult(RefuelStatus.OK, next, true);
	}

	/** «Остаток + добавка помещается в ёмкость». */
	public static boolean fits(int remaining, int addition, int capacity) {
		return addition > 0 && (long) remaining + (long) addition <= (long) capacity;
	}

	// ------------------------------------------------------------------
	// Кнопки меню
	// ------------------------------------------------------------------

	/** Кнопки меню источника. */
	public enum Button {
		ZAPRAVIT, POTUSHIT, ZAZHECH
	}

	/** Подписи кнопок (русские). */
	public static final String LABEL_ZAPRAVIT = "Заправить";
	public static final String LABEL_POTUSHIT = "Потушить";
	public static final String LABEL_ZAZHECH = "Зажечь";

	/** Подпись кнопки. */
	public static String buttonLabel(Button button) {
		return switch (button) {
			case ZAPRAVIT -> LABEL_ZAPRAVIT;
			case POTUSHIT -> LABEL_POTUSHIT;
			case ZAZHECH -> LABEL_ZAZHECH;
		};
	}

	/** Доступна ли кнопка для текущего состояния/наличия топлива. */
	public static boolean buttonEnabled(Button button, State s, boolean fuelAvailable) {
		if (!s.present() || s.corrupt()) {
			return false;
		}
		return switch (button) {
			case ZAPRAVIT -> fuelAvailable && s.remaining() < s.kind().capacity;
			case POTUSHIT -> s.lit();
			case ZAZHECH -> !s.lit() && s.remaining() > 0;
		};
	}

	// ------------------------------------------------------------------
	// Форматирование
	// ------------------------------------------------------------------

	/** Остаток в тиках → компактная русская строка (секунды/минуты/часы). */
	public static String formatDuration(int ticks) {
		if (ticks <= 0) {
			return "нет топлива";
		}
		long seconds = (ticks + 19L) / 20L;
		if (seconds < 60L) {
			return seconds + "с";
		}
		long minutes = seconds / 60L;
		long remSeconds = seconds % 60L;
		if (minutes < 60L) {
			return minutes + "м " + remSeconds + "с";
		}
		long hours = minutes / 60L;
		long remMinutes = minutes % 60L;
		return hours + "ч " + remMinutes + "м";
	}

	/** Кастомная подпись часов вечной ночи (не вводит в заблуждение ванильным «днём»). */
	public static final String NIGHT_LABEL = "Ночь";

	/** Мировое время суток в формате {@code HH:MM} (Minecraft: 0 тиков = 06:00). */
	public static String timeHhMm(long totalTicks) {
		long day = 24_000L;
		long normalized = ((totalTicks % day) + day) % day;
		long shifted = (normalized + 6_000L) % day;
		int hours = (int) (shifted / 1_000L);
		int minutes = (int) ((shifted % 1_000L) * 60L / 1_000L);
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	/** Подпись часов: всегда «Ночь · HH:MM», без ванильного счётчика дня. */
	public static String nightClockLabel(long totalTicks) {
		return NIGHT_LABEL + " · " + timeHhMm(totalTicks);
	}

	/** Эвристика «подпись вводит в заблуждение ванильным днём» — для контраста в тестах. */
	public static boolean isDayNightMisleading(String label) {
		String lower = label == null ? "" : label.toLowerCase(Locale.ROOT);
		return lower.contains("день") || lower.contains("day");
	}
}
