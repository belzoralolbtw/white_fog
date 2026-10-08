package com.whitefog.client;

import com.whitefog.network.LightRefuelResultPayload;
import com.whitefog.network.LightSourceSnapshotPayload;
import com.whitefog.network.SourcePanelPayload;

/**
 * Клиентский кэш снимка ближайшего источника, последнего результата операции и состояния открытой
 * панели источника (этап 1.6 + панель поверх 1.6).
 *
 * <p>Не источник истины: сервер авторитетен. Хранит последний снимок (для Work Panel), последний
 * ответ refuel (для отображения причины отказа) и последний {@link SourcePanelPayload} (для экрана
 * источника). Очищается при выходе из мира.</p>
 */
public final class ClientLightState {
	private volatile LightSourceSnapshotPayload latestSnapshot;
	private volatile LightRefuelResultPayload lastResult;
	private volatile SourcePanelPayload latestPanel;

	/** Применяет снимок ближайшего источника. */
	public void updateSnapshot(LightSourceSnapshotPayload payload) {
		this.latestSnapshot = payload;
	}

	/** Применяет результат refuel. */
	public void updateResult(LightRefuelResultPayload payload) {
		this.lastResult = payload;
	}

	/** Применяет состояние панели источника (экран по ПКМ). */
	public void updatePanel(SourcePanelPayload payload) {
		this.latestPanel = payload;
	}

	/** Последний снимок или {@code null}. */
	public LightSourceSnapshotPayload snapshot() {
		return this.latestSnapshot;
	}

	/** Последний результат refuel или {@code null}. */
	public LightRefuelResultPayload lastResult() {
		return this.lastResult;
	}

	/** Последнее состояние панели источника или {@code null}. */
	public SourcePanelPayload panel() {
		return this.latestPanel;
	}

	/** Очищает кэш (при выходе из мира). */
	public void clear() {
		this.latestSnapshot = null;
		this.lastResult = null;
		this.latestPanel = null;
	}
}
