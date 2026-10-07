package com.whitefog.client;

import com.whitefog.state.PlayerSurvivalState;

import java.util.Optional;

/**
 * Клиентский кэш последнего синхронизированного состояния игрока.
 *
 * <p>Это НЕ источник истины: серверная логика живёт на сервере, клиент лишь отображает
 * полученный снимок. Класс — обычный экземпляр (не static-поле), создаётся в
 * {@link WhiteFogClient} и передаётся получателю пакетов и HUD.</p>
 */
public final class ClientPlayerState {
	private volatile PlayerSurvivalState snapshot;

	/** Обновляет снимок (вызывается на клиентском потоке из сетевого получателя). */
	public void update(PlayerSurvivalState state) {
		this.snapshot = state;
	}

	/** Последний снимок, если он получен. */
	public Optional<PlayerSurvivalState> snapshot() {
		return Optional.ofNullable(this.snapshot);
	}

	/** Есть ли уже данные от сервера. */
	public boolean hasData() {
		return this.snapshot != null;
	}

	/** Очищает кэш (при выходе из мира/отключении). */
	public void clear() {
		this.snapshot = null;
	}
}
