package com.whitefog.tests.portablelight;

/**
 * Чистая (без Minecraft) модель действий с ПЛАЦЕД-источником света для sandbox-теста
 * (аналог {@code com.whitefog.darkness.light.SourceActionPolicy} и статусов
 * {@code white_fog:source_panel}).
 *
 * <p>Формализует: доступность кнопок {@code Заправить}/{@code Потушить}/{@code Зажечь}, статусы
 * ответа панели и правила тушения/зажигания/заправки, включая {@code managedByPost} (inspect
 * разрешён, действия запрещены). Это НЕ runtime-proof.</p>
 */
public final class SourceActionModel {
	private SourceActionModel() {
	}

	/** Действия (button id меню). */
	public static final int ACTION_OPEN = 0;
	public static final int ACTION_REFUEL = 1;
	public static final int ACTION_EXTINGUISH = 2;
	public static final int ACTION_RELIGHT = 3;

	/** Статусы ответа панели. */
	public static final int STATUS_OPEN_OK = 0;
	public static final int STATUS_OK = 1;
	public static final int STATUS_ACCEPTED = 10;
	public static final int STATUS_FULL = 2;
	public static final int STATUS_NO_FUEL = 3;
	public static final int STATUS_REFUSED = 4;
	public static final int STATUS_STALE = 5;
	public static final int STATUS_MANAGED_BY_POST = 6;
	public static final int STATUS_ALREADY_LIT = 7;
	public static final int STATUS_ALREADY_UNLIT = 8;
	public static final int STATUS_NO_FUEL_TO_LIGHT = 9;

	/** Состояние источника: lit + остаток. */
	public record Src(boolean lit, int remaining) {
	}

	/** Тушение сохраняет остаток. */
	public static Src extinguish(Src s) {
		return new Src(false, s.remaining());
	}

	/** Зажигание сохраняет остаток. */
	public static Src relight(Src s) {
		return new Src(true, s.remaining());
	}

	/** Заправка: добавка влезает в ёмкость. */
	public static boolean canRefuel(int remaining, int addition, int capacity) {
		return addition > 0 && (long) remaining + (long) addition <= (long) capacity;
	}

	/** Тушение возможно только у lit. */
	public static boolean canExtinguish(boolean lit) {
		return lit;
	}

	/** Зажигание возможно у unlit с запасом и не managedByPost. */
	public static boolean canRelight(boolean lit, int remaining, boolean managedByPost) {
		return !managedByPost && !lit && remaining > 0;
	}

	/** Доступность кнопки действия (UI). */
	public static boolean buttonEnabled(int action, boolean lit, int remaining, int capacity,
			boolean managedByPost, boolean fuelAvailable, int addition) {
		if (managedByPost) {
			return false;
		}
		return switch (action) {
			case ACTION_REFUEL -> fuelAvailable && addition > 0 && remaining < capacity;
			case ACTION_EXTINGUISH -> lit;
			case ACTION_RELIGHT -> !lit && remaining > 0;
			default -> false;
		};
	}

	/** Статус ответа сервера на действие. */
	public static int outcome(int action, boolean lit, int remaining, int capacity, boolean managedByPost,
			boolean fuelAvailable, int addition) {
		if (managedByPost) {
			return STATUS_MANAGED_BY_POST;
		}
		return switch (action) {
			case ACTION_REFUEL -> {
				if (addition > 0 && fuelAvailable) {
					yield remaining + addition > capacity ? STATUS_FULL : STATUS_ACCEPTED;
				}
				yield (!lit && remaining > 0) ? STATUS_OK : STATUS_NO_FUEL;
			}
			case ACTION_EXTINGUISH -> lit ? STATUS_OK : STATUS_ALREADY_UNLIT;
			case ACTION_RELIGHT -> {
				if (lit) {
					yield STATUS_ALREADY_LIT;
				}
				yield remaining <= 0 ? STATUS_NO_FUEL_TO_LIGHT : STATUS_OK;
			}
			default -> STATUS_REFUSED;
		};
	}
}
