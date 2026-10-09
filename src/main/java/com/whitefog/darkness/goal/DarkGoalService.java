package com.whitefog.darkness.goal;

import java.util.List;

/**
 * Чистый (без Minecraft) сервис целей игрока (этап 1.9).
 *
 * <p>Цели — стабильный серверный контракт для будущих служб (обсерватория, четыре поста, маяк,
 * финальное испытание). Здесь только идентификаторы, порядок, монотонные флаги выполнения и
 * вычисление активной цели; никаких world structures и никакого доступа к игроку. Серверный
 * persistent-адаптер живёт в {@code com.whitefog.state.PlayerSurvivalState} (флаги переживают
 * save/load, unload/load чанка, disconnect/reconnect и копируются при смерти через
 * {@code copyOnDeath()}).</p>
 *
 * <h2>Семантика</h2>
 * <ul>
 *     <li>{@link #markCompleted(boolean[], String)} принимает только известные ID, монотонно ставит
 *         флаг (повтор безопасен, сброса нет). Неизвестный ID отклоняется без изменения состояния.</li>
 *     <li>Активная цель — ПЕРВАЯ невыполненная в {@link #ORDER}. Отметка более поздней цели раньше
 *         ранней допустима (флаг сохраняется), но активная цель при этом НЕ перескакивает через
 *         первую невыполненную. После всех — {@link #ID_COMPLETE}.</li>
 *     <li>Неизвестный сохранённый ID нормализуется в {@link #DEFAULT_GOAL} (find_observatory) без
 *         удаления остальных полей сохранения.</li>
 * </ul>
 *
 * <p>Ревизия: класс чистый и не зависит от версии Minecraft, поэтому тестируется напрямую sandbox-ом
 * {@code tests/eternal_darkness/hud}.</p>
 */
public final class DarkGoalService {
	/** Найти обсерваторию. */
	public static final String ID_FIND_OBSERVATORY = "white_fog:find_observatory";
	/** Активировать четыре фонарных поста. */
	public static final String ID_ACTIVATE_POSTS = "white_fog:activate_posts";
	/** Построить/связать главный маяк. */
	public static final String ID_BUILD_BEACON = "white_fog:build_beacon";
	/** Выдержать финальное испытание. */
	public static final String ID_ENDURE_DARKNESS = "white_fog:endure_darkness";
	/** Победа. */
	public static final String ID_COMPLETE = "white_fog:complete";

	/** Порядок целей: активная цель — первая невыполненная в этом списке. */
	public static final List<String> ORDER = List.of(
			ID_FIND_OBSERVATORY,
			ID_ACTIVATE_POSTS,
			ID_BUILD_BEACON,
			ID_ENDURE_DARKNESS,
			ID_COMPLETE);

	/** Цель по умолчанию (и fallback для неизвестного сохранённого ID). */
	public static final String DEFAULT_GOAL = ID_FIND_OBSERVATORY;

	// --- DTO-значения до появления поставщиков (никаких фиктивных структур мира) ---

	/** Число активных постов по умолчанию. */
	public static final int DEFAULT_POST_COUNT = 0;
	/** Финальное состояние по умолчанию: заблокировано. */
	public static final String FINAL_STATE_LOCKED = "LOCKED";
	/** Финальное состояние по умолчанию. */
	public static final String DEFAULT_FINAL_STATE = FINAL_STATE_LOCKED;
	/** Остаток финального испытания по умолчанию (тиков). */
	public static final long DEFAULT_FINAL_REMAINING_TICKS = 72_000L;
	/** Топливо финального испытания по умолчанию. */
	public static final long DEFAULT_FINAL_FUEL_TICKS = 0L;
	/** Остаток волны по умолчанию. */
	public static final long DEFAULT_WAVE_REMAINING_TICKS = 0L;

	private DarkGoalService() {
	}

	/** Количество известных целей. */
	public static int goalCount() {
		return ORDER.size();
	}

	/** Известен ли ID цели. */
	public static boolean isKnown(String id) {
		return id != null && ORDER.contains(id);
	}

	/** Индекс цели в порядке или {@code -1}. */
	public static int indexOf(String id) {
		return id == null ? -1 : ORDER.indexOf(id);
	}

	/** Короткое имя ID (без namespace) — для ключей NBT. */
	public static String shortName(String id) {
		if (id == null || id.isEmpty()) {
			return "";
		}
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	/** Ключ NBT для флага выполнения цели. */
	public static String nbtKey(String id) {
		return "goal_completed_" + shortName(id);
	}

	/** Все флаги выключены (отсутствующие флаги = false). */
	public static boolean[] defaultFlags() {
		return new boolean[ORDER.size()];
	}

	/** Выполнена ли цель. */
	public static boolean isCompleted(boolean[] flags, String id) {
		int index = indexOf(id);
		return index >= 0 && flags != null && index < flags.length && flags[index];
	}

	/**
	 * Монотонно отмечает цель выполненной. Возвращает {@code true}, только если состояние изменилось.
	 * Неизвестный ID (или {@code flags == null}) отклоняется без изменения.
	 */
	public static boolean markCompleted(boolean[] flags, String id) {
		int index = indexOf(id);
		if (index < 0 || flags == null || index >= flags.length || flags[index]) {
			return false;
		}
		flags[index] = true;
		return true;
	}

	/** Первая невыполненная цель в порядке (или {@link #ID_COMPLETE}, если выполнены все). */
	public static String firstIncomplete(boolean[] flags) {
		for (int i = 0; i < ORDER.size(); i++) {
			if (flags == null || i >= flags.length || !flags[i]) {
				return ORDER.get(i);
			}
		}
		return ID_COMPLETE;
	}

	/** Число выполненных целей. */
	public static int completedCount(boolean[] flags) {
		int count = 0;
		for (int i = 0; i < ORDER.size(); i++) {
			if (flags != null && i < flags.length && flags[i]) {
				count++;
			}
		}
		return count;
	}

	/** Неизвестный/пустой сохранённый ID приводится к {@link #DEFAULT_GOAL}. */
	public static String normalizeGoalId(String raw) {
		return isKnown(raw) ? raw : DEFAULT_GOAL;
	}
}
