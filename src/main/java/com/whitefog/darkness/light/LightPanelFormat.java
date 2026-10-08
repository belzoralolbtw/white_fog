package com.whitefog.darkness.light;

import com.whitefog.darkness.DarknessConfig;

import java.util.Locale;

/**
 * Чистое (без Minecraft) форматирование панели источников света и часов вечной ночи.
 *
 * <p>Используется и клиентским HUD (клавиша {@code G}), и экраном источника по ПКМ, и sandbox-тестом.
 * Длительности — в игровых тиках ({@code 20 тиков = 1 секунда}); часы вечной ночи — понятная метка
 * {@code Ночь · HH:MM} по мировому времени, БЕЗ вводящего в заблуждение ванильного «дня».</p>
 */
public final class LightPanelFormat {
	private LightPanelFormat() {
	}

	/**
	 * Остаток в тиках → компактная русская строка (секунды/минуты/часы). Округление вверх, чтобы
	 * «осталось 1 тик» показывалось как 1с, а не 0с. Ноль/негатив → «нет топлива».
	 */
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

	/**
	 * Понятная длительность словами (тикет поверх 1.7): {@code X сек}, {@code X мин Y сек},
	 * {@code X ч Y мин}. Ноль/негатив → «нет топлива». Совпадает с sandbox
	 * {@code PortableUiModel.durationWords}. НЕ содержит сырых тиков.
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

	/** Мировое время суток в формате {@code HH:MM} (Minecraft: 0 тиков = 06:00). */
	public static String timeHhMm(long clockTicks) {
		long day = DarknessConfig.TICKS_PER_DAY;
		long normalized = ((clockTicks % day) + day) % day;
		long shifted = (normalized + 6_000L) % day;
		int hours = (int) (shifted / 1_000L);
		int minutes = (int) ((shifted % 1_000L) * 60L / 1_000L);
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	/** Кастомная не вводящая в заблуждение подпись часов вечной ночи: {@code Ночь · HH:MM}. */
	public static String nightClockLabel(long clockTicks) {
		return "Ночь · " + timeHhMm(clockTicks);
	}

	/** Русское имя вида источника по ordinal {@link LightFuelPolicy.SourceKind}. */
	public static String sourceKindName(int kindOrdinal) {
		return switch (kindOrdinal) {
			case 0 -> "Факел";
			case 1 -> "Факел душ";
			case 2 -> "Фонарь";
			case 3 -> "Фонарь душ";
			case 4 -> "Костёр";
			default -> "Источник";
		};
	}

	/** Строка статуса источника: вид + «горит»/«погас» + пометка поста. */
	public static String sourceStatusLine(boolean present, int kindOrdinal, boolean lit, boolean managedByPost) {
		if (!present) {
			return "Источник: нет рядом";
		}
		String base = sourceKindName(kindOrdinal) + (lit ? " · горит" : " · погас");
		return managedByPost ? base + " · пост" : base;
	}

	/** Строка топлива с понятным временем до угасания; сырые тики НЕ показываются. */
	public static String fuelLine(boolean present, int remaining) {
		if (!present) {
			return "Осталось: —";
		}
		return "Осталось: " + durationWords(remaining);
	}

	/**
	 * Строка источника для HUD (клавиша {@code G}). Отображение отделено от действия: метод только
	 * строит текст, ничего не отправляет. Приоритет: установленный рядом источник → валидный
	 * ГОРЯЩИЙ переносной свет в левой руке (тогда это тоже источник, а не «нет рядом») → «нет рядом».
	 *
	 * <p>{@code portableActive} должен быть {@code true} только для валидного ({@code lit} +
	 * {@code remaining>0} + {@code count==1}) переносного света, иначе (пустой/погасший/испорченный
	 * offhand) при отсутствии блока возвращается «Источник: нет рядом».</p>
	 */
	public static String hudSourceLine(boolean placedPresent, int placedKindOrdinal, boolean placedLit,
			boolean portableActive, int portableKindOrdinal) {
		if (placedPresent) {
			String base = "Источник: " + sourceKindName(placedKindOrdinal)
					+ (placedLit ? " · горит" : " · погас");
			return base;
		}
		if (portableActive) {
			return "Источник: в руке — " + sourceKindName(portableKindOrdinal);
		}
		return "Источник: нет рядом";
	}

	/**
	 * Строка остатка для HUD: сначала остаток установленного рядом источника, иначе — остаток
	 * переносного света в левой руке, иначе «Осталось: —». Сырые тики не показываются.
	 */
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
	 * Строка «в левой руке» для HUD: только имя переносного света, без сырого уровня света.
	 * {@code "В руке: Факел"} / {@code "В руке: Факел душ"} / {@code "В руке: —"}.
	 */
	public static String heldLine(boolean present, String displayName) {
		if (!present || displayName == null || displayName.isEmpty()) {
			return "В руке: —";
		}
		return "В руке: " + displayName;
	}

	/** Подпись кнопки меню источника. */
	public static String buttonLabel(int buttonId) {
		return switch (buttonId) {
			case SourceActionPolicy.ACTION_REFUEL -> "Заправить";
			case SourceActionPolicy.ACTION_EXTINGUISH -> "Потушить";
			case SourceActionPolicy.ACTION_RELIGHT -> "Зажечь";
			default -> "Действие";
		};
	}

	/** Текст результата действия (краткая причина/подтверждение). */
	public static String statusMessage(int status) {
		return switch (status) {
			case SourceActionPolicy.STATUS_OPEN_OK -> "";
			case SourceActionPolicy.STATUS_OK -> "Готово";
			case SourceActionPolicy.STATUS_ACCEPTED -> "Заправка…";
			case SourceActionPolicy.STATUS_FULL -> LightConfig.MESSAGE_TANK_FULL;
			case SourceActionPolicy.STATUS_NO_FUEL -> "Нет топлива в руке";
			case SourceActionPolicy.STATUS_REFUSED -> "Отказано";
			case SourceActionPolicy.STATUS_STALE -> "Источник изменился";
			case SourceActionPolicy.STATUS_MANAGED_BY_POST -> "Управляется постом";
			case SourceActionPolicy.STATUS_ALREADY_LIT -> "Уже горит";
			case SourceActionPolicy.STATUS_ALREADY_UNLIT -> "Уже погас";
			case SourceActionPolicy.STATUS_NO_FUEL_TO_LIGHT -> "Нет топлива для зажигания";
			default -> "";
		};
	}
}
