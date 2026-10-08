package com.whitefog.darkness.light;

/**
 * Чистая (без Minecraft) политика действий с ПЛАЦЕД-источником света (этап поверх 1.6) и коды
 * статусов ответа панели {@code white_fog:source_panel}.
 *
 * <p>Действия приходят серверу через ванильный menu-button packet ({@code clickMenuButton}) и
 * выполняются ТОЛЬКО на сервере после повторной валидации прав/дистанции/LOS/UUID/ревизии. Клиент
 * лишь отображает состояние и доступность кнопок; «ghost action» (локального предсказания) нет.</p>
 *
 * <p>Тушение/зажигание мгновенны и НЕ расходуют топливо (остаток сохраняется); заправка идёт
 * существующим refuel-job 20 тиков. {@code managedByPost} разрешает только inspect, действия
 * отклоняются.</p>
 */
public final class SourceActionPolicy {
	private SourceActionPolicy() {
	}

	// ------------------------------------------------------------------
	// Действия (button id меню)
	// ------------------------------------------------------------------

	/** Открытие панели (не кнопка; используется только как статус при первичном снимке). */
	public static final int ACTION_OPEN = 0;
	/** Заправить источник топливом из главной руки. */
	public static final int ACTION_REFUEL = 1;
	/** Потушить lit-источник, сохранив остаток. */
	public static final int ACTION_EXTINGUISH = 2;
	/** Зажечь unlit-источник с запасом ({@code remaining > 0}). */
	public static final int ACTION_RELIGHT = 3;

	// ------------------------------------------------------------------
	// Статусы ответа
	// ------------------------------------------------------------------

	/** Панель открыта/обновлена. */
	public static final int STATUS_OPEN_OK = 0;
	/** Действие выполнено. */
	public static final int STATUS_OK = 1;
	/** Заправка принята, идёт refuel-job 20 тиков. */
	public static final int STATUS_ACCEPTED = 10;
	/** Переполнение ёмкости (частичного списания нет). */
	public static final int STATUS_FULL = 2;
	/** Нет подходящего топлива и зажигать нечего. */
	public static final int STATUS_NO_FUEL = 3;
	/** Нет прав/вне дистанции/нет LOS. */
	public static final int STATUS_REFUSED = 4;
	/** Устаревшая ревизия/чужой UUID/источник заменён. */
	public static final int STATUS_STALE = 5;
	/** Источник управляется постом — действия запрещены. */
	public static final int STATUS_MANAGED_BY_POST = 6;
	/** Источник уже горит. */
	public static final int STATUS_ALREADY_LIT = 7;
	/** Источник уже погас. */
	public static final int STATUS_ALREADY_UNLIT = 8;
	/** Зажигание невозможно: остаток равен нулю. */
	public static final int STATUS_NO_FUEL_TO_LIGHT = 9;

	/** Можно ли применить топливо (добавка влезает в ёмкость). */
	public static boolean canRefuel(int remaining, int addition, int capacity) {
		return LightFuelPolicy.fits(remaining, addition, capacity);
	}

	/** Можно ли потушить. */
	public static boolean canExtinguish(boolean lit) {
		return lit;
	}

	/** Можно ли зажечь: не управляется постом, погашен и есть запас. */
	public static boolean canRelight(boolean lit, int remaining, boolean managedByPost) {
		return !managedByPost && !lit && remaining > 0;
	}

	/** Доступна ли кнопка действия (для UI; сервер всё равно перепроверяет). */
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
}
