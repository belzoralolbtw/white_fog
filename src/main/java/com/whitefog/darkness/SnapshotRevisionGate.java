package com.whitefog.darkness;

/**
 * Чистый (без Minecraft) гейт порядка ревизий снимка (этап 1.9).
 *
 * <p>Клиент принимает снимок, только если его ревизия не меньше последней принятой. Одинаковая
 * ревизия разрешена как heartbeat сервера (сервер повторяет тот же снимок, чтобы подтвердить
 * «я жив/свеж»); меньшая ревизия отбрасывается как устаревшая. {@link #reset()} вызывается при
 * disconnect: после этого принимается любая новая ревизия (например, новая сессия начинает с 1).</p>
 *
 * <p>Класс не зависит от версии Minecraft и тестируется sandbox-ом
 * {@code tests/eternal_darkness/hud}.</p>
 */
public final class SnapshotRevisionGate {
	/** Нет принятых данных. */
	public static final long UNSET = Long.MIN_VALUE;

	private long lastRevision = UNSET;

	/** Принять ли снимок с этой ревизией; обновляет внутреннее состояние при принятии. */
	public boolean shouldAccept(long revision) {
		if (this.lastRevision == UNSET) {
			this.lastRevision = revision;
			return true;
		}
		if (revision < this.lastRevision) {
			return false;
		}
		this.lastRevision = revision;
		return true;
	}

	/** Последняя принятая ревизия или {@link #UNSET}. */
	public long lastRevision() {
		return this.lastRevision;
	}

	/** Есть ли принятые данные. */
	public boolean hasData() {
		return this.lastRevision != UNSET;
	}

	/** Сброс (disconnect): следующая любая ревизия принимается. */
	public void reset() {
		this.lastRevision = UNSET;
	}
}
