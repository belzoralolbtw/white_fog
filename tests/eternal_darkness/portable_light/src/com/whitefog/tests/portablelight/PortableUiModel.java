package com.whitefog.tests.portablelight;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Чистая (без Minecraft) модель строк HUD для переносного света (этап 1.7, пользовательский тикет).
 *
 * <p>Требования тикета:</p>
 * <ul>
 *     <li>HUD показывает {@code В руке: Факел} / {@code В руке: Факел душ} — БЕЗ сырого уровня света
 *     {@code (свет 14)};</li>
 *     <li>остаток показывается понятно: {@code Осталось: X мин Y сек} — БЕЗ сырых тиков топлива.</li>
 * </ul>
 *
 * <p>Это logic-only: реальный рендер {@code GuiGraphicsExtractor}/шрифт не проверяется.</p>
 */
public final class PortableUiModel {
	private PortableUiModel() {
	}

	/** Строка отсутствия предмета в руке. */
	public static final String HELD_ABSENT = "В руке: —";
	/** Строка для обычного факела. */
	public static final String HELD_TORCH = "В руке: Факел";
	/** Строка для факела душ. */
	public static final String HELD_SOUL_TORCH = "В руке: Факел душ";

	/** Регулярка сырых тиков топлива: число + необязательный пробел + «т»/«t». */
	private static final Pattern RAW_FUEL_TICKS = Pattern.compile("\\d+\\s*[тtТT]");

	/**
	 * Строка «в руке» по русскому имени вида. Без уровня света: только имя факела.
	 * Отсутствие предмета → {@link #HELD_ABSENT}.
	 */
	public static String heldLine(String kindDisplayName, boolean present) {
		if (!present || kindDisplayName == null || kindDisplayName.isEmpty()) {
			return HELD_ABSENT;
		}
		return "В руке: " + kindDisplayName;
	}

	/** Строка остатка: понятное время до угасания, без сырых тиков. */
	public static String fuelLine(int remaining) {
		return "Осталось: " + durationWords(remaining);
	}

	/** Русское имя вида источника по ordinal (зеркало {@code LightPanelFormat.sourceKindName}). */
	public static String sourceKindName(int kindOrdinal) {
		return switch (kindOrdinal) {
			case 0 -> "Факел";
			case 1 -> "Факел душ";
			case 2 -> "Фонарь";
			case 3 -> "Фонарь душевный";
			case 4 -> "Костёр";
			default -> "Источник";
		};
	}

	/**
	 * Строка источника HUD (зеркало {@code LightPanelFormat.hudSourceLine}): поставленный блок в
	 * приоритете; иначе валидный горящий переносной свет = «в руке — <вид>»; иначе «нет рядом».
	 * Отображение отделено от действия.
	 */
	public static String hudSourceLine(boolean placedPresent, int placedKindOrdinal, boolean placedLit,
			boolean portableActive, int portableKindOrdinal) {
		if (placedPresent) {
			return "Источник: " + sourceKindName(placedKindOrdinal) + (placedLit ? " · горит" : " · погас");
		}
		if (portableActive) {
			return "Источник: в руке — " + sourceKindName(portableKindOrdinal);
		}
		return "Источник: нет рядом";
	}

	/** Строка остатка HUD: блок в приоритете, иначе переносной свет, иначе прочерк. */
	public static String hudFuelLine(boolean placedPresent, int placedRemaining,
			boolean portableActive, int portableRemaining) {
		if (placedPresent) {
			return "Осталось: " + durationWords(placedRemaining);
		}
		if (portableActive) {
			return "Осталось: " + durationWords(portableRemaining);
		}
		return "Осталось: —";
	}

	/**
	 * Длительность словами: {@code X сек}, {@code X мин Y сек}, {@code X ч Y мин}.
	 * Ноль/негатив → «нет топлива». Округление вверх (1 тик = 1 сек).
	 */
	public static String durationWords(int ticks) {
		if (ticks <= 0) {
			return "нет топлива";
		}
		long seconds = (ticks + 19L) / 20L;
		if (seconds < 60L) {
			return seconds + " сек";
		}
		long minutes = seconds / 60L;
		long remSeconds = seconds % 60L;
		if (minutes < 60L) {
			return minutes + " мин " + remSeconds + " сек";
		}
		long hours = minutes / 60L;
		long remMinutes = minutes % 60L;
		return hours + " ч " + remMinutes + " мин";
	}

	/** Содержит ли строка сырые тики топлива (недопустимо для HUD, например «12000т»). */
	public static boolean containsRawFuelTicks(String text) {
		return text != null && RAW_FUEL_TICKS.matcher(text).find();
	}

	/** Содержит ли строка сырой уровень света (недопустимо для HUD, например «свет 14»). */
	public static boolean containsLightLevel(String text) {
		return text != null && text.toLowerCase(Locale.ROOT).contains("свет ");
	}
}
