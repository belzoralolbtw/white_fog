package com.whitefog.client.hud;

/**
 * Чистая (без импортов Minecraft) политика выбора отображаемого источника света для компактного
 * статуса (багфикс поверх этапа 1.9).
 *
 * <p>Раньше {@link LightWidget} показывал только серверный «ближайший поставленный источник»
 * ({@code DarknessSnapshotPayload.sourceItemId}); валидный ГОРЯЩИЙ факел в левой руке в панели не
 * отражался, хотя рабочая панель {@code G} его уже показывала. Здесь собрана ровно детерминированная
 * приоритизация: <b>поставленный рядом источник (снимок сервера) побеждает</b>; если его нет —
 * отображается валидный горящий переносной свет из левой руки; если нет и его — источника нет.</p>
 *
 * <p>Валидность левой руки НЕ дублируется: вызывающая сторона передаёт уже готовый {@code boolean}
 * из production-семантики {@link com.whitefog.darkness.light.PortableLightPolicy#active} (вид
 * {@code PortableLightService.kindForStack}, компонент {@code LightFuelComponent}). Класс пригоден
 * для независимого sandbox-теста: методы не ссылаются на игровые классы.</p>
 */
public final class HudSourceDisplay {

	/** Откуда взят отображаемый источник. */
	public enum Origin {
		/** Источника нет. */
		NONE,
		/** Поставленный рядом источник из серверного снимка. */
		PLACED,
		/** Горящий переносной свет в левой руке (локальный клиент). */
		OFFHAND
	}

	/**
	 * Разрешённый источник для отображения. {@code kindOrdinal} — ordinal вида
	 * {@link com.whitefog.darkness.light.PortableLightPolicy.Kind} (для левой руки) либо ordinal
	 * {@code LightFuelPolicy.SourceKind} (для поставленного; сейчас не отображается по имени).
	 * {@code remainingTicks} — остаток топлива (-1, если источника нет).
	 */
	public record Resolved(Origin origin, int kindOrdinal, boolean lit, int remainingTicks) {
		/** Есть ли отображаемый источник. */
		public boolean present() {
			return this.origin != Origin.NONE;
		}

		/** Остаток в секундах округлением вверх ({@code ceil(ticks/20)}); 0 при отсутствии топлива. */
		public int fuelSeconds() {
			return DarkHudLayout.ceilSeconds(this.remainingTicks);
		}
	}

	/** Пустой результат (источника нет). */
	public static final Resolved NONE = new Resolved(Origin.NONE, -1, false, -1);

	private HudSourceDisplay() {
	}

	/**
	 * Детерминированно выбирает отображаемый источник.
	 *
	 * <p>Приоритет: поставленный источник ({@code placedPresent} и неотрицательный
	 * {@code placedRemaining}) → валидный горящий {@code offhandValid} с {@code offhandRemaining > 0} →
	 * ничего. Невалидный/пустой/погасший/испорченный offhand источника не создаёт.</p>
	 *
	 * @param placedPresent      есть ли поставленный рядом источник в серверном снимке
	 * @param placedKindOrdinal  ordinal вида поставленного источника
	 * @param placedLit          горит ли поставленный источник
	 * @param placedRemaining    остаток поставленного источника в тиках (-1, если нет)
	 * @param offhandValid       прошёл ли offhand production-проверку {@code PortableLightPolicy.active}
	 * @param offhandKindOrdinal ordinal вида переносного света (-1, если нет)
	 * @param offhandRemaining   остаток переносного света в тиках
	 */
	public static Resolved resolve(boolean placedPresent, int placedKindOrdinal, boolean placedLit,
			int placedRemaining, boolean offhandValid, int offhandKindOrdinal, int offhandRemaining) {
		if (placedPresent && placedRemaining >= 0) {
			return new Resolved(Origin.PLACED, placedKindOrdinal, placedLit, placedRemaining);
		}
		if (offhandValid && offhandRemaining > 0) {
			return new Resolved(Origin.OFFHAND, offhandKindOrdinal, true, offhandRemaining);
		}
		return NONE;
	}
}
