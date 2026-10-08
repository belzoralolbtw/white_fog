package com.whitefog.server;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogAttachments;
import com.whitefog.breaking.BreakTimerService;
import com.whitefog.darkness.LightExposureService;
import com.whitefog.server.command.WhiteFogDebugCommand;
import com.whitefog.state.PlayerSurvivalState;
import com.whitefog.station.RecoveryService;
import com.whitefog.station.SmallStonePickup;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.List;

/**
 * Серверная обвязка: ровно один тик-обработчик и первичная синхронизация при входе игрока.
 *
 * <p>Обработчик {@code END_SERVER_TICK} регистрируется один раз на весь мод (флаг {@link #registered}),
 * а не по одному на игрока. Внутри он обходит всех игроков и защищён try/catch как по игроку,
 * так и целиком, чтобы сбой одного состояния не ломал серверный тик.</p>
 *
 * <p>Отладочная команда регистрируется только в dev-среде ({@link FabricLoader#isDevelopmentEnvironment()}),
 * в продакшене её нет.</p>
 */
public final class WhiteFogServer {
	/** Защита от повторной регистрации обработчиков (не игровое состояние). */
	private static boolean registered = false;

	private WhiteFogServer() {
	}

	/** Регистрирует серверные обработчики. Повторный вызов безопасен (no-op). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		// ОДИН обработчик серверного тика (не по одному на игрока).
		ServerTickEvents.END_SERVER_TICK.register(WhiteFogServer::onEndServerTick);

		// Первичная синхронизация состояния сразу после входа игрока.
		ServerPlayConnectionEvents.JOIN.register(WhiteFogServer::onPlayerJoin);

		// Явный перенос/ресинк при смерти (состояние копируется через Attachment#copyOnDeath)
		// и при смене измерения (сущность та же, но клиенту нужен свежий снимок).
		ServerPlayerEvents.AFTER_RESPAWN.register(WhiteFogServer::onPlayerRespawn);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(WhiteFogServer::onPlayerChangeLevel);

		// Этап 1.3: очистка сессий разрушения при disconnect (tick-валидация сама ловит смерть/смену измерения).
		ServerPlayConnectionEvents.DISCONNECT.register(WhiteFogServer::onPlayerDisconnect);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			CommandRegistrationCallback.EVENT.register(WhiteFogDebugCommand::register);
			WhiteFog.LOGGER.info("White Fog: dev-only debug command registered (/whitefog debug [player])");
		}
	}

	/** Единый серверный тик: обновляет состояние каждого игрока и синхронизирует его с клиентом. */
	private static void onEndServerTick(MinecraftServer server) {
		try {
			List<ServerPlayer> players = server.getPlayerList().getPlayers();
			for (ServerPlayer player : players) {
				try {
					PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);
					SurvivalTicker.tick(server, player, state);
					PlayerStateSyncService.tickPlayer(server, player, state);
				} catch (RuntimeException e) {
					// Один сломанный игрок не должен ломать тик остальных.
					WhiteFog.LOGGER.error("White Fog: failed to tick survival state for player {}",
							player.getStringUUID(), e);
				}
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: server tick failed", e);
		}

		// Этап 1.3: серверный тик сессий разрушения (единый END_SERVER_TICK, без второго обработчика).
		BreakTimerService.tickAll(server);

		// Этап 1.4: серверный тик recovery-задач «2 cobblestone -> 1 flat_stone».
		RecoveryService.tickAll(server);

		// Этап 1.5: серверный тик воздействия тьмы (в единственном END_SERVER_TICK, без второго player tick).
		LightExposureService.tickAll(server);
	}

	/** Отключение игрока: сбрасываем сессию разрушения, подсказки и recovery (этапы 1.3/1.4). */
	private static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
		try {
			ServerPlayer player = handler.getPlayer();
			BreakTimerService.clearPlayer(player);
			RecoveryService.clear(player);
			SmallStonePickup.clear(player);
			LightExposureService.clear(player);
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to clear break session on disconnect", e);
		}
	}

	/** При входе игрока принудительно отправляем полный снимок состояния. */
	private static void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
		try {
			ServerPlayer player = handler.getPlayer();
			PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);
			state.invalidateSync();
			PlayerStateSyncService.sendNow(server, player, state);
			// Этап 1.5: немедленный снимок тьмы при входе.
			LightExposureService.onPlayerJoined(server, player);
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to send initial survival state on join", e);
		}
	}

	/**
	 * Явная обработка респавна: Fabric Data Attachment переносит состояние через
	 * {@code copyOnDeath()}; здесь лишь сбрасываем служебную синхронизацию и отдаём новый снимок клиенту.
	 */
	private static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		try {
			BreakTimerService.clearSession(newPlayer);
			RecoveryService.clear(newPlayer);
			SmallStonePickup.clear(newPlayer);
			PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(newPlayer);
			state.invalidateSync();
			MinecraftServer server = newPlayer.level().getServer();
			if (server != null) {
				PlayerStateSyncService.sendNow(server, newPlayer, state);
				// Этап 1.5: немедленный снимок тьмы после респавна (шкалы перенесены через copyOnDeath).
				LightExposureService.onPlayerRespawned(server, newPlayer);
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to resync survival state after respawn", e);
		}
	}

	/**
	 * Явная обработка смены измерения: сущность игрока сохраняется, состояние не пересоздаётся;
	 * отправляем свежий снимок в новое измерение.
	 */
	private static void onPlayerChangeLevel(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		try {
			BreakTimerService.clearSession(player);
			RecoveryService.clear(player);
			SmallStonePickup.clear(player);
			PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);
			state.invalidateSync();
			MinecraftServer server = destination.getServer();
			if (server != null) {
				PlayerStateSyncService.sendNow(server, player, state);
				// Этап 1.5: немедленный снимок тьмы после смены измерения.
				LightExposureService.onPlayerChangeLevel(server, player);
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to resync survival state after level change", e);
		}
	}
}
