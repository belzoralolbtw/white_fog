package com.whitefog.client;

import com.whitefog.network.DarknessSnapshotPayload;

/**
 * Клиентский кэш последнего снимка тьмы (этап 1.5).
 *
 * <p>Это НЕ источник истины: клиент хранит только последний полученный снимок и его ревизию,
 * сервер остаётся авторитетным. Устаревшие снимки (меньшая ревизия) отбрасываются.
 * При выходе из мира кэш очищается (см. {@link WhiteFogClient}).</p>
 */
public final class ClientDarknessState {
	private volatile DarknessSnapshotPayload latest;

	/**
	 * Монотонная метка времени последнего ПРИНЯТОГО снимка (наносекунды {@link System#nanoTime()}).
	 * Нужна потому, что серверный heartbeat (каждые 100 тиков) повторяет снимок с ТОЙ ЖЕ
	 * {@code revision} — по одной ревизии нельзя понять «сервер жив/свеж». Свежесть окна
	 * (7 c при heartbeat 5 c) даёт {@link #secondsSinceUpdate()}.
	 */
	private volatile long lastUpdateNanos;

	/** Применяет снимок, если он не старше уже сохранённого. */
	public void update(DarknessSnapshotPayload payload) {
		DarknessSnapshotPayload current = this.latest;
		if (current != null && payload.revision() < current.revision()) {
			return;
		}
		this.latest = payload;
		this.lastUpdateNanos = System.nanoTime();
	}

	/** Возраст последнего принятого снимка в секундах ({@link Double#MAX_VALUE}, если данных нет). */
	public double secondsSinceUpdate() {
		if (this.latest == null) {
			return Double.MAX_VALUE;
		}
		return (System.nanoTime() - this.lastUpdateNanos) / 1_000_000_000.0;
	}

	/** Текущий exposure последнего снимка (0, если данных нет). */
	public int lightExposure() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? 0 : current.lightExposure();
	}

	/** Последний снимок или {@code null}. */
	public DarknessSnapshotPayload snapshot() {
		return this.latest;
	}

	/** Нужен ли сейчас штраф скорости (false, если данных ещё нет). */
	public boolean speedRestricted() {
		DarknessSnapshotPayload current = this.latest;
		return current != null && current.speedRestricted();
	}

	/** Последняя известная ревизия снимка (-1 — данных нет). */
	public long lastRevision() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? -1L : current.revision();
	}

	/** Есть ли данные от сервера. */
	public boolean hasData() {
		return this.latest != null;
	}

	/** Очищает кэш (при выходе из мира/отключении). */
	public void clear() {
		this.latest = null;
		this.lastUpdateNanos = 0L;
	}
}
