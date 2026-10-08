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

	/** Применяет снимок, если он не старше уже сохранённого. */
	public void update(DarknessSnapshotPayload payload) {
		DarknessSnapshotPayload current = this.latest;
		if (current != null && payload.revision() < current.revision()) {
			return;
		}
		this.latest = payload;
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
	}
}
