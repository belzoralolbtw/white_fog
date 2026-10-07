package com.whitefog.server;

import com.whitefog.WhiteFogConfig;
import com.whitefog.network.PlayerStateSyncPayload;
import com.whitefog.state.PlayerSurvivalState;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Синхронизация серверного состояния с клиентом.
 *
 * <p>Клиент получает только снимок состояния (S2C); вся логика остаётся на сервере.
 * Отправка происходит:</p>
 * <ul>
 *     <li>по изменению ревизии состояния (сеттеры в {@link PlayerSurvivalState} её увеличивают);</li>
 *     <li>раз в {@link WhiteFogConfig#SYNC_INTERVAL_TICKS} тиков как страховочный ре-синк;</li>
 *     <li>немедленно при входе игрока (см. {@link WhiteFogServer#register()}).</li>
 * </ul>
 *
 * <p>Перед отправкой проверяем {@link ServerPlayNetworking#canSend}: если у клиента нет нашего канала
 * (ваниль/другой клиент), пакет не отправляется и сервер продолжает работать.</p>
 */
public final class PlayerStateSyncService {
	private PlayerStateSyncService() {
	}

	/** Решает, нужно ли отправить снимок, и отправляет его. Вызывается из серверного тика. */
	public static void tickPlayer(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state) {
		int now = server.getTickCount();
		boolean changed = state.hasUnsyncedChanges();
		boolean neverSent = state.lastSyncTick() == Long.MIN_VALUE;
		boolean periodic = !neverSent && now - state.lastSyncTick() >= WhiteFogConfig.SYNC_INTERVAL_TICKS;
		if (changed || neverSent || periodic) {
			send(server, player, state);
		}
	}

	/** Принудительная отправка полного снимка (вход игрока, respawn-подобные события). */
	public static void sendNow(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state) {
		send(server, player, state);
	}

	private static void send(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state) {
		if (!ServerPlayNetworking.canSend(player, PlayerStateSyncPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new PlayerStateSyncPayload(state.toNbt()));
		state.markSynced(server.getTickCount());
	}
}
