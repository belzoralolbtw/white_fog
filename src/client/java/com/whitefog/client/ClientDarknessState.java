package com.whitefog.client;

import com.whitefog.darkness.DarknessConfig;
import com.whitefog.darkness.SnapshotRevisionGate;
import com.whitefog.network.DarknessSnapshotPayload;

/**
 * Клиентский кэш последнего снимка света/целей (этапы 1.5 и 1.9).
 *
 * <p>Это НЕ источник истины: клиент хранит только последний принятый снимок и его ревизию, сервер
 * остаётся авторитетным. Порядок ревизий обеспечивает чистый {@link SnapshotRevisionGate}: снимок с
 * меньшей ревизией отбрасывается, одинаковая ревизия принимается как heartbeat. При выходе из мира
 * кэш очищается ({@link WhiteFogClient}), и после этого принимается любая новая ревизия.</p>
 *
 * <p>Скорость/штраф спринта не приходят отдельным полем: клиент выводит их из {@code exposure} ровно
 * по серверному порогу {@link DarknessConfig#SPEED_RESTRICT_EXPOSURE_THRESHOLD}.</p>
 */
public final class ClientDarknessState {
	private final SnapshotRevisionGate gate = new SnapshotRevisionGate();
	private volatile DarknessSnapshotPayload latest;

	/**
	 * Монотонная метка времени последнего ПРИНЯТОГО снимка (наносекунды {@link System#nanoTime()}).
	 * Нужна потому, что серверный heartbeat (каждые 100 тиков) повторяет снимок с ТОЙ ЖЕ
	 * {@code revision} — по одной ревизии нельзя понять «сервер жив/свеж». Свежесть окна
	 * (7 c при heartbeat 5 c) даёт {@link #secondsSinceUpdate()}.
	 */
	private volatile long lastUpdateNanos;

	/** Применяет снимок, если его ревизия не старше последней принятой. */
	public void update(DarknessSnapshotPayload payload) {
		if (payload == null || !this.gate.shouldAccept(payload.revision())) {
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

	/** Последний снимок или {@code null}. */
	public DarknessSnapshotPayload snapshot() {
		return this.latest;
	}

	/** Есть ли данные от сервера. */
	public boolean hasData() {
		return this.latest != null;
	}

	/** Последняя принятая ревизия (-1, если данных нет). */
	public long lastRevision() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? -1L : current.revision();
	}

	/** Уровень блока 0..15 из последнего снимка. */
	public int light() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? 0 : current.light();
	}

	/** Exposure 0..100 из последнего снимка. */
	public int lightExposure() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? 0 : current.exposure();
	}

	/** Укрытие из последнего снимка. */
	public boolean shelter() {
		DarknessSnapshotPayload current = this.latest;
		return current != null && current.shelter();
	}

	/** Id предмета-источника или {@code null}. */
	public String sourceItemId() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? null : current.sourceItemId();
	}

	/** Остаток топлива источника (-1, если источника нет). */
	public int sourceRemainingTicks() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? -1 : current.sourceRemainingTicks();
	}

	/** Полный id активной цели или {@code null}. */
	public String goalId() {
		DarknessSnapshotPayload current = this.latest;
		return current == null ? null : current.goalId();
	}

	/** Нужен ли сейчас штраф скорости (false, если данных нет). */
	public boolean speedRestricted() {
		return this.latest != null
				&& this.latest.exposure() >= DarknessConfig.SPEED_RESTRICT_EXPOSURE_THRESHOLD;
	}

	/** Очищает кэш и сбрасывает гейт ревизий (при выходе из мира/отключении). */
	public void clear() {
		this.latest = null;
		this.lastUpdateNanos = 0L;
		this.gate.reset();
	}
}
