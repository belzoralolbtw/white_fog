package com.whitefog.darkness.light;

import java.util.Locale;

/**
 * Чистая (без импортов Minecraft) политика переносного света факела в левой руке.
 *
 * <p>Переносной свет даёт только ГОРЯЩИЙ ({@code lit}) факел/факел душ в ЛЕВОЙ руке игрока:
 * item-компонент {@code white_fog:light_fuel} со значением {@code remainingTicks > 0} И
 * {@code lit = true} (тикет поверх 1.7). Нет компонента, 0, отрицательное значение или
 * {@code lit = false} — света нет; заряженный стек с {@code count > 1} считается повреждённым
 * (свет не даёт, операции запрещены, split не выполняется).</p>
 *
 * <p>Класс пригоден для независимого sandbox-теста: методы не ссылаются на игровые классы.
 * Связка с реальным {@code ItemStack}/инвентарём левой руки находится в
 * {@link PortableLightService} (сервер) и в client-адаптере динамического света.</p>
 */
public final class PortableLightPolicy {
	private PortableLightPolicy() {
	}

	/**
	 * Вид переносного света. Настенные варианты (wall_torch/soul_wall_torch) делят вид со стоячими
	 * (как item их нет, но id сохранён алиасом для симметрии block-id).
	 */
	public enum Kind {
		/** Обычный факел: эмиссия 14, ёмкость 12000. */
		TORCH("minecraft:torch", "Факел", 14, LightConfig.CAPACITY_TORCH),
		/** Факел душ: эмиссия 10, ёмкость 8000. */
		SOUL_TORCH("minecraft:soul_torch", "Факел душ", 10, LightConfig.CAPACITY_SOUL_TORCH);

		/** Точный item id в обычном виде (для справки/логов). */
		public final String itemId;
		/** Русское отображаемое имя. */
		public final String displayName;
		/** Эмиссия block light (0..15). */
		public final int emission;
		/** Ёмкость компонента (clamp значения). */
		public final int capacity;

		Kind(String itemId, String displayName, int emission, int capacity) {
			this.itemId = itemId;
			this.displayName = displayName;
			this.emission = emission;
			this.capacity = capacity;
		}
	}

	/**
	 * Вид переносного света по точному item id или {@code null}, если предмет не поддерживается.
	 * Принимаются только факелы (стоячие/настенные, обычные/душ); фонари и костры переносным
	 * светом этого этапа НЕ являются.
	 */
	public static Kind kindForItemId(String itemId) {
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

	/** Нормализация значения компонента: {@code null}/негатив → 0; больше ёмкости → clamp. */
	public static int normalize(Integer component, int capacity) {
		if (component == null || component < 0) {
			return 0;
		}
		return Math.min(component, Math.max(0, capacity));
	}

	/** Порча: заряженный компонент ({@code > 0}) при {@code count > 1} запрещён (без split/расхода). */
	public static boolean isCorrupt(Integer component, int count) {
		return component != null && component > 0 && count > 1;
	}

	/** Активен ли переносной свет: предмет есть, стек цел, {@code lit} и остаток положителен. */
	public static boolean active(boolean present, boolean corrupt, boolean lit, int remaining) {
		return present && !corrupt && lit && remaining > 0;
	}

	/** Эмиссия переносного света (0..15); 0, если свет неактивен или вид неизвестен. */
	public static int emission(Kind kind, boolean present, boolean corrupt, boolean lit, int remaining) {
		if (kind == null || !active(present, corrupt, lit, remaining)) {
			return 0;
		}
		return kind.emission;
	}

	/**
	 * Явный адаптер входа exposure: фактический block light клетки глаза заменяется на
	 * {@code max(vanilla, emission)}. Это НЕ радиус и НЕ сложение — только поднятие уровня
	 * до эмиссии факела; формула exposure ({@link com.whitefog.darkness.LightExposurePolicy})
	 * не меняется.
	 */
	public static int effectiveBlockLight(int vanillaBlockLight, int emission) {
		int vanilla = Math.max(0, Math.min(15, vanillaBlockLight));
		int portable = Math.max(0, Math.min(15, emission));
		return Math.max(vanilla, portable);
	}

	// ------------------------------------------------------------------
	// Пространственное затухание переносного света (чистая логика)
	// ------------------------------------------------------------------
	//
	// Тот же расчёт использует клиентский рендер (PortableLightClient): block light в клетке
	// получает динамический вклад emission - ceil(расстояние от глаз), но не выше 15 и не ниже 0.
	// Помещение в чистую политику позволяет проверить затухание sandbox-тестом без Minecraft.

	/** Затухание переносного света: 1 уровень на блок, {@code ceil(distance)}. */
	public static int attenuation(double distance) {
		if (!Double.isFinite(distance) || distance <= 0.0D) {
			return 0;
		}
		return (int) Math.ceil(distance);
	}

	/**
	 * Итоговый динамический уровень освещённости от переносного источника в точке на расстоянии
	 * {@code distance}: {@code emission - attenuation}, зажатый в {@code 0..15}. 0 = вклада нет.
	 */
	public static int dynamicLevel(int emission, double distance) {
		int capped = Math.max(0, Math.min(15, emission));
		int level = capped - attenuation(distance);
		return level <= 0 ? 0 : Math.min(15, level);
	}

	/**
	 * Сколько секций (16 блоков) вокруг источника нужно пометить на перестройку меша: радиус
	 * {@code ceil(emission / 16)}. Для факела (14) это 1 секция, то есть область ±1 секция вокруг
	 * игрока покрывает весь световой конус 14 блоков; для emission 0 — 0.
	 */
	public static int sectionRadius(int emission) {
		int capped = Math.max(0, Math.min(15, emission));
		return capped <= 0 ? 0 : (capped + 15) / 16;
	}

	/**
	 * Итоговый block light для first-person/hand-пути: {@code max(ванильный block light,
	 * динамический уровень переносного света на этом расстоянии)}. Ровно то, что делает повышение
	 * светового аргумента {@code ItemInHandRenderer} (тикет: предмет в руке от первого лица должен
	 * быть освещён своим же факелом). Никогда не опускает ванильное значение и не даёт fullbright —
	 * только уровень эмиссии факела с затуханием.
	 */
	public static int raisedBlockLight(int vanillaBlockLight, int emission, double distance) {
		int vanilla = Math.max(0, Math.min(15, vanillaBlockLight));
		return Math.max(vanilla, dynamicLevel(emission, distance));
	}
}
